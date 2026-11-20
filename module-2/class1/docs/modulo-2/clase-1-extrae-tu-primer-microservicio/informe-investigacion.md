# Informe de investigación: qué se mueve, qué se duplica y qué se reemplaza (catálogo, BC4)

Paso 1 de M2 · C1. **Solo lectura**: no se cambió código. Base: código de `src/main` y `src/test`,
`analisis-jdeps.txt`, `analisis-tablas.sql`, `analisis-tests.txt`, el
[plan de extracción](../../modulo-1/clase-6-planifica-la-migracion/plan-de-extraccion.md) y el
[ADR-002](../../modulo-1/clase-6-planifica-la-migracion/adr-002-extraccion-del-catalogo.md).

> Convención: las rutas son relativas a `src/main/java/com/twa/flights/` salvo que se indique otra cosa.
> "Se mueve" = se copia al servicio ahora y se borra del monolito en la etapa 8 del plan (Strangler: mientras tanto
> conviven). "Se duplica" = queda en los dos lados para siempre. "Se reemplaza" = en el destino se escribe algo propio
> en lugar de depender de la clase original.

---

## 1. Hallazgo previo: Precios no consume el catálogo

El pedido nombra a Búsqueda y Precios como consumidores. **El código dice otra cosa:**

| Consumidor | Clase | Uso | Evidencia |
|------------|-------|-----|-----------|
| **C1 · Búsqueda (BC1)** | `search/validator/AvailabilityRequestValidator` | `validateCodeWithCatalog` (l. 219-231): `getCity(code)` por cada origen y destino; cualquier excepción → `BadRequestException(CatalogError.INCOMPLETE_CATALOG_INFORMATION)` (40014) | jdeps: `search.validator -> catalog.api, catalog.dto` |
| **C2 · Proveedores (BC2)** | `provider/helper/FlightHelper` | `calculateFlightDuration(SegmentDTO)` (l. 65-66) y `calculateFlightDuration(LegDTO)` (l. 73-74): 2 `getCity` por segmento y 2 por tramo; solo lee `timeZone` | jdeps: `provider.helper -> catalog.api, catalog.dto` |
| C2 (indirecto) | `provider/service/Provider{Alpha,Beta,Gamma}Service` · `calculateFlightDuration(ItineraryDTO)` | `catch (Exception)` (l. 57 en Alpha): descarta el itinerario y deja un `LOGGER.error` | — |
| ~~Precios (BC3)~~ | `pricing/**`, `service/PricingService`, `specification/**` | **Ninguno.** No importan nada de `catalog` | jdeps sin aristas `pricing\|service\|specification -> catalog`; `grep` sin resultados |

El catálogo tiene **dos** consumidores, y el segundo es Proveedores, no Precios. El context map de la clase 3 ya lo
decía (`BC4 → BC1` y `BC4 → BC2`). El resto del informe usa C1 y C2.

---

## 2. Inventario del módulo y clasificación

### 2.1 Clases del paquete `catalog` (11 clases)

