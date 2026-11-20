# Plan de refactor: monolito modular

Paso 1 de la clase "Prepara el monolito" (solo lectura). Mide los cruces entre los contextos de la
[clase 3](../clase-3-que-partes-podemos-separar/context-map.md) y propone pasos chicos y reversibles, de menor a mayor
riesgo. Cada paso se ejecuta aislado con el prompt 2, en un commit propio.

> Convención de rutas: las clases están en `src/main/java/com/twa/flights/`. Se nombran por paquete y clase.

**Fuentes:** imports de `src/main`, `jdeps -verbose:package` sobre `target/classes`, migraciones de Flyway y los
entregables de la clase 3 ([context map](../clase-3-que-partes-podemos-separar/context-map.md),
[entidades compartidas](../clase-3-que-partes-podemos-separar/entidades-compartidas.md),
[ADR-001](../clase-3-que-partes-podemos-separar/adr-001-division-en-bounded-contexts.md)).

**Avisos:**

- El [`analisis-jdeps.txt`](analisis-jdeps.txt) de esta carpeta se regeneró después de compilar
  (`mvn -q -B clean package -DskipTests`). La primera versión tenía solo `Warning: Path does not exist: target/classes`,
  porque jdeps había corrido sin compilar antes.
- **"Aerolíneas" no es un contexto.** El ADR-001 lo descartó porque el carrier es solo un código. En este plan,
  "Aerolíneas" es **BC2 Proveedores** (Alpha, Beta y Gamma).

---

## 1. Imports cruzados entre contextos

| # | Desde → Hacia | Evidencia | Problema |
|---|---------------|-----------|----------|
| X1 | BC2 → BC4 (tabla) | `helper/FlightHelper` inyecta `CityRepository` y llama a `findByCode` (2 veces por tramo y 2 por segmento) | Se salta `CatalogService` y lee la tabla `city` de otro contexto |
| X2 | BC3 → BC1 | `service/PricingService` y `specification/CommercialPolicyCarrierSpecification` importan `ItineraryDTO`, `SegmentDTO`, `PaxPriceDTO`, `PriceInfoDTO` y `MarkupDTO` | Pricing conoce y **modifica** el modelo de la búsqueda, y además descarta itinerarios (R02) |
| X3 | BC1 → BC3 | `service/ClustersService` llama a `PricingService.priceItineraries` | Llama a la clase concreta, no a un contrato |
| X4 | BC1 → BC4 | `validator/AvailabilityRequestValidator` llama a `catalogService.getCity` | Está bien hecho, pero `CatalogService` es concreto y devuelve `CityDTO` del kernel |
| X5 | Todos → kernel | `dto/*` (`ItineraryDTO` está en 27 archivos), `enums/*` y `ExceptionStatus` (lo usan 5 clases de 3 contextos) | Es el acoplamiento principal. `BadRequestException` recibe directamente el enum `ExceptionStatus` |
| X6 | Ciclos entre paquetes | `service ↔ facade` (`ProviderXFacade` → `ProviderXService`), `service ↔ validator` (`CommercialPolicyService` → validator, `AvailabilityRequestValidator` → `CatalogService`), `helper ↔ repository` (`FlightHelper` → `CityRepository`, `ClustersRepositoryImpl` → `FlightIdGeneratorHelper`), `validator → helper` (`ItineraryFilterHelper.splitCodes`, `ItinerarySortHelper.normalize`) | Los paquetes por capa mezclan los cuatro contextos |

## 2. Clases con varias responsabilidades

| Clase | Responsabilidades mezcladas |
|-------|-----------------------------|
| `service/PricingService` | (a) elige la política, (b) calcula el markup, (c) escribe en el `PriceInfoDTO` de BC1, (d) filtra los itinerarios sin markup, que es una decisión de BC1 |
| `helper/FlightHelper` | Calcula duraciones (lógica pura) y además accede a datos del catálogo para obtener la zona horaria |
| `service/ProviderAlpha/Beta/GammaService` | Llaman al connector, calculan duraciones y descartan los itinerarios incompletos. Los tres archivos son **casi idénticos** (71 líneas cada uno) |
| `service/ClustersService` | Orquesta la búsqueda, la cotización, la persistencia, el filtro, el orden y la paginación |
| `repository/impl/ClustersRepositoryImpl` | Persistencia, generación del id (`FlightIdGeneratorHelper`), serialización JSON y TTL |
| `validator/AvailabilityRequestValidator` (231 líneas) | Valida el pedido, consulta el catálogo y reutiliza utilidades estáticas de filtro y orden |
| `enums/ExceptionStatus` | Tiene los códigos de error de BC1, BC3 y BC4 en un solo enum |

## 3. Tablas compartidas

- **No hay ninguna tabla con dos dueños.** `cluster_search` es de BC1, `commercial_policy*` es de BC3 y
  `continent`/`country`/`city` son de BC4. Las únicas FK son internas de cada contexto: `city → country → continent` y
  `commercial_policy_carrier → commercial_policy`.
