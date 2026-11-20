# Glosario por contexto

Lenguaje ubicuo de cada bounded context del [context map](context-map.md). Parte del
[glosario de la clase 2](../clase-2-conoce-tu-monolito/glosario.md) y reparte cada término en el contexto donde tiene
significado. Un mismo término puede aparecer en dos contextos con **significados distintos**: eso es esperable y está
marcado en la sección 5.

Columnas: **Término** (cómo se dice en el negocio) · **En el código** (cómo se llama hoy) · **Definición** dentro de ese
contexto · **Notas** (ambigüedades y reglas de la [lista de reglas ocultas](../clase-2-conoce-tu-monolito/reglas-ocultas.md)).

---

## 1. BC1 · Búsqueda de vuelos

| Término | En el código | Definición | Notas |
|---------|--------------|------------|-------|
| **Búsqueda** (*availability*) | `ClustersService.availability`, `AvailabilityRequestDTO` | Pedido de vuelos para una ruta, fechas, pasajeros y cabina. Devuelve itinerarios con precio final | En BC2 "availability" es otra cosa (ver sección 5) |
| **Búsqueda guardada** (*cluster*) | `ClusterSearch`, `ClusterSearchDTO`, tabla `cluster_search` | Resultado completo de una búsqueda, con todos los itinerarios ya cotizados, guardado para paginar y para consultar el detalle | "Cluster" no es un agrupamiento de vuelos. Nombre a revisar al separar |
| **Id de búsqueda** | `ClusterSearchDTO.id`, `FlightIdGeneratorHelper` | Texto que codifica el pedido: `TWA_g=<fecha>_f=<from>_t=<to>_d=<departure>_...` | No es un UUID (R17) |
| **TTL** | `ClustersRepositoryImpl.TOKEN_TTL` | Tiempo de vida de la búsqueda guardada: 5 minutos | Fijo en el código (R16) |
| **Ruta** | `from`, `to`, `departure` | Par origen-destino con su fecha | Solo la primera ruta llega a los proveedores (R10) |
| **Pasajeros** | `adults`, `children`, `infants` | Cantidad por tipo de pasajero del pedido | Máximo 9 entre adultos y niños (R11) |
| **Página / offset** | `PaginationDTO`, `offset` | `offset` es el **número de página** desde 0 | No es un desplazamiento en elementos (R14) |
| **Amount** | `AvailabilityRequestDTO.amount` | Tamaño de página **y** tope de itinerarios por proveedor | Un parámetro, dos significados (R14) |
| **Filtro** | `ItineraryFilterHelper` | Restricción sobre la búsqueda guardada: escalas, precio, carriers, franja horaria, equipaje | Se aplica en cada página (R15) |
| **Orden** | `SortCriteria`, `SortOrder` | `PRICE`, `CARRIER` o `DURATION`, `ASC` o `DESC`. Desempate por precio | Por defecto `PRICE ASC` |
| **Escalas** | `maxStops` | Tramos − 1 del segmento con más conexiones | |
| **Franja horaria de salida** | `DepartureTimeRange` | `NIGHT`, `MORNING`, `AFTERNOON`, `EVENING` sobre la salida del primer tramo de la ida | Hora local del aeropuerto |
| **Deduplicación** | `FlightDuplicationCheckHelper`, `ItineraryKeyGeneratorHelper` | Quitar itinerarios repetidos de un mismo proveedor, quedándose con el más barato | La clave incluye el proveedor; se hace antes del markup (R07) |
| **Itinerario** | `ItineraryDTO` | Opción de viaje vendible que se muestra al cliente: segmentos, precio final, carrier, cabina, equipaje | BC1 es el dueño del modelo publicado (ver [entidades compartidas](entidades-compartidas.md)) |
| **Precio final / total** | `PriceInfoDTO.getTotalAmount()` | Suma de los totales por tipo de pasajero, ya con markup | Es el valor que se filtra y ordena |

## 2. BC2 · Integración con proveedores

| Término | En el código | Definición | Notas |
|---------|--------------|------------|-------|
| **Proveedor** | `Provider` (`ALPHA`, `BETA`, `GAMMA`), `ProviderFacade` | Sistema externo que devuelve disponibilidad con su propio formato | Simulados en Microcks |
| **Disponibilidad del proveedor** | `Provider*Service`, `*Connector` | Llamada HTTP a un proveedor para una ruta | Un proveedor que falla no rompe la búsqueda (R09) |
| **Oferta** (*offer / flight option / itinerary*) | records de `dto/provider/*` | Opción de viaje en el formato del proveedor, antes de traducir | Alpha `Offer`, Beta `FlightOption`, Gamma `Itinerary`. Su id pasa a ser el id del itinerario (R18) |
| **Sentido** (*journey / bound*) | records de `dto/provider/*` → `SegmentDTO` | Ida o vuelta según el proveedor | Alpha `Journey`, Beta `Bound`, Gamma `SegmentIndex` |
| **Vuelo** (*flight / leg*) | records → `LegDTO` | Vuelo puntual entre dos aeropuertos | Alpha y Gamma `Flight`, Beta `Leg` |
| **Tarifa** (*fare*) | `ProviderMapperHelper.Fare(base, tax)` | Precio unitario por pasajero que manda el proveedor | Beta la manda en centavos |
| **Duración** | `FlightHelper` | Tiempo entre salida y llegada (`HH:mm`) usando la zona horaria de cada ciudad | Si no se puede calcular, el itinerario se descarta (R08). Hoy lee `CityRepository` directo |
| **Traducción** (*mapping*) | `mapper/provider/*ItineraryMapper` | Conversión del formato del proveedor al modelo interno | Es la capa anticorrupción. Particularidades por proveedor en R19 |

