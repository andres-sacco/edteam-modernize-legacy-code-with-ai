# Comportamientos a proteger antes de migrar

**Clase:** M1 · C4 · Crea una red de seguridad (paso 1, investigar)
**Rol:** QA de sistemas legacy Java/Spring Boot · **Alcance:** solo lectura, sin tests todavía
**Fuentes:** código de `src/main/java/com/twa/flights`, migraciones de Flyway, `microcks/generate-contracts.py`,
`scripts/smoke-test.sh` y los ejemplos reales de `ejemplos/` (búsqueda ida y vuelta, solo ida business, ciudad
inválida y logs del arranque).

## Cómo leer este documento

- **Módulos.** Uso los cuatro nombres del pedido y los mapeo a los bounded contexts del
  [ADR-001](../clase-3-que-partes-podemos-separar/adr-001-division-en-bounded-contexts.md):
  - **Búsqueda** = BC1 (búsqueda, filtros, orden, paginación, `cluster_search`).
  - **Aerolíneas** = BC2 "Integración con proveedores" (connectors, mappers y facades de Alpha, Beta y Gamma). El ADR
    aclara que "Aerolíneas" no existe como módulo propio: el carrier es solo un código de 2 caracteres que llega de los
    proveedores.
  - **Precios** = BC3 (cálculo del markup y administración de políticas comerciales).
  - **Catálogo** = BC4 (`continent`, `country`, `city`).
- **El código todavía está organizado por capa técnica** (`service`, `helper`, `mapper`...), no por módulo. Las
  citas usan la ruta relativa a `src/main/java/com/twa/flights/`.
- **Evidencia.** "Observado" significa que lo confirma un ejemplo real de `ejemplos/`. "Código" significa que se lee
  directamente del código. **Hipótesis** significa que se deduce del código pero no hay ejemplo que lo compruebe:
  hay que confirmarlo antes de fijarlo en un test.
- **Riesgo** = impacto para el negocio si se rompe × probabilidad de que la migración lo rompa.
  - **Alto:** toca dinero, contratos externos o la disponibilidad de la búsqueda.
  - **Medio:** cambia resultados o mensajes que ve el usuario.
  - **Bajo:** casos borde o comportamiento interno.
- **Tipo de test.**
  - **Caracterización:** fija la salida actual de una clase o un endpoint para entradas conocidas, sin juzgar si es
    correcta.
  - **Integración:** necesita base de datos real, Spring completo o varios módulos juntos.
  - **Contrato:** fija el formato de un borde (API pública, proveedores o persistencia).
- **Dificultad** = esfuerzo para que el test sea estable y repetible.

## Tabla de comportamientos (ordenada por riesgo)

