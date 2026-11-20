# TWA Flights - Monolith

Aplicación monolítica de búsqueda de vuelos. Reúne en un único despliegue la lógica que antes estaba repartida en
`api-clusters`, `api-catalog`, `api-pricing`, `api-itineraries-search`, `api-provider-alpha` y `api-provider-beta`.

La única funcionalidad expuesta es la **búsqueda de clusters** (itinerarios con precio final, ordenados y paginados).
El catálogo de ciudades, el cálculo de markup, la búsqueda en proveedores y los proveedores simulados son lógica interna.

## Requisitos

- Java 21+
- Maven 3.9+
- Docker

## Cómo ejecutarlo

Todo con Docker (base de datos + Microcks + aplicación):

```bash
make start
```

Sólo la infraestructura (base de datos y Microcks), y la aplicación desde el IDE o por consola:

```bash
make start-infra
mvn spring-boot:run
```

| Componente           | Puerto |
|----------------------|--------|
| twa-flights-monolith | 8080   |
| MySQL (`flights`)    | 3320   |
| Microcks             | 8585   |

La conexión a la base se puede cambiar con las variables `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER` y `DB_PASSWORD`.
Flyway crea el esquema y carga los datos al iniciar.

## Administración de políticas comerciales (Pricing)

Administra las políticas que usa el pricing para calcular el markup de cada aerolínea. Los cambios aplican a las
búsquedas nuevas en forma inmediata. Todas las operaciones requieren el header `X-Api-Key` (variable
`PRICING_API_KEY`, por defecto `twa-pricing-local-key`). En Swagger está en el grupo **pricing**.

| Método | URL (`/api/flights/pricing/commercial-policies`) | Descripción                                   |
|--------|------------------------------------------|-------------------------------------------------------|
| GET    | `/` (`?active=true&carrier=AR` opcionales) | Lista ordenada por prioridad                          |
| GET    | `/{id}`                                  | Detalle de una política                               |
| POST   | `/`                                      | Crea una política (activa por defecto)                |
| PUT    | `/{id}`                                  | Reemplaza la política, incluidas sus aerolíneas       |
| PATCH  | `/{id}/status`                           | Activa o desactiva (`{"active": false}`)             |
| DELETE | `/{id}`                                  | Borra la política                                     |
| PUT    | `/{id}/carriers/{carrier}`               | Agrega una aerolínea o cambia su porcentaje (`{"percentage": 3}`) |
| DELETE | `/{id}/carriers/{carrier}`               | Quita una aerolínea (debe quedar al menos una)        |

```bash
curl -X POST "http://localhost:8080/api/flights/pricing/commercial-policies" \
  -H "X-Api-Key: twa-pricing-local-key" -H "Content-Type: application/json" \
  -d '{"name":"Summer 2027","priority":20,"startDate":"2026-12-01T00:00:00","expireDate":"2027-03-01T00:00:00","carriers":[{"carrier":"AR","percentage":3}]}'
```

Cuando varias políticas vigentes incluyen la aerolínea del itinerario, se aplica la de mayor `priority`.
Errores de validación: códigos `40023` a `40029`; política o aerolínea inexistente: `40403` / `40404`; sin API key: `40101`.

## Proveedores externos (Microcks)

