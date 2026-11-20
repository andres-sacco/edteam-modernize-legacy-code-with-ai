# M2 · C2 · Separa los datos: informe de investigación (paso 1)

Dueño de cada tabla de la base compartida `flights` y cruces entre límites (foreign keys, joins y accesos), cada uno
con su riesgo y su alternativa: API, evento o copia de lectura. El análisis es de solo lectura.

## Fuentes

- **Esquema:** migraciones de Flyway `V1.0` a `V1.7` y el dump `analisis-ddl.sql` (`mysqldump --no-data`, tomado
  después de que Flyway terminó).
- **Consultas reales:** `analisis-consultas.log`, el log del monolito con `SPRING_JPA_SHOW_SQL=true` durante una
  corrida de `./scripts/smoke-test.sh` (21 pruebas OK, 81 consultas). El catálogo estaba en modo
  `LOCAL (batch: false, cache: false)`.
- **Código:** repositorios, entidades, `CommercialPolicyCarrierSpecification`, configuración de los dos proyectos,
  `docker-compose.yml`, `docker/mysql/` y `microcks/generate-contracts.py`.
- **Decisiones previas:** `docs/modulo-1/clase-6-planifica-la-migracion/adr-002-extraccion-del-catalogo.md` (D2, D3 y D4).

No había logs de consultas lentas. Lo que no se pudo comprobar está marcado como **hipótesis**.

## Tabla de ownership

| Tabla | Dueño | Quién escribe | Quién lee | Fuente |
|---|---|---|---|---|
| `continent` | **Catálogo** | Solo Flyway del monolito (`V1.1`). No hay escrituras mientras la app corre | `CatalogService` del monolito (modo LOCAL) y servicio de catálogo (`catalog_reader`) | `V1.0`, `V1.1` |
| `country` | **Catálogo** | Ídem (`V1.1`) | Ídem, a través de `City.country` (`@ManyToOne`, EAGER) | `V1.0`, `V1.1` |
| `city` | **Catálogo** | Ídem (`V1.1`, `V1.5`) | `CityRepository.findByCode` / `findByCodeIn` en los dos proyectos | `V1.0`, `V1.1`, `V1.5` |
| `commercial_policy` | **Pricing** | `CommercialPolicyService` (ABM con `X-Api-Key`) | `PricingService` vía `CommercialPolicyCarrierSpecification` | `V1.2`, `V1.3`, `V1.7` |
| `commercial_policy_carrier` | **Pricing** | Ídem, en cascada (`@OneToMany cascade ALL, orphanRemoval`) | Ídem, con JOIN y carga diferida | `V1.2`, `V1.3`, `V1.6`, `V1.7` |
| `cluster_search` | **Búsqueda** | `ClustersRepositoryImpl.insert` (asíncrono, TTL de 5 min) y `ClusterSearchCleanupJob` (`deleteExpired`) | `ClustersRepositoryImpl.get` | `V1.4` |
| `flyway_schema_history` | Infraestructura del monolito. Hoy es **una sola historia para todos los módulos** | Flyway | Flyway | Dump |

### Foreign keys físicas

Son tres y ninguna cruza de un dueño a otro (comprobado en el dump):

| FK | Límite |
|---|---|
| `city.country_id → country.id` (`FKrpd7j1p7yxr784adkx4pyepba`) | Dentro del Catálogo |
| `country.continent_id → continent.id` (`FKpymfsgrl32dy3gtl9r7rykkjg`) | Dentro del Catálogo |
| `commercial_policy_carrier.commercial_policy_id → commercial_policy.id` (`FKntp5a7llnodxdwlb4lyq37ol2`) | Dentro de Pricing |

Todos los cruces son lógicos (por código) o de acceso.

## Consultas por búsqueda (log)

En el log quedaron dos búsquedas completas (líneas 49–76 y 77–116):