| # | Comportamiento | Módulo | Evidencia (archivo · clase) | Riesgo | Tipo de test | Dificultad |
|---|----------------|--------|-----------------------------|--------|--------------|------------|
| 1 | **Fórmula del markup:** `markup = subtotal × porcentaje / 10` (no `/100`). `total = subtotal + markup`. Se calcula para cada tipo de pasajero. El JSON muestra `percentage` sin cambios. | Precios | `service/PricingService.java` · `PricingService.calculateMarkupByType`. **Observado:** AR 5.00 sobre subtotal 730.22 da markup 365.1100 (50 %). G3 2.00 sobre 2118.55 da 423.71 (20 %). | Alto | Caracterización | Media: necesita políticas cargadas y precios de proveedor fijos. Microcks los devuelve al azar. |
| 2 | **Una política se aplica cuando:** está `active`, `startDate ≤` salida del primer tramo del primer segmento, `expireDate ≥` **llegada del primer tramo del último segmento** (no del último tramo) y tiene el carrier del itinerario. Si hay varias, gana la de `priority` **más alta**. Con los datos semilla, "General" (10) le gana a "Special Carrier" (5) y a "Ciber Week" (1). | Precios | `specification/CommercialPolicyCarrierSpecification.java` · `toPredicate`, `convertDeparture`, `convertArrival`. `service/PricingService.java` · `calculatePrices` (`findFirst`). Datos: `V1.3`, `V1.6`. | Alto | Integración | Alta: es una Criteria de JPA sobre MySQL. Depende de las fechas. **Hipótesis:** con dos políticas de igual prioridad, el orden no está definido. |
| 3 | **Un itinerario sin política aplicable se descarta en silencio.** No aparece en la respuesta y no se loguea. | Precios | `service/PricingService.java` · `priceItineraries` (filtro `getMarkup() != null`) | Alto | Caracterización | Media: hay que armar un itinerario de un carrier sin política. |
| 4 | **Un cambio de política se ve en las búsquedas nuevas al instante** (no hay caché). Las búsquedas guardadas **conservan el precio** con el que se crearon: se guarda una foto. | Precios + Búsqueda | `service/PricingService.java` (`findAll` en cada búsqueda) · `repository/impl/ClustersRepositoryImpl.java` · `insert` (payload JSON completo) | Alto | Integración | Alta: es un flujo de punta a punta entre dos módulos y con estado en la base. |
| 5 | **La administración de pricing exige el header `X-Api-Key`** en `/pricing/**`. Sin él o con un valor inválido responde 401 con `code 40101`. La búsqueda es pública. | Precios | `configuration/PricingApiKeyInterceptor.java` · `preHandle`. `configuration/WebConfiguration.java` · `addInterceptors`. `scripts/smoke-test.sh` lo verifica. | Alto | Integración | Baja |
| 6 | **Validación de la búsqueda, en este orden:** fechas (vacía 40003, formato 40007, pasado 40009, desordenadas 40008), pasajeros (adultos > 0 40004, niños ≥ 0 40005, infantes ≥ 0 40006, adultos + niños ≤ 9 40010), origen y destino (vacíos 40001/40002, código > 3 caracteres 40011, catálogo 40014, iguales 40012, ruta duplicada 40013), orden (40016/40017), cabina (40018) y filtros (40019 a 40022). Gana el primer error. Los enums aceptan minúsculas y espacios. | Búsqueda | `validator/AvailabilityRequestValidator.java` · `validate`. `enums/ExceptionStatus.java`. **Observado:** la fecha pasada da 400 (smoke test). | Alto | Caracterización | Media: usa `LocalDate.now()`, así que necesita fechas relativas o un reloj fijo. Cada código consulta el catálogo. |
| 7 | **Forma del error:** `{code, shortDescription, reasons}`. Los 400 de validación tienen `reasons: null`. Los códigos son enteros de 5 dígitos (`400_14` → `40014`). | Búsqueda / Precios | `exception/ResourceExceptionHandler.java` · `badRequest`, `resourceNotFound`. `dto/ErrorDTO.java`. **Observado:** `busqueda-ciudad-invalida.json`. | Alto | Contrato | Baja |
| 8 | **Contrato de la respuesta de búsqueda:** `{id, pagination{offset,limit,total}, itineraries[]}`. Cada itinerario tiene `segments[].legs[]`, `priceInfo{adults,children,infants}` (con `null` cuando no hay pasajeros de ese tipo), `provider`, `flightType`, `carrier`, `cabin` y `baggage{carryOn,checked}`. Los campos van en lowerCamelCase. | Búsqueda | `dto/ClusterSearchDTO.java`, `dto/ItineraryDTO.java`. `configuration/WebConfiguration.java` · `configureMessageConverters`. **Observado:** los dos JSON de búsqueda. | Alto | Contrato | Media: hay que ignorar los valores aleatorios (UUID, horarios, precios) y validar solo la forma. |
| 9 | **Los decimales no se redondean:** `total`, `amount` y `percentage` salen con la escala que deja `BigDecimal` (por ejemplo `1095.3300`, `1082.6370`, `5.00`). Un cliente o una migración que redondee cambia el contrato. | Precios | `service/PricingService.java` · `calculateMarkupByType`. **Observado** en el JSON crudo de los ejemplos. | Alto | Contrato | Media: hay que comparar el texto del JSON, no el valor numérico. |
| 10 | **Si un proveedor falla, la búsqueda sigue.** Un error HTTP, un timeout (5 s) o una excepción de un proveedor deja su lista vacía y la respuesta es 200 con el resto. **Hipótesis:** si fallan los tres, la respuesta es 200 con `itineraries: []` y la búsqueda vacía igual se guarda. | Aerolíneas | `service/ItinerariesSearchService.java` · `makeProviderCalls`, `joinResults`. `connector/*Connector.java` · `onStatus` (lanza `TWAException`). `application.yml` (timeouts). | Alto | Integración | Alta: Microcks no simula fallas. Hace falta un stub con errores y demoras. |
| 11 | **Mapeo de Alpha:** la cabina usa códigos de reserva (`Y`/`W`/`C`), cada `journeys` es un segmento, las tarifas vienen por tipo (`ADT`/`CHD`/`INF`) y el equipaje sale de `luggage.cabin`/`hold`. Una cabina desconocida descarta la oferta. | Aerolíneas | `mapper/provider/AlphaItineraryMapper.java` · `map`, `getCabin`, `getFare` | Alto | Contrato | Media: los contratos de Microcks son aleatorios. Conviene tomar fixtures de `microcks/provider-alpha-openapi.json`. |
| 12 | **Mapeo de Beta:** si `status != "OK"`, devuelve una lista vacía. Los precios llegan en centavos y se dividen por 100. El segmento `OUTBOUND` va siempre primero. La cabina se resuelve con `CabinClass.valueOf(upper)`. | Aerolíneas | `mapper/provider/BetaItineraryMapper.java` · `map`, `getFare`, `getSegment` | Alto | Contrato | Media (igual que el #11) |
| 13 | **Mapeo de Gamma:** los vuelos se agrupan por `segmentIndex` y se ordenan por `sequence`. El equipaje viene como texto `nPC[mKG]`. La cabina usa `ECO`/`PEY`/`BUS`. Un formato desconocido descarta el itinerario. | Aerolíneas | `mapper/provider/GammaItineraryMapper.java` · `getSegments`, `getAllowance`, `getCabin` | Alto | Contrato | Media (igual que el #11) |
| 14 | **Búsqueda guardada y paginación:** la búsqueda se guarda en `cluster_search` **de forma asíncrona** con un TTL fijo de 5 minutos. Paginar un id inexistente o vencido da **HTTP 502** con `code 40015` (el código dice 400 y el estado dice 502). Pedir un itinerario de una búsqueda inexistente da 404 `40401`. Un itinerario inexistente da 404 `40402`. | Búsqueda | `repository/impl/ClustersRepositoryImpl.java` · `insert` (`executorService.submit`), `get`. `service/ClustersService.java` · `availabilityFromDatabase`, `getItinerary`. | Alto | Integración | Alta: hay una carrera entre la respuesta y el guardado (el smoke test espera a propósito). El TTL está fijo en el código y usa el reloj de la app. |
| 15 | **Duración de vuelos:** se calcula con la zona horaria de cada ciudad del catálogo, para cada tramo y cada segmento, con formato `HH:mm`. Si la llegada es anterior a la salida, o la ciudad no está en el catálogo (NPE), **el itinerario se descarta** y se loguea un ERROR. | Aerolíneas + Catálogo | `helper/FlightHelper.java` · `timeBetweenDateTime`, `calculateFlightDuration`. `service/Provider*Service.java` · `calculateFlightDuration`. **Observado:** BUE→MIA de 04:45 a 23:45 da `21:00` (UTC-3 a UTC-5). | Alto | Caracterización | Alta: depende del horario de verano (fecha), de Joda-Time y de que el catálogo esté en la base. |
| 16 | **`amount` se aplica por proveedor**, no al total. Cada connector lo manda al proveedor y además recorta la lista, así que la búsqueda junta hasta 3 × `amount`. La primera página trae `amount` itinerarios. Sin `amount` todo va en una sola página. | Búsqueda + Aerolíneas | `connector/*Connector.java` · `availability` (`limit`). `service/ClustersService.java` · `getPageSize`. **Observado:** `amount=5` da `total: 10`. Sin `amount` da `limit = total = 14`. | Medio | Caracterización | Media |
| 17 | **Paginación:** `offset` es el **número de página**, no de registros (`skip = offset × amount`). Cada página vuelve a aplicar los filtros y el orden sobre la foto guardada. `total` es la cantidad después de filtrar. | Búsqueda | `service/ClustersService.java` · `buildPage` | Medio | Caracterización | Media: necesita una búsqueda guardada (ver el #14). |
| 18 | **Deduplicación:** la clave es `provider|carrier@|duración|origen-destino(número)fecha_hora_...` por segmento. Ante duplicados queda el **más barato antes del markup**. Como la clave incluye el proveedor, **no se eliminan duplicados entre proveedores**. | Búsqueda | `helper/FlightDuplicationCheckHelper.java` · `removeDuplicateFlights`, `isBetterPrice`. `helper/ItineraryKeyGeneratorHelper.java` · `generateKeyForItinerary`. | Medio | Caracterización | Baja: es lógica pura. **Hipótesis:** el orden previo al sort sale de un `HashMap`. |
| 19 | **Filtros:** `maxStops` toma el segmento con más escalas (`legs - 1`). `minPrice` y `maxPrice` usan el total con markup, con límites inclusivos. `carriers` y `excludedCarriers` son listas. `departureTime` toma la hora del primer tramo de ida, con inicio inclusivo y fin exclusivo. `checkedBaggage` mira si `checked.pieces > 0`. | Búsqueda | `helper/ItineraryFilterHelper.java` · `filter`, `getStops`, `getDepartureHour`, `hasCheckedBaggage`. `enums/DepartureTimeRange.java`. | Medio | Caracterización | Baja |
| 20 | **Orden:** por defecto `PRICE ASC`. Con `CARRIER` o `DURATION`, el desempate es por precio **siempre ascendente**, también con `DESC`. `DURATION` suma todos los segmentos. `CARRIER` pone los `null` al final. | Búsqueda | `helper/ItinerarySortHelper.java` · `sort`, `getComparator`. **Observado:** los dos ejemplos vienen ordenados por total ascendente. | Medio | Caracterización | Baja. **Hipótesis:** con precios empatados, el orden no es determinístico (ver el #18). |
| 21 | **Precio por pasajero desde el proveedor:** `subtotal = (base + tax) × cantidad` y `total = subtotal` antes del markup. `children` e `infants` solo existen si la cantidad es mayor que 0. Si el proveedor no trae la tarifa de un tipo de pasajero pedido, el itinerario se descarta. | Aerolíneas | `mapper/provider/ProviderMapperHelper.java` · `createPriceInfo`, `createPaxPrice`. **Observado:** 2 × (339.39 + 25.72) = 730.22. | Medio | Caracterización | Baja |
| 22 | **Reglas comunes del mapeo:** el número de vuelo se queda solo con los dígitos (`AR1234` → `1234`). La hora se corta a `HH:mm`. Sin piezas de equipaje el peso es `null`. `flightType` es `ROUND_TRIP` si `to` tiene más de un elemento. | Aerolíneas | `mapper/provider/ProviderMapperHelper.java` · `createLeg`, `createAllowance`, `getFlightType`. **Observado:** `checked {pieces: 0, weightKg: null}`. | Medio | Caracterización | Baja |
| 23 | **Pedido a los proveedores:** solo se mandan el **primer** origen, el primer destino y `departure[0]`. `returnDate` es `departure[1]`. La cabina va en mayúsculas y, si falta, es `ECONOMY`. `amount` se manda solo si viene en el pedido. Las rutas multidestino de más de dos tramos se ignoran. | Aerolíneas | `connector/AlphaConnector.java`, `BetaConnector.java`, `GammaConnector.java` · `availability` | Medio | Contrato (lado consumidor) | Media: hay que capturar la URL que se manda (stub HTTP). |
| 24 | **Una oferta mal formada no rompe la búsqueda:** se descarta solo esa oferta y se loguea un WARN. | Aerolíneas | `mapper/provider/*ItineraryMapper.java` · `map(response, ...)` (`catch RuntimeException`) | Medio | Caracterización | Baja |
| 25 | **ABM de políticas:** `POST` responde 201 con `Location` y la política queda activa si no se indica otra cosa. `PUT` reemplaza todo, incluidos los carriers, y conserva los ids de los que siguen. `PATCH /status` cambia el estado. `DELETE` responde 204. `PUT /carriers/{c}` hace alta o modificación. `DELETE` del último carrier da 40026. Los códigos se normalizan a mayúsculas. Los errores van de 40023 a 40029 y los 404 son `40403`/`40404`. | Precios | `controller/CommercialPolicyController.java`. `service/CommercialPolicyService.java` · `create`, `update`, `syncCarriers`, `saveCarrier`, `deleteCarrier`. `validator/CommercialPolicyValidator.java`. | Medio | Integración | Media: necesita MySQL real (migración `V1.7` con `AUTO_INCREMENT` y la restricción única) y limpiar la base entre tests. |
| 26 | **Consulta del catálogo:** `getCity(code)` devuelve la ciudad con país y continente. Hay 86 ciudades semilla con zona horaria. Un código desconocido o incompleto termina en 40014, porque el validador **se traga cualquier excepción**. | Catálogo | `service/CatalogService.java` · `getCity`. `mapper/CityMapper.java`. `repository/CityRepository.java`. `validator/AvailabilityRequestValidator.java` · `validateCodeWithCatalog`. **Observado:** `XXX` da 40014. | Medio | Integración | Baja: usa Flyway sobre MySQL. Los datos semilla son parte del comportamiento. |
| 27 | **Persistencia de la búsqueda:** el payload se guarda en snake_case, mientras que la API usa camelCase. Al leerlo debe reconstruir el mismo `ClusterSearchDTO`, con markups y `null` incluidos. **Hipótesis:** si `serialize` falla, devuelve `null`, el `save` asíncrono falla en silencio y la búsqueda nunca se puede paginar. | Búsqueda | `serializer/JsonSerializer.java` · `serialize`, `deserialize`. `model/ClusterSearch.java`. | Medio | Contrato | Baja |
| 28 | **Listado de políticas:** se ordena por `priority` descendente y después por `id`. Se puede filtrar por `active` y `carrier` (sin distinguir mayúsculas). | Precios | `service/CommercialPolicyService.java` · `getPolicies` | Bajo | Caracterización | Baja |
| 29 | **Limpieza de búsquedas vencidas:** un job corre cada 60 s y borra las filas con `expire_at < now`. | Búsqueda | `job/ClusterSearchCleanupJob.java` · `removeExpiredSearches`. `ClusterSearchJpaRepository.deleteExpired`. | Bajo | Integración | Media: hay que controlar el reloj o llamar al método directo. |
| 30 | **Hipótesis: faltan parámetros y responde 500.** Sin `children` o `infants` hay un NPE por unboxing en `validatePassengerCount`. Con `id` y sin `offset` hay un NPE en `buildPage`. Con más orígenes que destinos hay un `IndexOutOfBoundsException` en `validateFromToRoutes`. Ninguno está mapeado en `ResourceExceptionHandler`. | Búsqueda | `validator/AvailabilityRequestValidator.java` · `validatePassengerCount`, `validateFromToRoutes`. `service/ClustersService.java` · `availabilityFromDatabase`. | Bajo | Caracterización | Baja |
| 31 | **Hipótesis: al paginar no se comparan los parámetros con el `id`.** Se validan `from`, `to` y `departure` (y se consulta el catálogo), pero el resultado sale del `id` aunque los parámetros sean de otra búsqueda. | Búsqueda | `service/ClustersService.java` · `availability`, `availabilityFromDatabase` | Bajo | Caracterización | Baja |
| 32 | **Hipótesis: los infantes no tienen tope.** No cuentan en el máximo de 9 y no se exige que haya un adulto por infante. | Búsqueda | `validator/AvailabilityRequestValidator.java` · `validatePassengerCount` | Bajo | Caracterización | Baja |

## Qué dificulta testear (transversal)

1. **No hay tests ni infraestructura de tests.** No existe `src/test`. El `pom.xml` solo trae
   `spring-boot-starter-test`, sin Testcontainers ni WireMock.
2. **Las migraciones son de MySQL** (backticks, `MODIFY ... AUTO_INCREMENT`, `SET FOREIGN_KEY_CHECKS`). Lo más
   probable es que no corran en H2. Los tests de integración necesitan MySQL real: Testcontainers o el contenedor de
   `make start-infra`.
3. **Los proveedores son aleatorios.** Microcks genera UUID, carriers, hubs, horarios y precios con `randomValue` y
   `randomInt` (`microcks/generate-contracts.py`). Por eso el smoke test solo valida estados y formas. Para caracterizar
   valores exactos hay que reemplazar los connectors por stubs con fixtures fijas.
4. **Dependencia del reloj.** `LocalDate.now()` (validación de fechas pasadas), `LocalDateTime.now()` (id de la
   búsqueda, TTL, job) y las fechas de las políticas semilla. Ejemplo: "Special Carrier" vence el 2027-05-01. A partir
   de esa fecha, las búsquedas sobre ese rango cambian de política. No hay un `Clock` inyectable.
5. **El id de la búsqueda no es determinístico:** incluye el timestamp de generación (`FlightIdGeneratorHelper`).
   Además tiene comas, `=` y `:`, así que hay que codificarlo en la URL de `GET /clusters/{id}/...`.
6. **Concurrencia oculta.** Hay dos `Executors.newFixedThreadPool(10)` creados a mano (`ItinerariesSearchService` y
   `ClustersRepositoryImpl`) que Spring no administra. El guardado asíncrono obliga a esperar o reintentar en los
   tests.
7. **Errores tragados.** Varios errores se convierten en "menos resultados" sin que el usuario se entere: un proveedor
   caído, una oferta mal formada, una duración inválida, un itinerario sin política. Solo se detectan contando
   itinerarios o leyendo logs, así que hace falta capturar logs (por ejemplo con `OutputCaptureExtension`).
8. **Acoplamiento entre módulos por la base:** el cálculo de duración (Aerolíneas) lee `city` (Catálogo) por su cuenta,
   con `CityRepository` en `FlightHelper`. Para testear un mapper de proveedor completo hace falta el catálogo cargado.
9. **Zonas horarias y horario de verano:** las duraciones dependen de la fecha del vuelo y de la base de zonas de
   Joda-Time.

## Hallazgos raros (se documentan, no se corrigen)

Por la regla de legacy de `CLAUDE.md`, estos puntos se caracterizan tal como están:

- **El markup divide por 10, no por 100** (#1). Un 5 % de política cobra un 50 % del subtotal. La administración
  acepta porcentajes de 0 a 100 (`CommercialPolicyValidator`), así que una política del 100 % multiplicaría el subtotal
  por 11.
- **La vigencia de la política se compara contra la llegada del *primer* tramo del último segmento** (#2), no contra
  la del último tramo.
- **Gana la prioridad más alta en número** (#2). Con los datos semilla, "Special Carrier" y "Ciber Week" nunca se
  aplican a carriers que también están en "General". Hay que confirmar con negocio si esa es la intención.
- **El 40015 se devuelve con HTTP 502** (#14), aunque el código sugiere un 400 y el caso es "no encontrado".
- **`amount` se aplica por proveedor** (#16), así que `total` puede ser hasta tres veces lo pedido.
- **No hay deduplicación entre proveedores** (#18). El mismo vuelo puede aparecer una vez por cada proveedor.
- **Prompt contra realidad:** el pedido dice que los módulos "ya están modularizados", pero el código sigue organizado
  por capa técnica. Además, `docs/README.md` ubica esta clase como "C5" y en la carpeta `clase-5-...`, mientras que los
  prompts y los ejemplos están en `clase-4-...`. Este documento quedó junto a los ejemplos, en `clase-4-...`.

## Orden sugerido para el paso 2

1. **Precios** (#1 a #5, #9, #25): es dinero y tiene la regla más sorprendente. Conviene empezar por
   `PricingService` con políticas en MySQL real.
2. **Aerolíneas** (#10 a #13, #15, #21 a #24): mappers con fixtures fijas por proveedor. Es lo que más cambia cuando
   el código se mueve a una capa anticorrupción (ACL).
3. **Búsqueda** (#6 a #8, #14, #16 a #20): contrato del endpoint con proveedores stubeados, y paginación con la
   espera del guardado asíncrono.
4. **Catálogo** (#26): es chico, pero es el primero que se extrae (clase 6). Conviene fijar el contrato de `getCity`
   y los datos semilla antes de moverlo.

Antes de escribir tests, conviene **revisar las hipótesis #10, #27 y #30 a #32 con alguien de negocio**, y validar si
los hallazgos raros son reglas buscadas o errores que el negocio ya conoce.