## 3. BC3 · Pricing

| Término | En el código | Definición | Notas |
|---------|--------------|------------|-------|
| **Cotización** (*pricing*) | `PricingService.priceItineraries` | Elegir la política vigente para el carrier y calcular el markup por tipo de pasajero | Hoy muta el `ItineraryDTO`. Itinerario sin política = no se vende (R02) |
| **Política comercial** | `CommercialPolicy`, tabla `commercial_policy` | Conjunto de porcentajes de markup por carrier, con nombre, prioridad, estado y vigencia | Agregado raíz del contexto |
| **Carrier de la política** | `CommercialPolicyCarrier`, tabla `commercial_policy_carrier` | Porcentaje de markup de un carrier dentro de una política | Un carrier por política. Columna de 3 caracteres, validador de 2 (R20) |
| **Porcentaje** | `CommercialPolicyCarrier.percentage` | Valor entre 0 y 100 que define el markup | Se divide por 10: un 5 es un 50 % (R01) |
| **Markup** | `MarkupDTO` (`percentage`, `amount`) | Recargo que se suma al subtotal | Se aplica sobre base + impuestos, igual para todos los pasajeros (R05) |
| **Subtotal** | `PaxPriceDTO.subtotal` | `(base + tax) × cantidad` de un tipo de pasajero | Es la base del markup |
| **Prioridad** | `CommercialPolicy.priority` | Desempate entre políticas que aplican al mismo carrier | Gana el número **más alto** (R03) |
| **Vigencia** | `startDate`, `expireDate` | Rango en el que la política aplica | Se compara con las fechas del **vuelo**, no de la compra (R04) |
| **Activa** | `CommercialPolicy.active` | Una política inactiva nunca se aplica | |
| **API key de pricing** | `X-Api-Key`, `PricingApiKeyInterceptor` | Clave compartida que protege la administración (`/pricing/**`) | Separa la audiencia admin de la búsqueda pública dentro del mismo contexto |

## 4. BC4 · Catálogo geográfico

| Término | En el código | Definición | Notas |
|---------|--------------|------------|-------|
| **Ciudad** | `City`, tabla `city` | Código, nombre, zona horaria y país | En la práctica son códigos IATA de ciudad o aeropuerto (`BUE`, `MIA`). Hasta 3 caracteres en la API, 4 en la tabla |
| **País** | `Country`, tabla `country` | Código, nombre y continente | |
| **Continente** | `Continent`, tabla `continent` | Código y nombre | |
| **Zona horaria** | `City.timeZone` | Id IANA (`America/Argentina/Buenos_Aires`) | La consume BC2 para la duración |
| **Información de catálogo incompleta** | `INCOMPLETE_CATALOG_INFORMATION` (400_14) | La ciudad no existe o le falta país o continente | Hoy el código lo devuelve BC1 al validar (R12) |

---

## 5. Mismo término, distinto significado según el contexto

| Término | BC1 Búsqueda | BC2 Proveedores | BC3 Pricing | BC4 Catálogo |
|---------|--------------|-----------------|-------------|--------------|
| **Availability** | La búsqueda pública | La llamada a un proveedor | — | — |
| **Itinerario** | Opción vendible con precio final, lo que ve el cliente | Oferta del proveedor traducida, sin markup | Unidad a cotizar: solo importan carrier, fechas y precios por pasajero | — |
| **Carrier** | Filtro y criterio de orden | Dato que se lee del formato del proveedor | Clave de la política. Además, "carrier" nombra la **fila** de la política | — |
| **Precio** | Total final, se filtra y ordena | Tarifa del proveedor (base e impuestos) | Subtotal sobre el que se calcula el markup | — |
| **Ciudad** | Código a validar en el pedido | Código para obtener la zona horaria | — | Entidad con país, continente y zona horaria |
| **Id del itinerario** | Clave para el detalle dentro de la búsqueda guardada | Id que manda el proveedor | Clave para cruzar el precio calculado | — |

## 6. Lenguaje publicado entre contextos

Términos que cruzan límites y deben significar lo mismo en todos los contratos. El detalle de cada uno, con su dueño,
está en [entidades compartidas](entidades-compartidas.md).

- **Código de carrier**: 2 caracteres. Sin maestro de aerolíneas.
- **Código de ciudad**: código IATA. Dueño BC4.
- **Tipo de pasajero**: `ADULT`, `CHILD`, `INFANT`.
- **Cabina**: `ECONOMY`, `PREMIUM_ECONOMY`, `BUSINESS`.
- **Proveedor**: `ALPHA`, `BETA`, `GAMMA`. Dueño BC2.