| Clase | Depende de | Qué pasa | Detalle |
|-------|-----------|----------|---------|
| `catalog/api/CatalogApi` | `catalog.dto.CityDTO` | **Se queda en el monolito** (es la costura) | Gana una 2.ª implementación (`CatalogClient`) y, en la etapa 1, `getCities(Collection<String>)`. El servicio publica su equivalente HTTP, no esta interfaz |
| `catalog/api/CatalogError` | `shared.exception.ErrorCode` | **Se queda en el monolito**; se muda a BC1 en la etapa 8 (ADR-002, D7) | El 40014 lo decide la búsqueda. El servicio **no** lo conoce: responde 404 o "no está en la lista" |
| `catalog/dto/CityDTO`, `CountryDTO`, `ContinentDTO` | `shared.dto.BaseDTO` | **Se duplican** | El monolito los conserva (son el lenguaje publicado que usan C1 y C2 y lo que devuelve `CatalogClient`). El servicio tiene los suyos con la misma forma JSON y sin `BaseDTO` |
| `catalog/mapper/CityMapper` (+ `CityMapperImpl` generado por MapStruct) | `catalog.model`, `catalog.dto` | **Se mueve** | MapStruct genera los métodos anidados `Country→CountryDTO` y `Continent→ContinentDTO`. Convierte `null` en `null` (eso sostiene el contrato "null si no existe") |
| `catalog/model/City`, `Country`, `Continent` | `shared.model.Base`, JPA | **Se mueven** con cambios | Sin `cascade = CascadeType.ALL` en `City.country` y `Country.continent` (riesgo 9 del plan). Sin `Base` (ver 3.1) |
| `catalog/repository/CityRepository` | `catalog.model.City` | **Se mueve** con cambios | Agrega `findByCodeIn(Collection<String>)` para el lote. `findByCode` devuelve una sola entidad: con un código duplicado lanzaría `IncorrectResultSizeDataAccessException` (no hay `UNIQUE`, etapa 1c) |
| `catalog/service/CatalogService` | repository, mapper, `CatalogApi` | **Se mueve** la lógica; la clase **se queda** como camino local hasta la etapa 8 | En el servicio deja de implementar `CatalogApi` (que no viaja). En el monolito sigue siendo la implementación `local` |

### 2.2 Clases nuevas

| Dónde | Clase | Para qué |
|-------|-------|----------|
| Monolito | `catalog/client/CatalogClient implements CatalogApi` | **Reemplaza** a `CatalogService` en el camino remoto. HTTP con timeout corto; 404 → `null` (mismo contrato que hoy); 5xx o timeout → `CatalogUnavailableException` (etapa 1, no "null") |
| Monolito | `catalog/client/configuration/CatalogClientConfiguration` | Host, timeouts y `RestClient`, con el estilo de `provider/connector/configuration/Provider*ConnectorConfiguration` |
| Monolito | Selector de implementación (`@Primary` por `catalog.mode`) | Ver dependencia **R7** |
| Servicio | `controller/CityController` + `controller/documentation/CityResources` | Endpoints de la sección 5, con Swagger en la interfaz como en el monolito |
| Servicio | `exception/CatalogServiceError`, `ResourceExceptionHandler`, `ErrorDTO` | Errores propios (ver **R4**) |

---

## 3. Lo que el catálogo comparte con el monolito

### 3.1 Kernel `shared`

| Clase | La usa el catálogo en | ¿La usa alguien más? | Qué pasa en el servicio |
|-------|----------------------|----------------------|------------------------|
| `shared/model/Base` (`@MappedSuperclass`: `id` sin `@GeneratedValue`, `code` length 4, `name` length 50) | `City`, `Country`, `Continent` | **No.** Solo el catálogo | **Se reemplaza** por campos propios en cada entidad (o un `Base` propio). Es en realidad una clase del catálogo que vive en `shared` |
| `shared/dto/BaseDTO` (`name`, `code`) | `CityDTO`, `CountryDTO`, `ContinentDTO` | **No.** Solo el catálogo | **Se reemplaza** igual que `Base`. En el monolito se queda mientras existan los DTOs |
| `shared/exception/ErrorCode` | `CatalogError` | Sí (`SearchError`, `PricingError`) | **Se reemplaza**: el servicio define su propio enum de errores |
| `shared/exception/ResourceExceptionHandler`, `shared/dto/ErrorDTO`, `APIException` | — (el catálogo no tiene controller) | Sí | **Se duplica** la forma (`code`, `shortDescription`, `reasons`) para que los errores del servicio se vean igual que los del monolito |
| `shared/exception/BadRequestException`, `TWAException` | — (las lanzan C1 y `FlightHelper`) | Sí | Se quedan en el monolito |
| `shared/configuration/WebConfiguration` (Jackson `LOWER_CAMEL_CASE`, `FAIL_ON_UNKNOWN_PROPERTIES=false`; interceptor de pricing) | — | Sí | **Se duplica** solo la parte de Jackson. El interceptor de pricing no aplica |
| `shared/configuration/SwaggerConfiguration`, `shared/service/AppInfoService` | — | Sí | **Se duplican** (grupo propio y versión desde `build-info`) |
| `enums/ExceptionStatus` (`@Deprecated`) | Su Javadoc hace `{@link com.twa.flights.catalog.api.CatalogError}` | — | Nada ahora. Cuando `CatalogError` se mude (etapa 8) el `@link` queda roto (warning de Javadoc, no de compilación) |