Los proveedores Alpha, Beta y Gamma son APIs externas simuladas con [Microcks](https://microcks.io). Sus contratos OpenAPI
están en [`microcks/`](microcks) y el contenedor `microcks-importer` los carga al levantar la infraestructura.

| Proveedor | URL                                                          | Variable              |
|-----------|--------------------------------------------------------------|-----------------------|
| Alpha     | `http://localhost:8585/rest/provider-alpha/1.0/itineraries`  | `PROVIDER_ALPHA_HOST` |
| Beta      | `http://localhost:8585/rest/provider-beta/1.0/itineraries`   | `PROVIDER_BETA_HOST`  |
| Gamma     | `http://localhost:8585/rest/provider-gamma/1.0/itineraries`  | `PROVIDER_GAMMA_HOST` |

```bash
# Fechas futuras (hoy + 60 y + 70 días); los ejemplos de este README las usan
read -r IDA VUELTA <<<"$(python3 -c 'import datetime as d; t = d.date.today(); print(t + d.timedelta(days=60), t + d.timedelta(days=70))')"
curl "http://localhost:8585/rest/provider-alpha/1.0/itineraries?origin=BUE&destination=MIA&departureDate=$IDA&returnDate=$VUELTA&adults=1"
```

Todos reciben los mismos parámetros, pero cada uno responde con **su propio formato**, que la aplicación transforma
al itinerario interno con un mapper por proveedor (`mapper/provider`):

| Proveedor | Formato de respuesta                                                                                 |
|-----------|------------------------------------------------------------------------------------------------------|
| Alpha     | camelCase anidado: `results[].journeys[].flights[]`, fechas ISO, tarifas por `ADT`/`CHD`/`INF`, cabina `Y`/`W`/`C` |
| Beta      | snake_case con sobre `status`/`data`: `bounds[]` con `direction`, fecha y hora separadas, importes en centavos |
| Gamma     | PascalCase: lista plana `Flights[]` con `SegmentIndex`/`Sequence`, equipaje como texto (`1PC23KG`), cabina `ECO`/`PEY`/`BUS` |

Los contratos se generan con `python3 microcks/generate-contracts.py`.

Cada proveedor devuelve itinerarios con aerolíneas, horarios, escalas y tarifas aleatorias según la cabina (`cabin`); si se envía `returnDate` responde
itinerarios de ida y vuelta, si no, sólo de ida. El parámetro `amount` es opcional y el conector lo usa para limitar
la respuesta. La consola de Microcks está en http://localhost:8585.

## API

| Método | URL                                           | Descripción                          |
|--------|-----------------------------------------------|--------------------------------------|
| GET    | `/api/flights/clusters/itineraries`           | Búsqueda de itinerarios con precio   |
| GET    | `/api/flights/clusters/{id}/itineraries/{itineraryId}` | Un itinerario de una búsqueda anterior |
| *      | `/api/flights/pricing/commercial-policies/**` | Administración de políticas comerciales (ver abajo) |
| GET    | `/api/flights/documentation`                  | Swagger UI                           |
| GET    | `/api/flights/actuator/health`                | Estado de la aplicación              |

Nueva búsqueda (la búsqueda rechaza fechas pasadas, por eso los ejemplos calculan `IDA` y `VUELTA` a partir de hoy):

```bash
read -r IDA VUELTA <<<"$(python3 -c 'import datetime as d; t = d.date.today(); print(t + d.timedelta(days=60), t + d.timedelta(days=70))')"
curl "http://localhost:8080/api/flights/clusters/itineraries?from=BUE,MIA&to=MIA,BUE&departure=$IDA,$VUELTA&adults=1&children=1&infants=0&amount=10"
```

`amount` es opcional: indica la cantidad de itinerarios por página (y el máximo que se pide a cada proveedor).
Si no se envía, se devuelven todos los itinerarios que respondan los proveedores en una única página.

Ordenamiento (opcional, no distingue mayúsculas):

| Parámetro   | Valores                         | Default |
|-------------|---------------------------------|---------|
| `sortBy`    | `PRICE` (precio total), `CARRIER` (código de aerolínea), `DURATION` (tiempo total de vuelo) | `PRICE` |
| `sortOrder` | `ASC`, `DESC`                   | `ASC`   |

Con `CARRIER` y `DURATION`, los empates se resuelven por el precio más bajo.

```bash
curl "http://localhost:8080/api/flights/clusters/itineraries?from=BUE,MIA&to=MIA,BUE&departure=$IDA,$VUELTA&adults=1&children=0&infants=0&sortBy=DURATION&sortOrder=ASC"
```

Cabina (opcional): `cabin` = `ECONOMY` (default), `PREMIUM_ECONOMY` o `BUSINESS`. Cambia las tarifas y la franquicia
de equipaje; cada itinerario devuelve su `cabin` y su `baggage` (`carryOn` y `checked`, con piezas y kilos).

Filtros (opcionales, combinables):

| Parámetro          | Descripción                                                             | Ejemplo    |
|--------------------|-------------------------------------------------------------------------|------------|
| `maxStops`         | Máximo de escalas por tramo (`0` = sólo directos)                       | `0`        |
| `minPrice` / `maxPrice` | Rango del precio total del itinerario                              | `300` / `900` |
| `carriers`         | Sólo estas aerolíneas, separadas por coma                               | `AR,AA`    |
| `excludedCarriers` | Excluir estas aerolíneas, separadas por coma                            | `DL`       |
| `departureTime`    | Franja de salida del vuelo de ida: `NIGHT` (00-06), `MORNING` (06-12), `AFTERNOON` (12-18), `EVENING` (18-24) | `MORNING` |
| `checkedBaggage`   | `true`: sólo con valija despachada incluida; `false`: sólo sin valija    | `true`     |

```bash
curl "http://localhost:8080/api/flights/clusters/itineraries?from=BUE,MIA&to=MIA,BUE&departure=$IDA,$VUELTA&adults=1&children=0&infants=0&cabin=BUSINESS&maxStops=0&carriers=AR,AA"
```

La búsqueda guarda todos los itinerarios; los filtros y el orden se aplican en cada request, así que al paginar con el
mismo `id` se pueden cambiar. `pagination.total` indica la cantidad de itinerarios que cumplen los filtros.

Siguiente página de una búsqueda anterior (los resultados se conservan 5 minutos): repetir los mismos parámetros
agregando `id` (el valor devuelto por la primera búsqueda) y `offset` (número de página, empezando en 0). Cada página
se ordena según el `sortBy`/`sortOrder` de ese request, así que se puede cambiar el orden mientras se pagina.

Detalle de un itinerario de una búsqueda anterior (mientras no expire, 5 minutos), usando el `id` de la búsqueda y el
`id` del itinerario. El `id` de la búsqueda puede enviarse tal cual o codificado (URL-encoded):

```bash
curl "http://localhost:8080/api/flights/clusters/{id}/itineraries/{itineraryId}"
```

Si la búsqueda no existe o expiró responde 404 con código `40401`; si el itinerario no pertenece a la búsqueda, 404
con código `40402`.

## Estructura

```
com.twa.flights
├── controller      Endpoint público y documentación
├── validator       Validación de la búsqueda
├── service         Lógica de negocio (clusters, catálogo, pricing, búsqueda, proveedores)
├── facade          Acceso a cada proveedor
├── connector       Clientes HTTP de los proveedores Alpha, Beta y Gamma
├── helper          Utilidades (ids, duplicados, duración de vuelos)
├── repository      Acceso a datos (JPA)
├── specification   Consultas de políticas comerciales
├── model           Entidades
├── mapper          Conversión de entidades y de las respuestas de cada proveedor al itinerario interno
├── dto / enums     Objetos compartidos entre capas (dto/provider: formato de cada proveedor)
├── serializer      Serialización de búsquedas guardadas
├── job             Limpieza de búsquedas expiradas
├── configuration   Swagger, Jackson
└── exception       Excepciones y manejo de errores
```

Base de datos única (`flights`):

| Tabla                        | Uso                                        |
|------------------------------|--------------------------------------------|
| `continent`, `country`, `city` | Catálogo de ciudades y zonas horarias    |
| `commercial_policy`, `commercial_policy_carrier` | Reglas de markup por aerolínea |
| `cluster_search`             | Resultados de búsqueda para paginar (TTL) |
