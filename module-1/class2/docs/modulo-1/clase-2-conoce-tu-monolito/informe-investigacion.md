# Informe de investigación: TWA Flights Monolith

Paso 1 (investigar) de la clase "Conoce tu monolito". El análisis se hizo en modo lectura, sobre el código de
`src/main`, las migraciones de Flyway, `analisis-git-log.txt` y `analisis-jdeps.txt`. Lo que no se pudo confirmar en
el código está marcado como **(hipótesis)**.

> El esquema de la base se obtuvo de las migraciones `V1.0` a `V1.7`.

---

## 1. Dominios candidatos

| # | Dominio | Clases principales | Responsabilidad |
|---|---------|-------------------|-----------------|
| D1 | **Búsqueda / Disponibilidad** | `ClustersController`, `ClustersService`, `ItinerariesSearchService`, `FlightDuplicationCheckHelper`, `ItineraryKeyGeneratorHelper`, `ItineraryFilterHelper`, `ItinerarySortHelper`, `AvailabilityRequestValidator` | Orquesta la búsqueda, une y deduplica resultados, filtra, ordena y pagina |
| D2 | **Integración con proveedores** (capa anticorrupción) | `connector/*Connector`, `connector/configuration/*`, `facade/Provider*Facade`, `service/Provider*Service`, `mapper/provider/*`, `dto/provider/*` (records), `FlightHelper` | Llama por HTTP a Alpha, Beta y Gamma, traduce cada formato a `ItineraryDTO` y calcula la duración |
| D3 | **Pricing (cálculo de markup)** | `PricingService`, `CommercialPolicyCarrierSpecification` | Elige la política vigente y aplica el markup a cada itinerario |
| D4 | **Administración de políticas comerciales** | `CommercialPolicyController`, `CommercialPolicyService`, `CommercialPolicyValidator`, `CommercialPolicyMapper`, `PricingApiKeyInterceptor` | CRUD de políticas y carriers, protegido con `X-Api-Key` |
| D5 | **Catálogo geográfico** | `CatalogService`, `CityRepository`, `City`/`Country`/`Continent`, `CityMapper` | Ciudades con su zona horaria, país y continente (solo lectura, se carga por migraciones) |
| D6 | **Sesión de búsqueda** (caché de resultados) | `ClustersRepositoryImpl`, `ClusterSearch`, `ClusterSearchJpaRepository`, `ClusterSearchCleanupJob`, `JsonSerializer`, `FlightIdGeneratorHelper` | Guarda la búsqueda como JSON durante 5 minutos para paginar y para `getItinerary` |
| — | **Kernel compartido** | `dto/*` (sobre todo `ItineraryDTO`, `PriceInfoDTO`, `PaxPriceDTO`), `enums/*`, `exception/*` | Lo usan todos los dominios |

D3 y D4 comparten tablas y entidad, así que probablemente son **un solo contexto (Pricing)** con dos caras: cálculo y
administración **(hipótesis de diseño)**. D6 está muy pegado a D1; es más un detalle de infraestructura de la búsqueda
que un dominio aparte.

---

## 2. Dependencias

### Entre paquetes (jdeps + código)

- **Ciclo `service` ↔ `facade`:** `ItinerariesSearchService` depende de `ProviderFacade`, y `ProviderAlphaFacade`,
  `ProviderBetaFacade` y `ProviderGammaFacade` dependen de `Provider*Service`.
- **Ciclo `service` ↔ `validator`:** `CommercialPolicyService` usa el método estático
  `CommercialPolicyValidator.normalize`. A la vez, `AvailabilityRequestValidator` depende de `CatalogService`.
- **`helper` → `repository`/`model`:** `FlightHelper` consulta `CityRepository` directamente
  ([FlightHelper.java:65](../../../src/main/java/com/twa/flights/helper/FlightHelper.java)). La integración con
  proveedores lee las tablas del catálogo sin pasar por `CatalogService`. Es el acoplamiento oculto más importante para
  extraer el catálogo.
- **`validator` → `helper` (estáticos):** `AvailabilityRequestValidator` usa `ItineraryFilterHelper.splitCodes` e
  `ItinerarySortHelper.normalize`.
- **`connector` → `mapper.provider`:** el connector, además de hacer el HTTP, mapea la respuesta.
- **`repository` → `dto`:** la interfaz `ClustersRepository` recibe y devuelve DTOs de la API, no entidades.
  `repository.impl` → `helper`, porque usa `FlightIdGeneratorHelper`.

### Entre dominios

```
D1 Búsqueda ──► D2 Proveedores ──► D5 Catálogo (CityRepository directo, por zona horaria)
     │                                  ▲
     ├──► D5 Catálogo (CatalogService, validación de códigos)
     ├──► D3 Pricing ──(lee las tablas de)──► D4 Administración
     └──► D6 Sesión de búsqueda
```