### 3.2 Configuración (`src/main/resources/application.yml`, `pom.xml`, Docker)

| Elemento | Hoy (monolito) | En el servicio | Clasificación |
|----------|----------------|----------------|---------------|
| `spring.datasource.*` (`DB_HOST`, `DB_PORT:3320`, `DB_NAME:flights`, `root`/`muppet`) | Un solo usuario `root` | Misma base `flights` (etapas 2 a 6), **usuario de solo lectura** | Se duplica (las variables) / se reemplaza (el usuario) — ver **R12** |
| `spring.datasource.hikari.maximum-pool-size: 20` | 20 | Pool chico (p. ej. 5): consultas por PK/índice sobre 86 filas | Se duplica con otro valor — ver **R13** |
| `spring.flyway.locations: classpath:db/migration` | El monolito crea y migra `continent`, `country`, `city` | **Flyway apagado** en las etapas 2 a 6 (el dueño de `flyway_schema_history` es el monolito, ADR-002 consecuencias) | No se mueve — ver **R11** |
| `spring.jpa.hibernate.ddl-auto: validate`, `open-in-view: false` | — | Igual | Se duplica |
| `server.servlet.context-path: /api/flights`, `port: 8080`, Jetty (`threads 10..50`) | — | `/api/catalog`, puerto propio (p. ej. 8081), Jetty | Se duplica con otros valores |
| `management.*` (`health,info,metrics`) | — | Igual | Se duplica |
| `pom.xml`: web + Jetty, actuator, data-jpa, `mysql-connector-j`, MapStruct (+ processor), springdoc, `spring-boot-maven-plugin` con `build-info` | — | Mismas versiones (Boot 3.2.3, MapStruct 1.5.5, springdoc 2.3.0) | Se duplica |
| `pom.xml`: `flyway-*`, `guava`, `joda-time`, `commons-lang3`, `jackson-datatype-jsr310`, `wiremock` | — | **No hacen falta** (el catálogo no usa Guava ni Joda; Flyway apagado) | No se copian. Joda solo en el test de zonas horarias (ver 4) |
| `pom.xml`: Testcontainers MySQL | — | Sí | Se duplica |
| `docker-compose.yml` | 4 contenedores | Nuevo `twa-catalog-service`, que **depende de que el monolito haya migrado** | Ver **R11** |
| `Dockerfile` | `eclipse-temurin:21-jre` + jar | Igual | Se duplica |
| Monolito, config nueva | — | `catalog.mode`, `catalog.remote.host`, `connectionTimeout`, `readTimeout` (más los flags de la etapa 1) | Nuevo en el monolito |

---

## 4. Suite de tests

