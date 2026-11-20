# Reglas de dependencias entre módulos

Paso 10 del [plan de refactor](plan-de-refactor.md). Después de los pasos 1 a 9, el monolito está organizado en
módulos por contexto. Este documento fija qué dependencias entre módulos están permitidas y cómo comprobarlo, para que
el monolito no vuelva a mezclarse antes de extraer los servicios.

> Convención: los paquetes se nombran sin el prefijo `com.twa.flights`.

## 1. Módulos e interfaz pública

| Módulo | Contexto | Lo que pueden usar los otros módulos | Lo interno (solo lo usa el módulo) |
|--------|----------|--------------------------------------|------------------------------------|
| `search` | BC1 Búsqueda | `search.dto` y `search.dto.request` (lenguaje publicado: `ItineraryDTO` y su árbol, `AvailabilityRequestDTO`) | `controller`, `service`, `helper`, `repository`, `model`, `job`, `serializer`, `validator`, `exception`, `dto.response` |
| `provider` | BC2 Proveedores | `provider.api` (`ProviderFacade`, `Provider`) | `connector`, `facade`, `mapper`, `service`, `helper`, `dto` |
| `pricing` | BC3 Pricing | `pricing.api` (`QuotingApi`) y `pricing.dto` (`QuoteRequest`, `QuoteResult`...) | `controller`, `service`, `validator`, `mapper`, `model`, `repository`, `configuration`, `exception` |
| `catalog` | BC4 Catálogo | `catalog.api` (`CatalogApi`, `CatalogError`) y `catalog.dto` (`CityDTO` y su árbol) | `service`, `repository`, `model`, `mapper` |
| `shared` | Kernel | Todo (`dto`, `exception`, `enums`, `model`, `configuration`, `service`) | — |

## 2. Dependencias permitidas

| Desde ↓ / Hacia → | `search` | `provider` | `pricing` | `catalog` | `shared` |
|-------------------|----------|------------|-----------|-----------|----------|
| **`search`** | — | `api` | `api`, `dto` | `api`, `dto` | sí |
| **`provider`** | `dto`, `dto.request` (ACL) | — | no | `api`, `dto` | sí |
| **`pricing`** | no | no | — | no | sí |
| **`catalog`** | no | no | no | — | sí |
| **`shared`** | no | no | no (ver excepción E1) | no | — |

Reglas:

1. **Ningún módulo usa lo interno de otro.** Solo `api` y `dto`, como dice la tabla.
2. **`pricing` y `catalog` no conocen a nadie** salvo el kernel. En particular, `pricing` no conoce el modelo de la
   búsqueda: cotiza con `QuoteRequest`/`QuoteResult`. `search.service.ItineraryPricingAdapter` traduce entre los dos
   modelos (paso 9).
3. **`provider → search.dto` es la capa anticorrupción:** los mappers traducen el formato de cada proveedor al modelo
   publicado de la búsqueda.
4. **`shared` no depende de ningún módulo.** Hoy tiene una excepción pendiente (E1).

## 3. Excepciones conocidas (pendientes)

Están permitidas en el chequeo para que pase hoy, pero no forman parte de la regla: cada una tiene que desaparecer. No
se resolvieron porque el plan no las incluye en ningún paso.

| # | Dependencia | Por qué existe | Cómo se resolvería |
|---|-------------|----------------|--------------------|
| E1 | `shared.configuration → pricing.configuration` | `WebConfiguration` registra el `PricingApiKeyInterceptor` y `SwaggerConfiguration` usa la constante del header `X-Api-Key` | Que `pricing` registre su propio interceptor (un `WebMvcConfigurer` en `pricing.configuration`) |
| E2 | `service.PricingService` y `specification.CommercialPolicyCarrierSpecification` fuera de `pricing.*` (dependen de `pricing.model`, `pricing.repository`, `pricing.api` y `pricing.dto`) | El plan no los mueve: la implementación de `QuotingApi` y la specification quedaron en los paquetes legacy | Moverlos a `pricing.service` y `pricing.specification` (solo cambio de paquete) |
| E3 | `search.helper` y `search.validator → enums` | `DepartureTimeRange`, `SortCriteria` y `SortOrder` solo los usa la búsqueda, pero el plan no los asigna | Moverlos a `search.*` |
| E4 | Ciclo `provider.api ↔ search.dto` | `ItineraryDTO` (lenguaje publicado de BC1) tiene un campo del enum `Provider` | Decidir de quién es `Provider`: o pasa a `search.dto` (el proveedor de un itinerario es parte del modelo publicado) o a `shared` |

