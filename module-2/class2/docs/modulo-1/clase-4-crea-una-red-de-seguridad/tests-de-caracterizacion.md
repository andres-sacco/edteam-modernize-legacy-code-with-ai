# Red de tests de caracterización

**Clase:** M1 · C4 · Crea una red de seguridad (paso 2, ejecutar)
**Entrada:** [comportamientos-a-proteger.md](comportamientos-a-proteger.md) (los números `#n` de este documento son los
de esa lista) y las respuestas grabadas de Microcks en `src/test/resources/providers/`.
**Resultado:** 171 tests JUnit 5 en `src/test`, todos en verde contra el código actual. No se modificó código de
producción.

## Cómo correrlos

```bash
mvn -B test
```

- Necesitan **Docker** (Testcontainers levanta un `mysql:8.0` propio). No usan el stack de `docker compose` ni Microcks,
  así que se pueden correr con el stack apagado.
- Tardan unos 20 segundos. Uno de ellos espera el timeout real de 5 segundos de un proveedor.

## Infraestructura de test

| Pieza | Para qué | Dónde |
|-------|----------|-------|
| Testcontainers MySQL 8.0 (`testcontainers-mysql` 2.0.5) | Base real con las migraciones de Flyway y los datos semilla. Las migraciones son de MySQL y no corren en H2. Se usa la línea 2.x porque la 1.19 de Spring Boot 3.2 no habla con Docker 29. | `support/AbstractIntegrationTest` |
| WireMock 3.13 (`wiremock-standalone`) | Stub de **lo externo** únicamente: los tres proveedores (aerolíneas). Responde las respuestas grabadas, errores HTTP, demoras y cuerpos inválidos. | `support/ProviderStubs` |
| Respuestas grabadas | Ida y vuelta BUE-MIA de Alpha, Beta y Gamma (1 adulto, 1 niño, 1 infante) con fechas fijas (2027-01-14 / 2027-01-24). | `src/test/resources/providers/` |
| Foto del catálogo | Las 86 ciudades semilla con nombre, zona horaria, país y continente. | `src/test/resources/catalog/cities.csv` |

Las dependencias nuevas están en el `pom.xml` con `<scope>test</scope>`.

