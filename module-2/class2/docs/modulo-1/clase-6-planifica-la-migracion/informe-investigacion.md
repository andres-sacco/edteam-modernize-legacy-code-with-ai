# Informe de investigación: extracción del catálogo de ciudades (BC4)

Análisis de solo lectura. Cada hallazgo cita archivo y clase. Las afirmaciones que no salen directo del código están
marcadas con **[H]** (hipótesis a validar).

Base: el [context map](../clase-3-que-partes-podemos-separar/context-map.md) (clase 3), los
[comportamientos a proteger](../clase-4-crea-una-red-de-seguridad/comportamientos-a-proteger.md) y los
[tests de caracterización](../clase-4-crea-una-red-de-seguridad/tests-de-caracterizacion.md) (clase 4), y el
[plan de refactor](../clase-5-prepara-el-monolito/plan-de-refactor.md) con las
[reglas de dependencias](../clase-5-prepara-el-monolito/reglas-de-dependencias.md) (clase 5).

## 1. Superficie del módulo

- **Contrato publicado:** `CatalogApi.getCity(String code)`, en
  [CatalogApi.java](../../../src/main/java/com/twa/flights/catalog/api/CatalogApi.java). Devuelve un `CityDTO`
  (código, nombre, `timeZone`, `country` → `continent`) **o `null`** si el código no existe.
- **Error publicado:** `CatalogError.INCOMPLETE_CATALOG_INFORMATION` (40014), en
  [CatalogError.java](../../../src/main/java/com/twa/flights/catalog/api/CatalogError.java). Implementa
  `shared.exception.ErrorCode`.
- **Implementación:** `CatalogService` → `CityRepository.findByCode` → `CityMapper` (MapStruct), en
  [CatalogService.java](../../../src/main/java/com/twa/flights/catalog/service/CatalogService.java).
- **Dependencias del kernel:** `CityDTO`/`CountryDTO`/`ContinentDTO` heredan de `shared.dto.BaseDTO`, y
  `City`/`Country`/`Continent` heredan de `shared.model.Base`.
- **No hay endpoint HTTP.** El [README](../../../README.md) lo declara así: el catálogo es lógica interna.

## 2. Consumidores

### Internos (llamadas Java en el mismo proceso)

| # | Consumidor | Contexto | Uso | Qué hace con un `null` o una excepción |
|---|---|---|---|---|
| C1 | `AvailabilityRequestValidator.validateCodeWithCatalog` ([AvailabilityRequestValidator.java](../../../src/main/java/com/twa/flights/search/validator/AvailabilityRequestValidator.java), líneas 219-231) | BC1 Búsqueda | Valida cada código de `from`/`to` y exige que tenga código, país y continente | `catch (Exception)` convierte **cualquier** excepción, incluida la NPE por `null`, en 400/40014 |
| C2 | `FlightHelper.calculateFlightDuration(SegmentDTO / LegDTO)` ([FlightHelper.java](../../../src/main/java/com/twa/flights/provider/helper/FlightHelper.java), líneas 61-78) | BC2 Proveedores | Lee solo `timeZone` del origen y del destino | NPE → `Provider{Alpha,Beta,Gamma}Service.calculateFlightDuration` la atrapa, loguea ERROR y **descarta el itinerario** ([ProviderAlphaService.java](../../../src/main/java/com/twa/flights/provider/service/ProviderAlphaService.java), líneas 50-61) |

Consumidores indirectos de C2: `ProviderAlphaService`, `ProviderBetaService` y `ProviderGammaService`, que se ejecutan
en paralelo desde `ItinerariesSearchService` (pool fijo de 10 hilos, línea 34 de
[ItinerariesSearchService.java](../../../src/main/java/com/twa/flights/search/service/ItinerariesSearchService.java)).

[check-module-dependencies.sh](../../../scripts/check-module-dependencies.sh) permite solo `search → catalog.api|dto`
y `provider → catalog.api|dto`. El corte X1 de la clase 5 (`FlightHelper` leía `CityRepository` directo) ya está
aplicado: hoy no hay otro acceso a las clases internas fuera de `catalog`.

### Externos (fuera del proceso)