| Consulta | Búsqueda 1 | Búsqueda 2 | Origen |
|---|---|---|---|
| `select ... from city where code=?` | 8 | 11 | `CatalogService.getCity` → `findByCode` |
| `select ... from country left join continent ... where id=?` | 8 | 11 | `City.country` EAGER: Hibernate lo trae en una consulta aparte, no con JOIN |
| `commercial_policy join commercial_policy_carrier where active and start_date<=? and expire_date>=? and carrier=? order by priority desc` | 9 | 15 | `PricingService.quote` → una `findAll(spec)` por `QuoteRequest` |
| `select ... from commercial_policy_carrier where commercial_policy_id=?` | 1 | 1 | Carga diferida de `validatingCarriers`. Las demás salen de la caché de la sesión |
| `cluster_search`: `select` por id + `insert` | 1 + 1 | 1 + 1 | `save()` con id asignado: Hibernate hace un `merge` y por eso consulta antes de insertar |

No aparece ningún `findByCodeIn`: con `batch: false` no se usa el camino en lote.

## Cruces entre límites

| # | Cruce | Dónde (consulta o clase) | Riesgo | Alternativa |
|---|---|---|---|---|
| 1 | El monolito lee las tablas del Catálogo directamente | `catalog/service/CatalogService` → `CityRepository.findByCode` / `findByCodeIn`. Se usa con `catalog.mode=LOCAL`, el valor por defecto en `application.yml` y en `docker-compose.yml` | **Alto.** Hay dos lectores de las mismas tablas. Cuando el Catálogo tenga su propia base, el modo LOCAL deja de funcionar | **API:** `CatalogClient` (`GET /cities?codes=`) con `catalog.mode=REMOTE` y la caché `CachedCatalogApi`. Después se borran `CatalogService`, `CityRepository` y las entidades del monolito |
| 2 | El servicio de catálogo lee `flights.city/country/continent`, pero el esquema lo maneja el monolito | `twa-flights-catalog/.../CityRepository` + `application.yml` (`ddl-auto: validate`, usuario `catalog_reader`), `docker/mysql/grants/grant-catalog-reader.sh` | **Alto.** El dueño del esquema (Flyway del monolito) no es el dueño de los datos. Si una migración del monolito cambia `city`, el servicio falla al arrancar por el `validate` | **Copia de lectura y después base propia** (ADR-002 D2): base `catalog` con su propio Flyway como primaria. `flights.city` se mantiene con **outbox + relay** mientras exista el camino de vuelta |
| 3 | Búsqueda y Proveedores usan datos del Catálogo (zona horaria y validación) | `provider/helper/FlightHelper` (`getCities` en `Provider*Service:44`, `catalogApi.getCity` en las líneas 72-73) y `search/validator/AvailabilityRequestValidator` | **Alto.** Comprobado en el log: 2 consultas por ciudad y entre 8 y 11 búsquedas de ciudad por búsqueda, sin lote ni caché. En modo REMOTE serían entre 8 y 11 llamadas HTTP por búsqueda. Con D4, si cualquiera falla, la búsqueda entera responde 503 | **API en lote** (`getCities`, ADR D3) + caché, como **condición para activar REMOTE**. Las zonas horarias casi no cambian, así que una caché con TTL alcanza |
| 4 | Referencia lógica por código IATA de ciudad | `LegDTO.origin` / `destination` (strings) serializados en `cluster_search.payload` (`ClustersRepositoryImpl.insert`) | **Bajo.** Solo guarda códigos, sin nombres ni ids del catálogo | No cambia nada: el **código IATA** es la clave de integración. Ver la nota 1 sobre la unicidad |
| 5 | Búsqueda llama a Pricing para cotizar | `search/service/ItineraryPricingAdapter` → `QuotingApi` → `service/PricingService.quote` → `CommercialPolicyCarrierSpecification` | **Medio.** Comprobado en el log: una consulta por itinerario (entre 9 y 15 por búsqueda, un N+1). `quote(List)` ya recibe el lote entero, así que como API remota sería una sola llamada si el lote se mantiene | **API** (`QuotingApi` como REST en lote). Otra opción es una **copia de lectura** de las políticas activas en Búsqueda (son pocas filas y se leen mucho), invalidada con un **evento** `CommercialPolicyChanged` del ABM |
| 6 | Código de Pricing fuera de su paquete | `com.twa.flights.service.PricingService` y `com.twa.flights.specification.CommercialPolicyCarrierSpecification` usan `pricing.model` / `pricing.repository` | **Medio.** No es un cruce de datos, pero si se extrae `pricing/`, la consulta de cotización queda afuera | Mover las dos clases con Pricing al extraerlo |
| 7 | Snapshot de la salida de Pricing en Búsqueda | `cluster_search.payload` guarda `PriceInfoDTO` / `MarkupDTO` ya calculados | **Bajo.** Es intencional: si cambia una política, las búsquedas ya guardadas no se recalculan. Ese es el comportamiento actual por hasta 5 min | **Copia** (es dato propio de Búsqueda). Se mantiene |
| 8 | El monolito se conecta con un solo usuario `root` | `twa-flights-monolith/src/main/resources/application.yml` (`DB_USER:root`) | **Medio.** Cualquier módulo puede leer y escribir cualquier tabla y nada hace cumplir los límites | Un usuario o esquema por módulo antes de partir la base (como ya se hizo con `catalog_reader`) |
| 9 | El contrato, la historia de Flyway y la infraestructura del Catálogo dependen del monolito | `microcks/generate-contracts.py` (`CATALOG_MIGRATIONS = V1.1, V1.5`), `flyway_schema_history` única, `catalog-db-grants` espera a Flyway del monolito | **Medio.** Cuando el Catálogo tenga su propio Flyway, el generador y los grants apuntan a una fuente que deja de ser la verdad, y sus migraciones quedan registradas en la historia del monolito | Generar el contrato desde las migraciones del servicio de catálogo, hacer un baseline en la base nueva y eliminar los grants cuando haya base propia |