**Cómo se fijan fechas y datos.** El validador usa `LocalDate.now()` sin `Clock`, así que las fechas de los *pedidos*
son relativas a hoy (hoy + 60 y + 70 días). Todo lo demás es fijo: las respuestas grabadas (enero de 2027), los
itinerarios armados a mano (`support/ItineraryTestData`) y las políticas. Las políticas que crean los tests usan
carriers que no existen en los datos semilla (`ZZ`, `ZY`, `ZX`, `ZW`) y se borran al terminar cada test. El único
test que toca una política semilla (`ClustersPricingTest`, #4) la restaura en un `@AfterEach`.

**Un único contexto de Spring.** Todos los tests de integración heredan de `AbstractIntegrationTest`, así que el
contexto y los contenedores se levantan una sola vez.

## Casos cubiertos

Orden por riesgo, igual que el commit de cada módulo. Las clases están en `src/test/java/com/twa/flights/`.

### 1. Precios (commit `test: add the characterization tests of the pricing`)

| # | Caso | Test |
|---|------|------|
| 1 | `markup = subtotal × % / 10`: AR 5 % sobre 730.22 da 365.1100 y total 1095.3300 | `service/PricingServiceTest.markupDividesThePercentageByTen` |
| 1 | Carrier de Gamma (G3 2 %) sale de la política General: 423.7100 | `PricingServiceTest.markupOfGammaCarrier` |
| 1 | Markup por cada tipo de pasajero; los tipos ausentes quedan `null`; base, tax y subtotal no cambian | `PricingServiceTest.markupForEachPassengerType` |
| 1 | Política de 0 % conserva el itinerario con markup cero (AF) | `PricingServiceTest.zeroPercentageKeepsTheItinerary` |
| 1 | Política de 100 % multiplica el subtotal por 11 | `PricingServiceTest.oneHundredPercentMultipliesByEleven` |
| 2 | Gana la prioridad más alta con los datos semilla (AR 5, AA 2, BA 3, LH 8) | `PricingServiceTest.highestPriorityWinsWithSeedPolicies` |
| 2 | Gana la prioridad más alta con políticas nuevas | `PricingServiceTest.highestPriorityWinsWithNewPolicies` |
| 2 | Una política inactiva se ignora aunque tenga más prioridad | `PricingServiceTest.inactivePolicyIsIgnored` |
| 2 | `startDate` contra la salida del primer tramo, inclusivo (08:00 aplica, 08:01 no) | `PricingServiceTest.startDateIsInclusiveWithTheFirstDeparture` |
| 2 | `expireDate` contra la llegada del **primer** tramo del último segmento, inclusivo | `PricingServiceTest.expireDateUsesTheFirstLegOfTheLastSegment` |
| 3 | Itinerario sin política (LA, FY) se descarta en silencio | `PricingServiceTest.itineraryWithoutPolicyIsDiscarded` |
| 3 | Fuera de la vigencia de las semillas (2030) no hay política: se descarta | `PricingServiceTest.itineraryOutsideTheSeedPoliciesIsDiscarded` |
| 1, 9 | La búsqueda devuelve markup y totales por pasajero con la escala de `BigDecimal` (texto exacto del JSON: `580.9350`, `5.00`) | `controller/ClustersPricingTest.pricesOfTheSearchAreNotRounded` |
| 3 | La búsqueda con las respuestas grabadas devuelve 12 de 15 itinerarios: sin LA ni FY | `ClustersPricingTest.itinerariesWithoutPolicyAreNotReturned` |
| 4 | Un cambio de política se ve en la búsqueda siguiente; la búsqueda guardada conserva el precio viejo | `ClustersPricingTest.policyChangesAffectOnlyTheNewSearches` |
| 5 | La búsqueda es pública, no pide `X-Api-Key` | `ClustersPricingTest.searchDoesNotNeedTheApiKey` |
| 5, 7 | Sin `X-Api-Key`: 401 `{code: 40101, shortDescription, reasons: null}` | `controller/CommercialPolicyControllerTest.withoutApiKeyResponds401` |
| 5 | Con clave inválida: 401 también en las escrituras, y no se borra nada | `CommercialPolicyControllerTest.withInvalidApiKeyResponds401` |
| 28 | Listado por prioridad descendente, filtros `active` y `carrier` (en minúsculas) | `CommercialPolicyControllerTest.listOfPolicies` |
| 28 | Con igual prioridad, desempata por id | `CommercialPolicyControllerTest.samePriorityIsSortedById` |
| 25 | Política semilla General: 15 carriers de V1.3 y V1.6 con sus porcentajes | `CommercialPolicyControllerTest.seedPolicy` |
| 25 | `POST`: 201, `Location`, activa por defecto, nombre recortado y carrier en mayúsculas | `CommercialPolicyControllerTest.createPolicy` |
| 25 | `PUT` reemplaza todo, conserva el id de los carriers que siguen y sin `active` la activa | `CommercialPolicyControllerTest.updatePolicyReplacesEverything` |
| 25 | `PATCH /status` cambia solo el estado | `CommercialPolicyControllerTest.changeStatus` |
| 25, 7 | `DELETE` 204; después 404 `40403` y sin carriers huérfanos | `CommercialPolicyControllerTest.deletePolicy` |
| 25 | `PUT /carriers/{c}` alta o modificación | `CommercialPolicyControllerTest.saveCarrier` |
| 25 | `DELETE /carriers/{c}`: 404 `40404` si no existe, 400 `40026` si es el último | `CommercialPolicyControllerTest.deleteCarrier` |
| 25, 7 | Política inexistente: 404 `40403` | `CommercialPolicyControllerTest.unknownPolicy` |
| 25, 7 | 12 validaciones del alta (40023 a 40028) con `reasons: null` | `CommercialPolicyControllerTest.invalidPolicy` (parametrizado) |
| 25 | Estado obligatorio (40029) y carrier/porcentaje del path (40027, 40028) | `CommercialPolicyControllerTest.invalidStatusAndCarrier` |

### 2. Aerolíneas (commit `test: add the characterization tests of the providers (airlines)`)

| # | Caso | Test |
|---|------|------|
| 21 | `subtotal = (base + tax) × cantidad`, `total = subtotal`, sin markup | `mapper/provider/ProviderMapperHelperTest.priceByPassengerType` |
| 21 | Niños e infantes solo con cantidad > 0 (0 o `null` → `null`) | `ProviderMapperHelperTest.childrenAndInfantsOnlyWithQuantity` |
| 21 | Sin la tarifa de un tipo pedido, el itinerario no se arma | `ProviderMapperHelperTest.missingFareOfRequestedPassenger` |
| 22 | Número de vuelo solo dígitos (`AR01234` → 1234), hora cortada a `HH:mm` | `ProviderMapperHelperTest.legWithNumberAndTime` |
| 22 | Número de vuelo sin dígitos: `NumberFormatException` | `ProviderMapperHelperTest.flightNumberWithoutDigits` |
| 22 | Sin piezas de equipaje el peso es `null` | `ProviderMapperHelperTest.allowanceWithoutPieces` |
| 22 | `ROUND_TRIP` si hay más de un destino, también en multidestino | `ProviderMapperHelperTest.flightType` |
| 11 | Alpha: cada oferta es un itinerario y cada `journey` un segmento | `AlphaItineraryMapperTest.recordedResponse` |
| 11, 21 | Alpha: tarifas por `ADT`/`CHD`/`INF` | `AlphaItineraryMapperTest.faresByPassengerType` |
| 11, 22 | Alpha: equipaje de `luggage.cabin`/`hold` | `AlphaItineraryMapperTest.baggage` |
| 11 | Alpha: los vuelos de un `journey` son los tramos, en orden | `AlphaItineraryMapperTest.connections` |
| 11 | Alpha: cabina `Y`/`W`/`C` | `AlphaItineraryMapperTest.cabinWithBookingClasses` |
| 11, 24 | Alpha: cabina desconocida descarta solo esa oferta con WARN | `AlphaItineraryMapperTest.unknownCabinDiscardsTheOffer` |
| 21, 24 | Alpha: sin tarifa `CHD` se descarta, salvo que no se pidan niños | `AlphaItineraryMapperTest.missingFareDiscardsTheOffer` |
| 24 | Alpha: respuesta vacía o sin `results` → lista vacía | `AlphaItineraryMapperTest.emptyResponses` |
| 12 | Beta: cada opción es un itinerario y cada `bound` un segmento | `BetaItineraryMapperTest.recordedResponse` |
| 12 | Beta: precios en centavos ÷ 100 con dos decimales | `BetaItineraryMapperTest.pricesInCents` |
| 12, 22 | Beta: equipaje `hand`/`checked` | `BetaItineraryMapperTest.baggage` |
| 12 | Beta: `OUTBOUND` siempre primero | `BetaItineraryMapperTest.outboundIsTheFirstSegment` |
| 12 | Beta: cabina por nombre del enum en cualquier caso; desconocida descarta | `BetaItineraryMapperTest.cabin` |
| 12 | Beta: `status` distinto de `OK` → lista vacía con WARN | `BetaItineraryMapperTest.statusDifferentFromOk` |
| 12 | Beta: respuesta vacía, sin `data` o sin opciones → lista vacía | `BetaItineraryMapperTest.emptyResponses` |
| 13 | Gamma: cada itinerario es un itinerario, las fechas pierden los segundos | `GammaItineraryMapperTest.recordedResponse` |
| 13 | Gamma: agrupa por `SegmentIndex` y ordena por `Sequence` aunque vengan desordenados | `GammaItineraryMapperTest.flightsAreGroupedBySegmentIndex` |
| 13 | Gamma: equipaje `nPC[mKG]` (`1PC23KG`, `0PC`, `2PC`) | `GammaItineraryMapperTest.baggageAsText` |
| 13, 24 | Gamma: formato de equipaje desconocido (`23KG`, minúsculas) descarta con WARN | `GammaItineraryMapperTest.unknownBaggageFormat` |
| 13 | Gamma: cabina `ECO`/`PEY`/`BUS`; desconocida descarta | `GammaItineraryMapperTest.cabin` |
| 24 | Gamma: respuesta vacía → lista vacía | `GammaItineraryMapperTest.emptyResponses` |
| 15 | Duración con zonas horarias: BUE 04:45 → MIA 23:45 = `21:00` | `helper/FlightHelperTest.durationWithTimeZones` |
| 15 | Sin zonas horarias compara horas locales | `FlightHelperTest.durationWithoutTimeZones` |
| 15 | Formato `HH:mm`, y más de dos dígitos desde 100 horas (`100:30`) | `FlightHelperTest.format` |
| 15 | El horario de verano de la fecha cambia la duración (Nueva York, 2027-03-14) | `FlightHelperTest.daylightSavingTime` |
| 15 | Llegada anterior a la salida: `TWAException` | `FlightHelperTest.arrivalBeforeDeparture` |
| 23 | Ida y vuelta: primer origen/destino, `returnDate` = segunda fecha, `amount`, cabina en mayúsculas (URL exacta a los tres) | `connector/ProviderConnectorsTest.requestOfRoundTrip` |
| 23 | Solo ida: sin `returnDate` ni `amount`, cabina `ECONOMY` | `ProviderConnectorsTest.oneWayRequest` |
| 23 | Multidestino: solo la primera ruta; el tercer tramo se ignora | `ProviderConnectorsTest.multipleDestinationsRequest` |
| 23 | Sin `children`/`infants` el parámetro va sin valor | `ProviderConnectorsTest.requestWithoutChildrenAndInfants` |
| 16 | Cada connector recorta a `amount`; sin `amount` devuelve todo | `ProviderConnectorsTest.amountLimitsEachProvider` |
| 10 | Un error HTTP del proveedor es una `TWAException` en el connector | `ProviderConnectorsTest.errorOfTheProvider` |
| 10 | Proveedor con error 500: la búsqueda sigue con los otros, ERROR en el log | `service/ProvidersSearchTest.providerWithError` |
| 10 | Proveedor que tarda 6 s: corta a los 5 s y sigue sin él | `ProvidersSearchTest.providerWithTimeout` |
| 10 | Proveedor con un cuerpo que no es JSON: sigue sin él | `ProvidersSearchTest.providerWithInvalidBody` |
| 10 | Fallan los tres: 200, `itineraries: []`, `pagination {0,0,0}` y la búsqueda vacía se guarda igual | `ProvidersSearchTest.allProvidersFail` |
| 15 | Duración por tramo y por segmento con las ciudades del catálogo (incluye la conexión) | `ProvidersSearchTest.durationOfTheFlights` |
| 15 | Llegada anterior a la salida descarta el itinerario y loguea ERROR (caso real de Gamma) | `ProvidersSearchTest.arrivalBeforeDepartureDiscardsTheItinerary` |
| 15 | Ciudad fuera del catálogo descarta el itinerario y loguea ERROR | `ProvidersSearchTest.cityOutsideTheCatalogDiscardsTheItinerary` |
| 24 | Oferta mal formada se descarta con WARN, la búsqueda sigue | `ProvidersSearchTest.malformedOfferIsDiscarded` |

### 3. Búsqueda (commit `test: add the characterization tests of the search`)

| # | Caso | Test |
|---|------|------|
| 6, 7 | 27 errores de validación, cada uno con su código (40001 a 40022) y `{code, shortDescription, reasons: null}` | `controller/ClustersValidationTest.invalidRequest` (parametrizado) |
| 6 | Gana el primer error: fechas → pasajeros → origen/destino → orden → cabina → filtros (6 combinaciones) | `ClustersValidationTest.firstErrorWins` (parametrizado) |
| 6 | Los enums aceptan minúsculas y espacios | `ClustersValidationTest.enumsInAnyCase` |
| 6 | Una búsqueda para hoy es válida | `ClustersValidationTest.searchForToday` |
| 26 | Los códigos de ciudad se aceptan en minúsculas | `ClustersValidationTest.cityCodeInLowerCase` |
| 32 | Los infantes no tienen tope ni necesitan un adulto cada uno | `ClustersValidationTest.infantsWithoutLimit` |
| 30 | Sin `children` o sin `infants`: 500 | `ClustersValidationTest.withoutChildrenOrInfants` |
| 30 | Más orígenes que destinos: 500 con el error por defecto de Spring Boot, no `ErrorDTO` | `ClustersValidationTest.moreOriginsThanDestinations` |
| 30 | Más destinos que orígenes: solo se validan las rutas de los orígenes (200) | `ClustersValidationTest.moreDestinationsThanOrigins` |
| 6 | Un parámetro con tipo incorrecto: 400 de Spring, sin `ErrorDTO` | `ClustersValidationTest.parameterWithWrongType` |
| 8 | Forma de la respuesta y nombres en lowerCamelCase en todos los niveles | `ClustersSearchTest.contractOfTheResponse` |
| 8 | Sin niños ni infantes, sus precios salen `null` | `ClustersSearchTest.pricesOfPassengersThatAreNotRequested` |
| 14 | El id tiene el timestamp y los parámetros del pedido | `ClustersSearchTest.idOfTheSearch`, `helper/FlightIdGeneratorHelperTest` |
| 16 | Sin `amount`: una sola página, `limit = total = 12` | `ClustersSearchTest.withoutAmount` |
| 16 | `amount=2` se pide a cada proveedor: `total` 5, no 2 | `ClustersSearchTest.amountByProvider` |
| 17, 20 | `offset` es el número de página; las páginas mantienen el orden y no se vuelve a llamar a los proveedores | `ClustersSearchTest.pagination` |
| 17, 19 | Cada página vuelve a filtrar y ordenar; `total` es después de filtrar | `ClustersSearchTest.filtersInThePagination` |
| 31 | Con `id` se ignoran los demás parámetros (otra ruta devuelve la búsqueda guardada) | `ClustersSearchTest.paginationIgnoresTheParameters` |
| 30 | Con `id` y sin `offset`: 500 | `ClustersSearchTest.paginationWithoutOffset` |
| 14, 7 | Paginar un id inexistente: **502** `{code: 40015, shortDescription: null, reasons: null}` | `ClustersSearchTest.paginationOfUnknownSearch` |
| 14, 29 | Una búsqueda vencida no se pagina (502, 404) y el job borra solo las vencidas | `ClustersSearchTest.expiredSearch` |
| 14, 27 | Se guarda en snake_case con TTL de 5 minutos | `ClustersSearchTest.savedSearch` |
| 14 | `GET /clusters/{id}/itineraries/{itineraryId}` devuelve el mismo itinerario (id codificado en el path) | `ClustersSearchTest.itineraryOfTheSearch` |
| 14, 7 | Búsqueda inexistente 404 `40401`, itinerario inexistente 404 `40402` | `ClustersSearchTest.unknownSearchOrItinerary` |
| 19 | Sin filtros no cambia nada | `helper/ItineraryFilterHelperTest.withoutFilters` |
| 19 | `maxStops` toma el segmento con más escalas | `ItineraryFilterHelperTest.maxStops` |
| 19 | `minPrice`/`maxPrice` inclusivos sobre el total de todos los pasajeros | `ItineraryFilterHelperTest.priceRange` |
| 19 | El filtro de precio usa el total con markup | `ItineraryFilterHelperTest.priceWithMarkup` |
| 19 | `carriers` y `excludedCarriers` son listas, en cualquier caso y con espacios | `ItineraryFilterHelperTest.carriers` |
| 19 | `departureTime` por la hora del primer tramo de ida, inicio inclusivo y fin exclusivo | `ItineraryFilterHelperTest.departureTime` |
| 19 | `checkedBaggage` mira `checked.pieces > 0`; sin equipaje es `false` | `ItineraryFilterHelperTest.checkedBaggage` |
| 19 | `splitCodes` normaliza e ignora vacíos | `ItineraryFilterHelperTest.splitCodes` |
| 20 | Por defecto `PRICE ASC` | `helper/ItinerarySortHelperTest.defaultOrder` |
| 20 | `CARRIER` desempata por precio **ascendente** también con `DESC` | `ItinerarySortHelperTest.carrierWithPriceAsTiebreaker` |
| 20 | `CARRIER` pone los `null` al final con `ASC` y al principio con `DESC` | `ItinerarySortHelperTest.nullCarriers` |
| 20 | `DURATION` suma todos los segmentos | `ItinerarySortHelperTest.durationOfAllTheSegments` |
| 20 | Con el mismo precio se mantiene el orden de entrada | `ItinerarySortHelperTest.samePriceKeepsTheOrder` |
| 18 | Formato exacto de la clave del itinerario | `helper/FlightDuplicationCheckHelperTest.keyOfTheItinerary` |
| 18 | Entre duplicados del mismo proveedor queda el más barato | `FlightDuplicationCheckHelperTest.cheapestDuplicateIsKept` |
| 18 | Con el mismo precio queda el primero | `FlightDuplicationCheckHelperTest.samePriceKeepsTheFirst` |
| 18 | No hay deduplicación entre proveedores | `FlightDuplicationCheckHelperTest.duplicatesBetweenProvidersAreKept` |
| 18 | Cabina, equipaje y hora de llegada no son parte de la clave | `FlightDuplicationCheckHelperTest.attributesOutsideTheKey` |
| 27 | El payload va en snake_case, con los `null` y la escala de los decimales | `serializer/JsonSerializerTest.payloadInSnakeCase` |
| 27 | Ida y vuelta de la serialización reconstruye la misma búsqueda | `JsonSerializerTest.roundTripOfTheSerialization` |
| 27 | Un payload inválido se lee como `null`, sin excepción | `JsonSerializerTest.invalidPayload` |

### 4. Catálogo (commit `test: add the characterization tests of the catalog`)

| # | Caso | Test |
|---|------|------|
| 26 | `getCity` devuelve ciudad, zona horaria, país y continente | `service/CatalogServiceTest.cityWithCountryAndContinent` |
| 26 | Código desconocido o vacío → `null` (el validador lo convierte en 40014) | `CatalogServiceTest.unknownCity` |
| 26 | El código se encuentra en minúsculas (collation de MySQL) | `CatalogServiceTest.codeInLowerCase` |
| 26 | Las 86 ciudades semilla coinciden con la foto `catalog/cities.csv` | `CatalogServiceTest.seedCities` |
| 15, 26 | Todas las zonas horarias del catálogo son válidas para Joda-Time | `CatalogServiceTest.timeZonesAreValid` |

## Cobertura de la lista de comportamientos

Los 32 comportamientos tienen al menos un test. Lo que queda **sin cubrir a propósito**:

- **#2, hipótesis de dos políticas con la misma prioridad:** el orden no está definido (depende de MySQL), un test
  sería inestable. Hay que decidir con negocio cuál debería ganar.
- **#18 y #20, orden previo al sort:** sale de un `HashMap`. Solo se fija que, con el mismo precio, el sort es estable.
- **#27, hipótesis de `serialize` que falla:** no se puede provocar sin tocar producción.
- **#14 y #29, el reloj real:** no se esperan 5 minutos ni 60 segundos. El vencimiento se simula con una fila con
  `expire_at` en el pasado y el job se llama directo.

## Hallazgos al correr los tests (se documentan, no se corrigen)

1. **Un itinerario real de Gamma se descarta por la duración, no por pricing.** El LA `df3a40aa` vuela MIA 07:15 →
   SAO 09:00, que con las zonas horarias "llega antes de salir" (12:15 UTC → 12:00 UTC). Los datos aleatorios de
   Microcks disparan el #15 en la búsqueda normal.
2. **Si fallan los tres proveedores la respuesta es 200 vacía y la búsqueda se guarda** (`limit: 0`). Se confirma la
   hipótesis del #10.
3. **Se confirman las hipótesis #30, #31 y #32:** 500 sin `children`, `infants` u `offset` (con `id`) y con más
   orígenes que destinos; al paginar se ignoran los parámetros; los infantes no tienen tope.
4. **El 502 `40015` sale con `shortDescription: null`.** El `APIException` se crea con el constructor que no la
   completa. Los demás errores sí tienen descripción.
5. **`PUT` de una política sin `active` la reactiva**, aunque estuviera inactiva.
6. **La respuesta del `POST` muestra el porcentaje tal como llegó (`2.5`)** y el `GET` lo muestra como lo guarda la
   base (`2.50`).
7. **Los carriers de una política salen en orden alfabético.** No hay `ORDER BY`: es un efecto del índice único de
   `V1.7`. Si la migración cambia la base o el índice, el orden puede cambiar.
8. **Los códigos de ciudad se aceptan en minúsculas** por la collation de MySQL (`bue` es válido). Otra base con
   collation sensible a mayúsculas lo rompería.
9. **`CARRIER DESC` pone los carriers `null` primero** (el `nullsLast` se invierte con el `reversed()`).
10. **Un parámetro con tipo incorrecto (`adults=one`) responde el 400 de Spring**, sin el `ErrorDTO` de la aplicación.
11. **La duración pasa de dos dígitos desde las 100 horas** (`100:30`), el formato no es estrictamente `HH:mm`.

## Límites de la red

- **Vigencia de los datos semilla:** los itinerarios fijos son de 2027 y la política General vence el 2030-01-01. Si
  una migración cambia las políticas semilla, los tests de Precios fallan a propósito.
- **No cubre** el arranque del stack con Docker Compose, Microcks ni Swagger: eso lo sigue verificando
  `./scripts/smoke-test.sh`.
- **No hay tests de rendimiento ni de concurrencia** de los dos pools de 10 hilos.