- `PricingService` y `CommercialPolicyCarrierSpecification` reciben `ItineraryDTO`, el modelo de la búsqueda. Pricing no
  tiene contrato propio.
- `PricingService.priceItineraries` **modifica en el lugar** los `PaxPriceDTO` del itinerario.

### Archivos que cambian juntos (git log)

| Grupo | Commits | Lectura |
|-------|---------|---------|
| `ExceptionStatus.java` | 1e5ccc1, 8176f5d, 7245208, b0ee16b, 6eddbb8, a868561 | Es el punto más caliente: entra en casi todas las features, de todos los dominios |
| `ClustersResources` + `ClustersAvailabilityRequestDTO` + `AvailabilityRequestValidator` + `ClustersService` + `README.md` | 8176f5d, 7245208, b0ee16b | Cada feature de búsqueda toca la API, el DTO, el validador y el servicio |
| `{Alpha,Beta,Gamma}Connector` + `Provider*ConnectorConfiguration` | d0b786a | Cirugía de escopeta: un fix de timeouts se replica 6 veces |
| `{Alpha,Beta,Gamma}ItineraryMapper` + `ProviderMapperHelper` | c0f5987, 7245208 | Ídem con los mappers |
| Proveedor nuevo: 13 archivos + `V1.5` (ciudades) + `V1.6` (pricing) + `docker-compose` + `microcks` | f77c8a1 | Sumar un proveedor obliga a tocar el **catálogo** y el **pricing** |
| `application.yml` | 5 commits | Configuración compartida por todos los dominios |
| `WebConfiguration` + `SwaggerConfiguration` | 1e5ccc1, a868561 | Infraestructura web transversal |

---

## 3. Datos: tablas por módulo

Todo está en una sola base, `flights`.

| Tabla | Dueño propuesto | Escribe | Lee | Notas |
|-------|----------------|---------|-----|-------|
| `continent`, `country`, `city` | D5 Catálogo | Solo migraciones (`V1.0`, `V1.1`, `V1.5`) | `CatalogService`, **`FlightHelper`** (D2) | Las relaciones `@ManyToOne(cascade = ALL)` en `City`→`Country`→`Continent` son peligrosas si alguna vez se escribe |
| `commercial_policy`, `commercial_policy_carrier` | D3/D4 Pricing | `CommercialPolicyService` | `PricingService` vía la Specification | `V1.7` pasó los ids a AUTO_INCREMENT, agregó la auditoría y `UNIQUE(policy, carrier)` |
| `cluster_search` | D1/D6 Búsqueda | `ClustersRepositoryImpl` (async) | `ClustersRepositoryImpl` | `payload` es un JSON con el resultado completo; `ClusterSearchCleanupJob` borra cada 60 s |

**Referencias implícitas, sin clave foránea:**

- `commercial_policy_carrier.carrier` es un código de aerolínea de 2 caracteres que no referencia ninguna tabla. No hay
  maestro de aerolíneas.
- Los códigos de ciudad que mandan los proveedores se resuelven contra `city.code` en tiempo de ejecución.
- `cluster_search.payload` guarda códigos de ciudad, carriers y precios ya con markup, como una foto del momento.
- `V1.6` agrega carriers de Gamma (CM, AV, G3, JA) a la política "General". Esa data de pricing existe porque se sumó
  un proveedor.

No hay joins SQL entre dominios. El acoplamiento pasa por el código (`FlightHelper`) y por las referencias implícitas,
así que separar los datos parece viable **(hipótesis)**.

---

## 4. Reglas de negocio ocultas

Las reglas están verificadas en el código. Lo que se dice sobre la intención o el impacto en negocio está marcado.

**Pricing**

1. **El markup se divide por 10, no por 100.** En `PricingService.calculateMarkupByType` se calcula
   `subtotal × (percentage / 10)`. Con AR = 5, el markup es el 50 % del subtotal. Además `CommercialPolicyValidator`
   acepta porcentajes hasta 100, que equivalen a 10 veces el precio. Que sea un bug o una regla del negocio es
   **hipótesis**.
2. **Si no hay política aplicable, el itinerario desaparece** del resultado (`PricingService.priceItineraries`, filtro
   `markup != null`). Un carrier sin política nunca se vende.
3. **Gana la política con número de prioridad más alto** (`CommercialPolicyCarrierSpecification.toPredicate`,
   `orderBy desc` + `findFirst`). En los datos semilla, "General" (10) le gana a "Ciber Week" (1) cuando comparten
   carrier. Que la intención sea la contraria es **hipótesis**.