- **Ninguno directo.** El catálogo no tiene endpoint HTTP.
- **Cliente de `GET /clusters/itineraries`, de forma indirecta:**
  - recibe 40014 cuando el catálogo no tiene un código;
  - recibe duraciones `HH:mm` calculadas con las zonas horarias;
  - ve desaparecer los itinerarios que tienen una ciudad fuera del catálogo.
- **La paginación también consulta el catálogo:** el validador se ejecuta igual cuando se pagina con `id`
  (comportamiento #31 de la clase 4).
- **Proveedores simulados (Microcks):** los "hubs" de conexión de
  [generate-contracts.py](../../../microcks/generate-contracts.py) (`PTY, LIM, BOG, MIA, SAO, SCL, MEX`) tienen que
  existir en el catálogo. Hoy están las 7. Si un proveedor devuelve una ciudad que no está, el itinerario se descarta
  en silencio.

## 3. Datos que mueve

| Dato | Origen | Volumen y forma |
|---|---|---|
| `continent`, `country`, `city` | [V1.0](../../../src/main/resources/db/migration/V1.0__create_catalog_tables.sql) (esquema), [V1.1](../../../src/main/resources/db/migration/V1.1__insert_catalog_rows.sql) (30 ciudades), [V1.5](../../../src/main/resources/db/migration/V1.5__insert_more_cities.sql) (56 ciudades) | 86 ciudades, de solo lectura. Las únicas FK son internas (`city → country → continent`) |
| Campos que se consumen | C1: `code`, `country.code`, `continent.code`. C2: solo `timeZone` | Nadie usa `name` en producción |
| Volumen de llamadas | C2: 2 por tramo + 2 por segmento | Ejemplo: un ida y vuelta con una conexión por tramo hace 12 `getCity` por itinerario, por proveedor. Hoy cada llamada es una consulta a MySQL; no hay caché (no hay `@Cacheable` en el código) |
| Errores | `CatalogError` 40014 | Lo define el catálogo pero lo emite la búsqueda |

## 4. Riesgos de la extracción

1. **El contrato real es "`null` + NPE atrapada", no `getCity`.** Ninguno de los dos consumidores trata el `null` de
   forma explícita. Con un cliente remoto:
   - **C1:** un timeout o un 5xx del servicio de catálogo terminaría en **400/40014**. Una caída del catálogo se vería
     como un error del cliente. **[H]** Esto aplica si se mantiene el `catch (Exception)`.
   - **C2:** una caída descartaría **todos** los itinerarios. La respuesta sería un 200 con la lista vacía, sin ningún
     error visible para el cliente. Es el riesgo más grave.
2. **Latencia (N+1 por red).** Hoy son consultas locales. Remotas serían decenas o cientos de HTTP por búsqueda,
   repartidas entre 3 proveedores en paralelo, con un pool de 10 hilos y una Hikari de 20 conexiones. **[H]** Sin
   caché en el consumidor, o sin una operación en lote del tipo `getCities(codes)` o `getTimeZones(codes)`, la
   búsqueda se degrada.
3. **No distingue mayúsculas por la collation de MySQL.** `getCity("mia")` funciona solo por la collation; está fijado
   en `CatalogServiceTest.codeInLowerCase`. Un servicio nuevo con otra base o con un mapa en memoria rompería esto en
   silencio.
4. **Orden de validación.** El catálogo (40014) gana sobre "origen = destino" (40012). Está fijado en
   `ClustersValidationTest.severalErrors`. Si la validación se mueve o se vuelve asíncrona, el orden no puede cambiar.
5. **Inconsistencias de longitud del código.** `AvailabilityRequestValidator` rechaza códigos de más de 3 caracteres
   (40011), pero la columna admite 4. Además, `MI` (2 letras) llega al catálogo y da 40014. Un servicio nuevo que
   valide el formato por su cuenta cambiaría el código de error.
6. **El código no tiene `UNIQUE` ni índice** (V1.0). Hoy hay 86 códigos distintos. Si apareciera un duplicado,
   `findByCode` lanzaría una excepción que C1 convertiría en 40014. **[H]** Conviene decidir si el servicio nuevo
   garantiza unicidad.
7. **Datos semilla en un historial de Flyway compartido.** Los IDs están hardcodeados en V1.1 y V1.5 resuelve el país
   por código. Separar la base implica partir `flyway_schema_history` sin editar las migraciones existentes. Además,
   dar de alta un proveedor hoy toca el catálogo: V1.5 se agregó junto con Gamma.
8. **Zonas horarias y Joda-Time.** La duración depende de la base tzdata de la JVM. Si el servicio de catálogo y el
   consumidor usan otra versión de tzdata o pasan a `java.time`, las duraciones cerca de un cambio de horario de
   verano pueden variar. **[H]**
9. **`cascade = ALL` en `@ManyToOne`** (`City.country`, `Country.continent`). Hoy es inocuo porque nadie escribe. Si
   el servicio nuevo agrega ABM, la cascada podría modificar países o continentes sin querer.
10. **Red de seguridad acoplada a MySQL.**
    - `CatalogServiceTest` usa `CityRepository` directamente (prueba interna).
    - `ProvidersSearchTest` (`durationOfTheFlights`, `cityOutsideTheCatalogDiscardsTheItinerary`) y
      `ClustersValidationTest` necesitan el catálogo cargado vía Testcontainers.
    - Al extraer hace falta un stub o un contrato (por ejemplo, otro mock en Microcks). La foto
      `src/test/resources/catalog/cities.csv` sirve como contrato de datos.
11. **Kernel compartido.** `CityDTO` hereda de `shared.dto.BaseDTO` y `CatalogError` de `shared.exception.ErrorCode`.
    El JSON publicado tiene que reproducir esa forma sin arrastrar el kernel.

### Rareza observada (no se toca)

- **Bariloche (`BRC`) tiene `America/Argentina/Salta`** (V1.5, línea 8). Hoy da el mismo offset, pero es un dato a
  revisar.

## 5. Decisiones del equipo

| # | Pregunta | Decisión | Impacto en los riesgos |
|---|---|---|---|
| 1 | ¿Qué devuelve la búsqueda si el catálogo no responde? | **503.** Una caída del catálogo deja de verse como un 40014 | Riesgo 1 (C1): hay que distinguir "código inexistente" (sigue 40014) de "catálogo caído" (503). El `catch (Exception)` de `AvailabilityRequestValidator` ya no alcanza. Es un **cambio de comportamiento** respecto de lo caracterizado en la clase 4 (#6, #26) |
| 2 | ¿Operación en lote y caché en los consumidores? | **Sí:** se agrega `getCities(codes)` (o equivalente) y caché en los consumidores | Riesgo 2 |
| 3 | ¿Códigos únicos y sin distinguir mayúsculas? | **Sí:** el código es único y no distingue mayúsculas de minúsculas. Es parte del contrato | Riesgos 3 y 6: el servicio nuevo tiene que garantizarlo con un índice `UNIQUE` y normalizando el código, sin depender de la collation de MySQL |
| 7 | ¿Cómo se dan de alta ciudades? | **ABM** en el servicio de catálogo | Riesgos 7 y 9: los datos dejan de ser de solo lectura. La `cascade = ALL` pasa a ser un riesgo real, y la caché del punto 2 necesita invalidación o un TTL corto. Las ciudades semilla siguen viniendo de V1.1 y V1.5 |

## 6. Preguntas abiertas para el equipo

1. Con el 503 de la decisión 1: ¿qué hacen los proveedores (C2) si el catálogo no responde? ¿Descartan el itinerario
   (comportamiento actual), lo devuelven sin duración o hacen fallar toda la búsqueda con 503?
2. Con el ABM de la decisión 7: ¿qué TTL tiene la caché de los consumidores, o cómo se invalida (por ejemplo, con un
   evento de ciudad modificada)?
3. ¿El ABM permite borrar o cambiar el código de una ciudad? ¿Qué pasa con las búsquedas guardadas en `cluster_search`
   que la usan? **[H]**
4. ¿BC2 debería recibir solo `timeZone` (`getTimeZone(code)`) en lugar del `CityDTO` completo?
5. ¿Quién es dueño del 40014: el catálogo o la búsqueda?
6. ¿Qué estrategia usamos para los datos?
   - **Strangler con la misma base**, como primer paso. Con un ABM, el monolito y el servicio escribirían o leerían la
     misma tabla durante la convivencia.
   - **Base propia**, con un nuevo baseline de Flyway.
7. **Branch by Abstraction:** `CatalogApi` ya es la abstracción. ¿Hacemos la convivencia con un feature flag entre
   `CatalogService` (local) y un `CatalogClient` (remoto)? ¿Comparamos resultados en sombra antes de cortar?