- **Hay un acceso cruzado:** BC2 lee `city` por JPA (X1).
- **Acoplamientos por la forma de los datos:**
  - `cluster_search.payload` guarda `ItineraryDTO` como JSON. El JSON no guarda nombres de clase, así que mover el
    paquete no lo rompe. Lo que sí lo rompe es cambiar los campos, y el TTL de 5 minutos limita ese riesgo.
  - El carrier es `varchar(3)` en la tabla, pero `CommercialPolicyValidator` solo acepta 2 caracteres.
- **Una sola base y un solo `flyway_schema_history`.** Dar de alta un proveedor toca BC2, BC3 y BC4 (`V1.5` y `V1.6`).
  Este refactor no lo resuelve.

## 4. Estructura destino

Paquetes por contexto con subpaquetes por capa, para mantener el estilo de `CLAUDE.md`:

```
com.twa.flights.search.*    BC1 Búsqueda
com.twa.flights.provider.*  BC2 Proveedores
com.twa.flights.pricing.*   BC3 Pricing
com.twa.flights.catalog.*   BC4 Catálogo
com.twa.flights.shared.*    lenguaje publicado y transversales
```

Cada uno con sus subpaquetes `controller`, `service`, `repository`, `model`, `dto`, `helper`... y un subpaquete `api`
con la interfaz pública del módulo. Esto cambia la regla "paquetes por capa técnica" de `CLAUDE.md`, que es
justamente el objetivo de esta clase: conviene actualizar esa regla cuando el equipo lo apruebe.

## 5. Pasos

En todos los pasos, la verificación base es: `mvn -q -B clean package` **con tests** (24 clases de caracterización de
la clase 4), después `./scripts/smoke-test.sh` y por último `docker logs twa-flights-monolith | grep ERROR`. Cada paso
va en su propio commit y se revierte con `git revert`.