| Test | Toca el catálogo | Qué pasa |
|------|------------------|----------|
| `catalog/service/CatalogServiceTest` (5 tests: #26 ciudad completa, código inexistente → `null`, `mia` encontrado por la collation, 86 ciudades contra `cities.csv`, zonas válidas para Joda) | Directo | **Se duplica.** Se queda en el monolito (camino local) y se escribe su equivalente HTTP en el servicio: `null` pasa a ser 404 o "fuera de la lista"; `""` pasa a 404/400 |
| `CatalogServiceTest.timeZonesAreValid` | Directo | **Se queda en el monolito**: valida contra el tzdata de **Joda del monolito**, que es quien calcula la duración (riesgo 8). En el servicio se puede validar con `ZoneId`, pero eso no reemplaza a este |
| `support/AbstractIntegrationTest` | Indirecto (levanta todo el monolito + WireMock + `ClusterSearchJpaRepository`) | **Se reemplaza** en el servicio por una base liviana (MySQL de Testcontainers, sin WireMock ni búsqueda) |
| `support/Fixtures` | Lee `cities.csv` | **Se duplica** (es un helper de 20 líneas) |
| `src/test/resources/catalog/cities.csv` (86 filas: `code;name;time_zone;country;continent`) | Contrato de datos | **Se duplica** y es la fuente del mock de Microcks (etapa 2). Mientras haya dos copias, un chequeo tiene que compararlas |
| `search/controller/ClustersValidationTest` (40014 con `XXX` y `MI`; orden 40014 antes de 40012 en `severalErrors`) | Por C1 | **Se queda.** Tiene que pasar igual en `catalog.mode=local` y `remote` (contra el mock) |
| `provider/service/ProvidersSearchTest` (#15 duración con zonas; ciudad `XYZ` → itinerario descartado con `ERROR`) | Por C2 | **Se queda.** Mismo criterio de los dos modos |
| `search/controller/ClustersSearchTest`, `ClustersPricingTest` | Por C1 y C2 (BUE, MIA, SAO… del catálogo) | **Se quedan** |
| `provider/helper/FlightHelperTest` | **No**: `new FlightHelper(null)` | Se queda. Ojo: el día que el constructor cambie (lote, caché), este `null` deja de compilar o de alcanzar |
| Resto (`pricing`, mappers, serializer, helpers) | No | Sin cambios |
| **Falta** | — | Tests del `CatalogClient` (404 → `null`, 5xx/timeout → excepción, JSON con campos extra) y el test con el catálogo caído que pide la etapa 1 |

---

## 5. Estructura propuesta del servicio

Mismo estilo que el monolito (paquetes por capa técnica, POJOs con getters/setters, inyección por constructor con
`@Autowired`, `LOGGER` de SLF4J, Swagger en `controller/documentation`).

```
twa-catalog-service/
├── pom.xml                          Boot 3.2.3, Java 21, Jetty, JPA, MySQL, MapStruct, springdoc, actuator
├── Dockerfile
└── src/
    ├── main/java/com/twa/catalog/
    │   ├── CatalogApplication.java
    │   ├── configuration/          WebConfiguration (Jackson), SwaggerConfiguration
    │   ├── controller/             CityController
    │   │   └── documentation/      CityResources (interfaz con @Operation/@ApiResponse)
    │   ├── dto/                    CityDTO, CountryDTO, ContinentDTO, ErrorDTO
    │   ├── exception/              CatalogServiceError, NotFoundException, ResourceExceptionHandler
    │   ├── mapper/                 CityMapper (MapStruct)
    │   ├── model/                  City, Country, Continent (sin Base compartido, sin cascade)
    │   ├── repository/             CityRepository (findByCode, findByCodeIn)
    │   └── service/                CityService (normaliza a mayúsculas, filtra inexistentes)
    ├── main/resources/application.yml   context-path /api/catalog, flyway.enabled=false, ddl-auto=validate
    └── test/
        ├── java/com/twa/catalog/   CityControllerTest, CityServiceTest, support/AbstractIntegrationTest, Fixtures
        └── resources/
            ├── catalog/cities.csv
            └── db/                 copia del esquema y semilla (V1.0, V1.1, V1.5) solo para Testcontainers (R11)
```

En el monolito solo se agrega `catalog/client/**` (más su configuración) y el selector de `CatalogApi`.

---

## 6. DTOs

La forma JSON tiene que ser **idéntica** a la de `CityDTO` de hoy, porque `CatalogClient` la deserializa en el
`CityDTO` del monolito. `id` **no** se expone (hoy `BaseDTO` no lo tiene).

```json
{
  "code": "BUE",
  "name": "Buenos Aires",
  "timeZone": "America/Argentina/Buenos_Aires",
  "country": {
    "code": "AR",
    "name": "Argentina",
    "continent": { "code": "SA", "name": "South America" }
  }
}
```

| DTO (servicio) | Campos | Nota |
|----------------|--------|------|
| `CityDTO` | `code`, `name`, `timeZone`, `country` | `timeZone` es el **id** IANA, nunca un offset (ADR-002, D3) |
| `CountryDTO` | `code`, `name`, `continent` | — |
| `ContinentDTO` | `code`, `name` | — |
| `ErrorDTO` | `code`, `shortDescription`, `reasons` | Misma forma que `shared/dto/ErrorDTO` |

Del lado del monolito no hay DTOs nuevos: `CatalogClient` devuelve los `catalog/dto/*` existentes.

---

## 7. Endpoints

Base: `http://<host>:8081/api/catalog`.

| Método y ruta | Respuesta | Reglas |
|---------------|-----------|--------|
| `GET /cities/{code}` | `200` `CityDTO` · `404` `ErrorDTO` | Sin distinguir mayúsculas: el servicio normaliza a mayúsculas (decisión 3), no depende de la collation. **No** valida formato: `MI` o `ABCD` responden 404 (riesgo 5) |
| `GET /cities?codes=BUE,MIA` | `200` `[CityDTO]` | Los códigos inexistentes **no aparecen** (no es error). Sin repetidos. `codes` vacío o ausente → `400`. Tope sugerido: 50 códigos por pedido |
| `GET /actuator/health`, `/actuator/info`, `/actuator/metrics` | Actuator | `health` incluye la base |
| `GET /documentation` | Swagger UI | — |
| `POST/PUT/DELETE /cities` | — | **Fuera de esta clase** (etapa 6, con `X-Api-Key` y `catalog.admin.enabled`) |

Errores propios del servicio (no reutilizan el 40014, que es de BC1): `CITY_NOT_FOUND` (404) e `INVALID_CODES` (400),
con códigos de la misma convención `HTTP_nn`.

---

## 8. Dependencias a resolver

Ordenadas por gravedad. "Etapa" refiere al plan de extracción.

| # | Dependencia | Dónde (archivo · clase) | Riesgo si no se resuelve | Cómo se resuelve | Etapa |
|---|-------------|-------------------------|--------------------------|------------------|-------|
| R1 | **Contrato "`null` + excepción atrapada"**: `CatalogService.getCity` devuelve `null`; C1 lo convierte en NPE → `catch (Exception)` → 40014; C2 → NPE → `catch (Exception)` → itinerario descartado | `search/validator/AvailabilityRequestValidator` · `validateCodeWithCatalog`; `provider/service/Provider*Service` · `calculateFlightDuration(ItineraryDTO)`; `search/service/ItinerariesSearchService` · `makeProviderCalls` (l. 60) y `joinResults` (l. 76) | Con el servicio caído, la búsqueda da **40014** (C1) o **200 con lista vacía** (C2), sin señal | `CatalogClient`: 404 → `null` (igual que hoy); 5xx/timeout → `CatalogUnavailableException` que atraviesa los cuatro `catch` y sale como 503 | 1 (antes de cualquier tráfico remoto) |
| R2 | **N+1 de C2**: 2 `getCity` por segmento + 2 por tramo, por itinerario y proveedor | `provider/helper/FlightHelper` · `calculateFlightDuration` (l. 65-66, 73-74) | Decenas de llamadas HTTP por búsqueda | `getCities` en lote + caché (Caffeine, TTL 60 s, sin cachear inexistentes) | 1a, 1b |
| R3 | **Mayúsculas por collation**: `findByCode("mia")` encuentra `MIA` solo por la collation de MySQL (`CatalogServiceTest.codeInLowerCase`) | `catalog/repository/CityRepository` · `findByCode` | El servicio con otra collation u otra base cambia el resultado | Normalizar a mayúsculas en el servicio y en `CatalogService` | 1 |
| R4 | **Kernel de errores**: `CatalogError implements shared.exception.ErrorCode`; el manejo de errores HTTP está en `shared/exception/ResourceExceptionHandler` | `catalog/api/CatalogError`; `shared/exception/*` | El servicio no compila sin `shared` o mezcla el 40014 de BC1 | `CatalogError` se queda en el monolito; el servicio define `CatalogServiceError` + handler + `ErrorDTO` propios | 2 |
| R5 | **`Base` y `BaseDTO` solo los usa el catálogo** pero viven en `shared` | `shared/model/Base`; `shared/dto/BaseDTO` | Copiar `shared` entero al servicio (acoplamiento) | Campos propios en el servicio. En el monolito, al borrar el catálogo (etapa 8) quedan huérfanos: se borran con él | 2 / 8 |
| R6 | **Forma del JSON**: `CatalogClient` deserializa en `catalog/dto/CityDTO` del monolito | `catalog/dto/*`; `shared/configuration/WebConfiguration` (Jackson) | Un campo renombrado (`timeZone` → `time_zone`) da `timeZone=null` → `DateTimeZone` cae al default de la JVM **sin error** en `FlightHelper.timeBetweenDateTime` (salta el `if` de zonas vacías) y las duraciones salen mal en silencio | Test de contrato con el mock de Microcks; el cliente valida que `timeZone` no esté vacío | 2, 3 (la sombra compara `timeZone`) |
| R7 | **Dos beans de `CatalogApi`**: hoy hay uno (`CatalogService`) e inyección por tipo en los consumidores | `search/validator/AvailabilityRequestValidator` (constructor); `provider/helper/FlightHelper` (constructor) | `NoUniqueBeanDefinitionException` al arrancar | Un selector `@Primary` que delega en local/remoto según `catalog.mode`; `CatalogServiceTest` sigue inyectando la clase concreta | 2 |
| R8 | **Orden de validación**: el 40014 sale antes del 40012 porque `validateFromToValidCodes` corre antes de `validateFromToRoutes` y es síncrono | `AvailabilityRequestValidator` · `validateFromTo` | Un lote asíncrono o reordenado rompe `ClustersValidationTest.severalErrors` | El lote se llama en el mismo punto; el test corre en los dos modos | 1a, 4 |
| R9 | **Longitud del código**: el validador rechaza > 3 (40011), la columna admite 4, `MI` (2) llega al catálogo | `AvailabilityRequestValidator` · `validateFromToValidCodes`; `shared/model/Base` (`length = 4`) | Si el servicio valida formato, `MI` pasa de 40014 a otra cosa | El servicio no valida formato (404 / fuera de la lista) | 2 |
| R10 | **`cascade = CascadeType.ALL`** en `City.country` y `Country.continent` | `catalog/model/City`, `catalog/model/Country` | Con el ABM, un `save` de ciudad podría escribir país o continente | El servicio no usa cascada (y en la etapa 2 es solo lectura) | 2 / 6 |
| R11 | **Dueño del esquema**: el monolito crea y migra las tablas (`V1.0`, `V1.1`, `V1.5`) y es dueño de `flyway_schema_history` | `src/main/resources/db/migration/*`; `application.yml` (`spring.flyway`) | El servicio arranca antes que el monolito y `ddl-auto: validate` falla; los tests del servicio no tienen esquema | Servicio con Flyway apagado y `depends_on` del monolito sano en `docker-compose.yml`; copia de esquema + semilla **solo en los tests** del servicio (sin editar las migraciones) | 2 (base propia: etapa 7) |
| R12 | **Credenciales**: hoy `root`/`muppet` | `application.yml` (`spring.datasource`); `docker-compose.yml` (`MYSQL_ROOT_PASSWORD`) | El servicio puede escribir cualquier tabla de `flights` | Usuario con `SELECT` sobre `continent`, `country`, `city`, creado por un script de init del contenedor (no es un cambio de datos de Flyway) | 2 |
| R13 | **Conexiones a la misma MySQL**: monolito `maximum-pool-size: 20` por instancia | `application.yml` (`spring.datasource.hikari`) | 2 monolitos + 2 servicios contra el `max_connections` (151 por defecto) | Pool chico en el servicio; medir `hikaricp.connections.pending` | 2 |
| R14 | **Sin `UNIQUE` en `city.code`** | `V1.0__create_catalog_tables.sql`; `CityRepository.findByCode` | Un duplicado da `IncorrectResultSizeDataAccessException` (hoy, 40014 silencioso) y en el lote, dos ciudades con el mismo código | Migración nueva con el índice (siguiente `V1.<n>` libre) | 1c |
| R15 | **Zonas horarias y Joda**: la duración se calcula con el tzdata de Joda del monolito | `provider/helper/FlightHelper` · `timeBetweenDateTime`; `CatalogServiceTest.timeZonesAreValid` | Un id válido en `java.time` del servicio pero no en Joda rompe C2 | El servicio devuelve el id; el test con Joda se queda en el monolito | — |
| R16 | **Hubs de Microcks** (`PTY, LIM, BOG, MIA, SAO, SCL, MEX`) tienen que existir en el catálogo | `microcks/generate-contracts.py` (`dispatcher`, `hubs`) | Itinerarios con conexión descartados (ciudad inexistente en C2) | Agregar el mock del catálogo al generador, alimentado con `cities.csv`; chequear que los hubs estén | 2 |
| R17 | **Regla de dependencias** | `scripts/check-module-dependencies.sh` (`ALLOWED`) | `catalog.client` → `shared` o → `catalog.dto` puede marcarse como violación o quedar sin control | Agregar la regla para `catalog.client` | 2 |
| R18 | **Código que no encaja** en el catálogo pero depende de él: `ExceptionStatus` (Javadoc a `CatalogError`); `FlightHelperTest` (`new FlightHelper(null)`) | `enums/ExceptionStatus`; `src/test/.../provider/helper/FlightHelperTest` | Warnings de Javadoc o tests que dejan de compilar cuando cambie el constructor | Revisarlos en el commit que los toque | 1 / 8 |

---

## 9. Resumen

| Categoría | Elementos |
|-----------|-----------|
| **Se mueve** (copia ahora, borrado en el monolito en la etapa 8) | `City`, `Country`, `Continent`, `CityRepository`, `CityMapper`, lógica de `CatalogService` |
| **Se duplica** | `CityDTO`, `CountryDTO`, `ContinentDTO` (forma JSON); forma de `ErrorDTO`; Jackson de `WebConfiguration`; `SwaggerConfiguration`/`AppInfoService`; actuator, Jetty, `Dockerfile`; `cities.csv`, `Fixtures` y los tests de `CatalogServiceTest` (salvo zonas con Joda) |
| **Se reemplaza** | `shared.model.Base` y `shared.dto.BaseDTO` → campos propios; `ErrorCode`/`CatalogError` → errores propios del servicio; `AbstractIntegrationTest` → base liviana; usuario `root` → solo lectura; `CatalogService` (en el monolito) → `CatalogClient` detrás de `CatalogApi` |
| **Se queda en el monolito** | `CatalogApi`, `CatalogError` (a BC1 en la etapa 8), migraciones de Flyway, tests de C1 y C2, `timeZonesAreValid` |

## 10. Rarezas encontradas (no se tocan)

- Los archivos `analisis-*.txt`/`.sql` están en `docs/modulo-2/clase-1-…/`, aunque `docs/README.md` dice que van en
  la raíz del proyecto, ignorados por git.
- `FlightHelper.timeBetweenDateTime` ignora las dos zonas si **una** está vacía y calcula con la zona de la JVM: un
  `timeZone` nulo no da error (relevante para R6).
- `APIException(HttpStatus, int, String, Throwable)` no asigna `status` (queda `null`). No afecta al catálogo, pero
  si el servicio copiara el kernel heredaría el problema.