No cruzan límites:
- el borrado de `ClusterSearchCleanupJob` (JPQL de Búsqueda sobre su propia tabla);
- el JOIN de Pricing entre sus dos tablas y su N+1 al listar políticas en la administración;
- el select antes del insert en `cluster_search`;
- las FKs internas del Catálogo.

## Notas para el paso 2

1. **`city.code` no tiene `UNIQUE` ni índice.** Pasa lo mismo con `country.code` y `continent.code` (comprobado en el dump). Hay dos consecuencias:
   - `findByCode` devuelve un solo `City`, así que un código duplicado haría fallar la consulta. Además hoy hace un full scan, aunque con ~86 ciudades no importa.
   - Antes de cambiar la FK de id a código IATA hay que agregar la restricción única, y antes de eso revisar que no haya duplicados.
2. **La collation es `utf8mb4_0900_ai_ci`**, que ignora mayúsculas y acentos. Por eso `eze` encuentra `EZE`, y eso es lo que maneja `CityCodes.matches`. La base `catalog` y cualquier copia de lectura tienen que usar la misma collation. Si no, cambia qué códigos se encuentran y qué valores choca una restricción `UNIQUE` sobre `code`.
3. **`city.country_id` y `commercial_policy_carrier.commercial_policy_id` aceptan `NULL`.** Al copiar datos conviene contar huérfanos antes y después.
4. **`V1.5` busca `country_id` con `SELECT id FROM country WHERE code = ...`.** Es un join dentro del Catálogo, pero muestra que las semillas ya dependen del código y no del id.
5. **`commercial_policy_carrier.carrier` es un código de aerolínea sin tabla maestra.** Las aerolíneas llegan de los proveedores, así que no hay un dueño de "carriers" ni nada que cruzar.

## Hipótesis abiertas

- **Las búsquedas de ciudad se repiten por itinerario.** Una búsqueda de ida y vuelta toca pocas ciudades distintas, pero el log muestra entre 8 y 11 búsquedas. El log no muestra los parámetros, así que no se pudo comprobar.