| # | Qué mover / cambiar | Interfaz a crear | Archivos afectados | Riesgo | Cómo verificarlo (además de la base) |
|---|---------------------|------------------|--------------------|--------|--------------------------------------|
| 0 | Nada. Fijar la línea base y regenerar jdeps con `target/classes` | — | `analisis-jdeps.txt` | Nulo | Todo en verde. Guardar el jdeps válido para comparar después |
| 1 | Mover **BC4 Catálogo** a `catalog.*`: `City`, `Country`, `Continent`, `CityRepository`, `CatalogService`, `CityMapper`, `CityDTO`, `CountryDTO`, `ContinentDTO` | `catalog.api.CatalogApi` (`getCity(code)`, que implementa `CatalogService`) | Esas 9 clases, `AvailabilityRequestValidator`, `FlightHelper` (solo el import) y `CatalogServiceTest` | Bajo | `CatalogServiceTest`, `ClustersValidationTest` (ciudad inválida: `400_11`/`400_14`) |
| 2 | **Cortar X1:** `FlightHelper` deja de usar `CityRepository` y pasa a pedir la zona horaria a `CatalogApi` | Agregar `CatalogApi.getTimeZone(code)`, o usar `getCity(code).getTimeZone()` | `FlightHelper`, `CatalogApi` y su implementación, `FlightHelperTest` (hoy hace `new FlightHelper(null)`) | Bajo-medio: una ciudad inexistente tiene que seguir descartando el itinerario (R08) y hay que confirmar si `getCity` lanza excepción o devuelve null | `FlightHelperTest`, `ProvidersSearchTest`. `grep CityRepository` fuera de `catalog` tiene que dar 0 resultados |
| 3 | Mover **BC2 Proveedores** a `provider.*`: `connector/**`, `facade/**`, `mapper/provider/**`, `dto/provider/**`, `ProviderXService`, `FlightHelper`, `enums/Provider` | `provider.api.ProviderFacade` (ya existe, se mueve sin cambios) | Unos 22 archivos más los tests de `connector`, `mapper/provider`, `helper/FlightHelperTest` y `ProvidersSearchTest` | Bajo-medio: solo cambia el paquete, pero rompe el ciclo `service ↔ facade` | Tests de mappers y connectors, y smoke de búsqueda con Alpha, Beta y Gamma. En jdeps tiene que desaparecer el ciclo `service ↔ facade` |
| 4 | Mover **BC3 Pricing (administración)** a `pricing.*`: `CommercialPolicyController`, `CommercialPolicyResources`, `CommercialPolicyService`, `CommercialPolicyValidator`, `CommercialPolicyMapper`, `model/CommercialPolicy*`, `CommercialPolicyRepository`, `dto/pricing/**`, `PricingApiKeyInterceptor` | — (solo se mueve) | Unos 16 archivos, `WebConfiguration` y **`SwaggerConfiguration`** (`packagesToScan("com.twa.flights.controller")` en las líneas 29 y 35) | Medio: si no se ajusta `packagesToScan`, el grupo `pricing` de Swagger queda vacío sin dar error | `CommercialPolicyControllerTest`, `X-Api-Key` (401) y `curl /api/flights/documentation` con los grupos `default` y `pricing` |
| 5 | Mover **BC1 Búsqueda** a `search.*`: `ClustersController`, `ClustersResources`, `ClustersService`, `ItinerariesSearchService`, los helpers de filtro, orden, id, clave y duplicados, `ClustersRepository*`, `ClusterSearch*`, `ClusterSearchCleanupJob`, `JsonSerializer`, los validators y `dto/request`/`dto/response` | — | Unos 20 archivos, `SwaggerConfiguration` y los tests de `controller`, `helper` y `serializer` | Medio: es el paquete con más clases | `ClustersSearchTest`, la paginación con un `id` de búsqueda existente (JSON viejo de `cluster_search`) y que el job de limpieza siga programado |
| 6 | Dejar el **kernel mínimo** en `shared.*`: `ErrorDTO`, `APIException`, `BadRequestException`, `TWAException`, `ResourceExceptionHandler`, `PassengerType`, `CabinClass`, `BaseDTO`, `Base`, `SwaggerConfiguration`, `WebConfiguration`, `AppInfoService`. El árbol `ItineraryDTO` y `FlightType` pasan a `search.dto` como contrato publicado de BC1 | — | Imports en unos 40 archivos (cambio mecánico) | Medio-bajo | Que compile, y en jdeps que ningún paquete `pricing`, `catalog` o `provider` dependa de `search.*`, salvo `provider → search.dto` (ACL) y `pricing → search.dto` (X2, se resuelve en el paso 9) |
| 7 | **Repartir `ExceptionStatus`** sin cambiar códigos ni mensajes: `SearchError` (400_01–22, 404_01–02), `PricingError` (400_23–29, 401_01, 404_03–04) y `CatalogError` (400_14) | `shared.exception.ErrorCode` (`getCode()`, `getMessage()`). `BadRequestException` y `APIException` pasan a recibir `ErrorCode` | `ExceptionStatus` (se borra o queda deprecado), `BadRequestException`, los 2 validators, `ClustersService`, `CommercialPolicyService` y `PricingApiKeyInterceptor` | Medio: es el archivo que más cambia y define lo que ve el cliente | `ClustersValidationTest` y `CommercialPolicyControllerTest` comparan código y mensaje. Comparar los JSON de error con [los ejemplos de la clase 4](../clase-4-crea-una-red-de-seguridad/ejemplos/) |
| 8 | **Contrato de cotización (BC3):** crear el modelo propio de pricing a partir de `UpdatedPriceInfoDTO`/`UpdatedPaxPriceDTO`. `CommercialPolicyCarrierSpecification` deja de navegar `ItineraryDTO` | `pricing.api.QuotingApi` → `List<QuoteResult> quote(List<QuoteRequest>)`. `QuoteRequest` = clave, carrier, salida del primer segmento, llegada del último y subtotal/total por pasajero. `QuoteResult` = markup por pasajero, o "sin política" | `PricingService`, `CommercialPolicyCarrierSpecification`, `UpdatedPriceInfoDTO`/`UpdatedPaxPriceDTO` (se mueven a `pricing.dto`) y DTOs nuevos en `pricing.dto` | **Alto:** hay que conservar R01 (`/10`), R03 (gana la prioridad más alta) y R04 (vigencia por fecha del vuelo) | `PricingServiceTest` y `ClustersPricingTest` sin cambios en lo que esperan. En este paso, `priceItineraries` sigue existiendo como adaptador |
| 9 | **Cortar X2 y X3:** el mapeo itinerario ↔ `QuoteRequest`, la aplicación del markup y el filtro R02 pasan a BC1 (`search.service.ItineraryPricingAdapter`). `ClustersService` usa `QuotingApi` | Usa la interfaz del paso 8 | `ClustersService`, el adaptador nuevo, `PricingService` (deja de recibir `ItineraryDTO`) y `PricingServiceTest` (cambia la entrada) | **Alto:** R02 se ejecuta en otro lugar. Además, el cruce por `id` con `findFirst` tiene la hipótesis de colisión entre proveedores ([entidades compartidas §2.7](../clase-3-que-partes-podemos-separar/entidades-compartidas.md#27-id-del-itinerario--dueño-bc1)): hay que mantener el mismo comportamiento | `ClustersPricingTest`, smoke, y comparar una búsqueda real antes y después (cantidad de itinerarios y totales). En jdeps, `pricing → search` tiene que llegar a 0 |
| 10 | Evitar regresiones: dejar en `docs/` la regla de dependencias permitidas y un chequeo con `jdeps` o `grep` (por ejemplo, ningún import de `search.` dentro de `pricing/`) | — | Documentación y, opcionalmente, un test de arquitectura (ArchUnit) **si se pide** | Bajo | jdeps final contra el del paso 0 |

## 6. Fuera de alcance (detectado, no se cambia)

- `ProviderAlpha/Beta/GammaService` tienen el código triplicado. Unificarlo es un refactor fuera de la
  modularización; se puede proponer como paso aparte después del 3.
- `AvailabilityRequestValidator` usa métodos estáticos de los helpers de filtro y orden. Queda resuelto porque todo
  pasa a `search.*`.
- La tabla de `docs/README.md` tiene invertidas C4 y C5 (las carpetas dicen "clase-4-prepara", pero los prompts de esta
  clase están en `clase-5-prepara-el-monolito`).