Además quedan en paquetes legacy, sin dependencias problemáticas: `controller.DocumentationController` (redirect a
Swagger UI) y `enums.ExceptionStatus` (deprecado, lo usa `ClustersValidationTest` como referencia de códigos y
mensajes).

## 4. Cómo comprobarlo

**Chequeo completo (jdeps, falla con código 1 si hay una violación):**

```bash
mvn -q -B clean package -DskipTests
./scripts/check-module-dependencies.sh
```

[`scripts/check-module-dependencies.sh`](../../../scripts/check-module-dependencies.sh) corre `jdeps` sobre
`target/classes` y compara cada dependencia entre paquetes de módulos distintos con la lista `ALLOWED` del script, que
es la tabla de la sección 2 más las excepciones E1 a E3. Usa jdeps y no `grep` porque también detecta los usos con el
nombre completo de la clase, sin `import`. Hoy da `OK: 45 dependencies between modules checked`. Se comprobó que
detecta una violación agregando temporalmente un campo `ItineraryDTO` en `pricing.dto` y un campo `Provider` en
`catalog.dto`: informó `pricing.dto -> search.dto` y `catalog.dto -> provider.api` y salió con código 1.

**Chequeo rápido sin compilar (grep, aproximado):**

```bash
grep -rn "com\.twa\.flights\.\(search\|provider\|catalog\)\." src/main/java/com/twa/flights/pricing
grep -rn "com\.twa\.flights\.\(search\|provider\|pricing\)\." src/main/java/com/twa/flights/catalog
grep -rnE "com\.twa\.flights\.(search|provider|pricing|catalog)\." src/main/java/com/twa/flights/shared
grep -rnE "com\.twa\.flights\.(provider\.(connector|facade|mapper|service|helper|dto)|pricing\.(service|model|repository|controller|validator|mapper)|catalog\.(service|repository|model|mapper))\b" src/main/java/com/twa/flights/search
```

Los dos primeros tienen que dar vacío. El de `shared` muestra la excepción E1 y los `packagesToScan` de
`SwaggerConfiguration`, que nombran los paquetes de los controllers como texto (no es una dependencia de clases, pero
si se mueve un controller hay que actualizarlos). El último busca usos de lo interno de otros módulos desde `search` y
tiene que dar vacío.

**Cuándo correrlo:** antes de cada commit que mueva clases o agregue imports entre módulos, junto con la verificación
habitual (`mvn -q -B clean package`, `./scripts/smoke-test.sh`). Si se quiere que lo haga la CI o un test, la opción es
un test de arquitectura con ArchUnit; no se agregó porque no se pidió.

**Para agregar una dependencia nueva entre módulos:** primero se discute con el equipo, después se actualiza la tabla
de la sección 2 y la lista `ALLOWED` del script en el mismo commit.

## 5. Resultado: jdeps del paso 0 contra el final

`analisis-jdeps.txt` es el del paso 0 y `analisis-jdeps-final.txt` el de después del paso 9 (los dos con
`jdeps -verbose:package` sobre `target/classes`).

| | Paso 0 | Final |
|-|--------|-------|
| Paquetes de `com.twa.flights` | 24 | 48 |
| Dependencias entre paquetes | 74 | 113 |
| Ciclos directos entre paquetes | 2 (`facade ↔ service`, `service ↔ validator`) | 1 (`provider.api ↔ search.dto`, excepción E4) |
| Grupos de paquetes en ciclo | 1 grupo de 3 (`facade`, `service`, `validator`) | 1 grupo de 2 (`provider.api`, `search.dto`) |
| `pricing → search` | `service`/`specification` usaban `ItineraryDTO` y lo modificaban (X2) | 0 |
| Acceso a la tabla `city` desde proveedores | `FlightHelper → CityRepository` (X1) | 0 (`FlightHelper → CatalogApi`) |
| `ExceptionStatus` compartido por 3 contextos | Sí | No: `SearchError`, `PricingError`, `CatalogError` detrás de `ErrorCode` |

Hay más paquetes y más dependencias porque cada módulo tiene sus propios subpaquetes por capa. Lo que importa es la
dirección: las dependencias entre módulos van solo a `api`, `dto` y `shared`.

## 6. Pendiente para el equipo

- La regla de `CLAUDE.md` "paquetes por capa técnica (`controller`, `service`, `repository`, `helper`...)" ya no
  describe el código: ahora son paquetes por módulo con subpaquetes por capa. El plan propone actualizarla cuando el
  equipo lo apruebe.
- Resolver las excepciones E1 a E4 (cada una es un paso chico, del mismo estilo que los del plan).