4. **Vigencia de la política:** se exige `start_date <= salida del primer tramo` y
   `expire_date >= llegada del primer tramo del último segmento`. Ojo: usa `convertArrival(lastSegment())` con
   `firstLeg()`, no el último tramo. Las horas son locales y no se tiene en cuenta la zona horaria.
5. El markup se aplica sobre `subtotal = (base + tax) × cantidad`, o sea **también sobre los impuestos**, y con el
   mismo porcentaje para adultos, niños e infantes.
6. La política semilla "Special Carrier" vence el 2027-05-01 (`V1.3`). Ese día cambian los precios sin que nadie
   despliegue nada **(hipótesis sobre el impacto)**.

**Búsqueda**

7. **La deduplicación incluye el proveedor en la clave** (`ItineraryKeyGeneratorHelper.generateKeyForItinerary`). Si
   el mismo vuelo llega de Alpha y de Beta, **no se deduplica**. Dentro de un mismo proveedor se queda el más barato
   (`FlightDuplicationCheckHelper`), y en caso de empate el primero. La clave **no incluye la cabina ni el equipaje**:
   dos ofertas del mismo vuelo con distinta cabina colapsan en una sola. Además se deduplica **antes** del markup.
8. **Si no se puede calcular la duración, el itinerario se descarta en silencio** (`Provider{Alpha,Beta,Gamma}Service`).
   Pasa cuando la ciudad no está en el catálogo o cuando la llegada es anterior a la salida
   (`FlightHelper.timeBetweenDateTime`). Si al catálogo le falta una ciudad, los resultados se filtran sin avisar.
9. **Si un proveedor falla, devuelve una lista vacía** sin error para el cliente
   (`ItinerariesSearchService.makeProviderCalls`). Beta con `status != "OK"` también da vacío
   (`BetaItineraryMapper`). Una oferta que no se puede mapear se descarta y el proveedor sigue (fix c0f5987).
10. **Solo se busca la primera ruta.** Los connectors mandan `origin[0]`, `destination[0]`, `departure[0]` y
    `departure[1]` como vuelta (`AlphaConnector.availability`, igual en Beta y Gamma). En cambio, el tipo ROUND_TRIP
    sale de **la cantidad de destinos** (`ProviderMapperHelper.getFlightType`). El validador acepta varias rutas, pero
    solo valida que no estén duplicadas.
11. **Pasajeros:** máximo 9 entre adultos y niños; los infantes no cuentan
    (`AvailabilityRequestValidator.validatePassengerCount`). No se valida que haya como máximo un infante por adulto.
    Si `children` o `infants` llegan null, el código hace unboxing a null y daría un 500, salvo que haya defaults en
    otro lado **(hipótesis)**.
12. **Ciudades:** el código puede tener hasta 3 caracteres, y la ciudad tiene que existir con país y continente.
    Cualquier excepción, incluso un error de base, se convierte en `INCOMPLETE_CATALOG_INFORMATION`
    (`AvailabilityRequestValidator.validateCodeWithCatalog`).
13. **Paginación:** `offset` es el **número de página**, no un desplazamiento (`skip = offset × pageSize` en
    `ClustersService.buildPage`). Sin `amount`, todo va en una sola página. Además, `amount` limita **cuántos
    resultados se toman de cada proveedor** (en el connector).
14. **Los filtros y el orden se aplican sobre la búsqueda guardada, en cada página** (`ItineraryFilterHelper`,
    `ItinerarySortHelper`). Las escalas cuentan las del segmento con más conexiones. La franja horaria mira solo la
    salida de la ida. "Con equipaje" significa `pieces > 0`. En cualquier orden, el desempate es por precio. El orden
    por defecto es PRICE ASC. Si no hay piezas, se ignora el peso (`ProviderMapperHelper.createAllowance`).
15. **TTL de 5 minutos fijo en el código** (`ClustersRepositoryImpl.TOKEN_TTL`). El guardado es asíncrono
    (`ClustersRepositoryImpl.insert`). Si se pide la página 2 enseguida, puede no estar guardada todavía y devuelve
    **HTTP 502 con código 400_15** (`ClustersService.availabilityFromDatabase`).
16. **El id de la búsqueda codifica el pedido** (`TWA_g=<now>_f=..._t=..._cb=...` en `FlightIdGeneratorHelper`). Dos
    búsquedas iguales en el mismo instante colisionan **(hipótesis, poco probable)**.
17. **Reglas de cada proveedor:** Alpha usa códigos de reserva (`Y`/`W`/`C`). Beta manda precios en centavos y la ida
    puede venir desordenada. Gamma manda el equipaje como `1PC23KG` y agrupa los vuelos por `segmentIndex`. En los tres
    casos, al número de vuelo se le quitan las letras (`ProviderMapperHelper.getFlightNumber`).

**Administración de pricing**

18. Una política siempre tiene al menos un carrier: no se puede borrar el último
    (`CommercialPolicyService.deleteCarrier`). Si no se indica, se crea activa. Los carriers se normalizan a
    mayúsculas. Cambiar un carrier actualiza el `updatedAt` de la política (método `touch`).

---

## 5. Antipatrones

| Antipatrón | Dónde | Efecto |
|-----------|-------|--------|
| Copy-paste por proveedor | `Provider{Alpha,Beta,Gamma}Service`, `*Facade`, `*Connector`, `*ConnectorConfiguration`: los diffs solo cambian nombres | Cirugía de escopeta (d0b786a, c0f5987); el log de error de Alpha no incluye la excepción y el de Beta sí |
| Paquetes por capa técnica con ciclos | `service`↔`facade`, `service`↔`validator` | Hay que cortar los ciclos antes de modularizar |
| Helper que accede a la base | `FlightHelper` → `CityRepository` | Saltea el catálogo; N+1: 2 consultas por tramo y 2 por segmento |
| N+1 en pricing | `PricingService.calculatePrices`: una consulta por itinerario | Escala mal con muchos resultados |
| Excepciones tragadas | `ItinerariesSearchService`, `Provider*Service`, `AvailabilityRequestValidator` | Errores invisibles para el cliente; se degrada sin métricas |
| Thread pools a mano | `Executors.newFixedThreadPool(10)` en `ItinerariesSearchService` y `ClustersRepositoryImpl` | No los gestiona Spring, nunca se cierran, el guardado es fire-and-forget |
| Modelo compartido y mutable | `ItineraryDTO` lo usan los 6 dominios; `PricingService` lo modifica | Kernel compartido que acopla todo |
| Enum de errores centralizado | `ExceptionStatus` (6 commits) | Conflictos de merge; el código y el status HTTP no coinciden (400_15 con 502) |
| Números mágicos | `/10`, `TOKEN_TTL = 5`, `> 9`, thread pools de 10 | Reglas de negocio escondidas en constantes |
| Dos APIs de fechas mezcladas | Joda-Time en `FlightHelper`, java.time en el resto | Riesgo con las zonas horarias |
| Lógica en métodos estáticos usados desde otras capas | `ItinerarySortHelper.normalize`, `CommercialPolicyValidator.normalize` | Acoplamiento poco visible |
| Seguridad mínima | `PricingApiKeyInterceptor`: una sola clave compartida, comparada con `equals` | Sin roles ni auditoría de quién cambió qué |

---

## 6. Preguntas abiertas para el negocio

1. ¿El markup es en porcentaje (`/100`) o en otra unidad? Hoy un 5 equivale a un 50 %. ¿Los precios publicados son los
   correctos?
2. En la prioridad de políticas, ¿gana el número más alto o el más bajo? ¿"Ciber Week" debería ganarle a "General"?
3. ¿Es intencional no vender carriers sin política? ¿O debería salir con markup 0?
4. ¿El markup tiene que aplicarse sobre los impuestos? ¿Niños e infantes deberían tener otro porcentaje?
5. ¿Cómo se define la vigencia de la política: por la fecha de salida, por la de llegada o por la fecha de compra?
6. Si el mismo vuelo llega de dos proveedores, ¿se muestran los dos o solo el más barato? ¿Y si son dos cabinas del
   mismo vuelo?
7. ¿Se soportan búsquedas multidestino o solo ida, e ida y vuelta?
8. ¿Hace falta avisarle al usuario cuando un proveedor no responde o devuelve resultados incompletos?
9. ¿Alcanza con 5 minutos de vida de la búsqueda? ¿Qué pasa con un itinerario elegido cuando la búsqueda ya venció?
10. ¿Puede haber más de un infante por adulto? ¿El tope de 9 pasajeros viene de las aerolíneas?
11. ¿Quién mantiene el catálogo de ciudades? Hoy solo se carga con migraciones, y una ciudad faltante oculta vuelos.
12. ¿Hace falta un maestro de aerolíneas? ¿Quién da de alta los carriers de un proveedor nuevo en pricing?
13. ¿Quién administra las políticas? ¿Hace falta auditar quién hizo cada cambio?

---

## Aclaraciones

- Muchas reglas (1, 3, 7, 10, 11, 15) parecen bugs. No se modificaron porque el objetivo es caracterizar el
  comportamiento actual, no arreglarlo.
- El status HTTP y el código de error de la regla 15 salen de leer el código; no se probaron contra el stack levantado.
