# TWA Catalog Service

Catálogo de ciudades, países y continentes (BC4) extraído del monolito TWA Flights (M2 · C1, etapa 2 del
[plan de extracción](../docs/modulo-1/clase-6-planifica-la-migracion/plan-de-extraccion.md)). Lee las tablas
`continent`, `country` y `city` de la **misma base** `flights` del monolito con un usuario de solo lectura; el esquema y
los datos los sigue migrando el monolito con Flyway.

## Endpoints

Base: `http://localhost:8081/api/catalog`

| Método y ruta | Respuesta |
|---------------|-----------|
| `GET /cities/{code}` | `200` con la ciudad · `404` (`40401`) si no existe · `503` (`50301`) si la base no responde |
| `GET /cities?codes=BUE,MIA` | `200` con las ciudades que existen, en el orden pedido y sin repetidas · `400` sin códigos (`40001`) o con más de 50 (`40002`) |
| `GET /actuator/health`, `/actuator/info`, `/actuator/metrics` | Actuator |
| `GET /documentation` | Swagger UI |

El JSON de una ciudad es el mismo que el `CityDTO` del monolito (sin `id`):

```json
{"code":"BUE","name":"Buenos Aires","timeZone":"America/Argentina/Buenos_Aires",
 "country":{"code":"AR","name":"Argentina","continent":{"code":"SA","name":"South America"}}}
```

Los códigos se normalizan a mayúsculas y no se valida su formato (`MI` o `ABCD` son ciudades inexistentes, igual que en
el monolito).

## Configuración (variables de entorno)

| Variable | Default | Para qué |
|----------|---------|----------|
| `SERVER_PORT` | `8081` | Puerto HTTP |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `localhost` / `3320` / `flights` | Base del monolito |
| `DB_USER` / `DB_PASSWORD` | `catalog_reader` / **sin default** | Usuario de solo lectura. Lo crea `docker/mysql/init` sin permisos y el contenedor `catalog-db-grants` le da `SELECT` sólo sobre `continent`, `country` y `city` cuando Flyway del monolito las crea. La contraseña sale de `.env` (`./scripts/init-env.sh`); sin `DB_PASSWORD` el servicio no arranca |
| `DB_POOL_SIZE` | `5` | Conexiones de Hikari (la MySQL se comparte con el monolito) |
| `SERVER_THREADS_MIN` / `SERVER_THREADS_MAX` | `10` / `50` | Hilos de Jetty |
| `CATALOG_MAX_CODES` | `50` | Máximo de códigos en `GET /cities?codes=` |
| `CATALOG_COPY_ENABLED` | `false` | Copia del catálogo en la base propia `catalog` (M2 · C2). En `true` el servicio migra esa base con su Flyway (`db/copy`) y el publicador aplica los eventos de `flights.catalog_outbox`. Las lecturas siguen siendo de `flights`. `docker-compose.yml` la enciende |
| `CATALOG_COPY_DB_HOST` / `CATALOG_COPY_DB_PORT` / `CATALOG_COPY_DB_NAME` | los de `DB_HOST` / `DB_PORT` / `catalog` | Base propia del servicio |
| `CATALOG_COPY_DB_USER` / `CATALOG_COPY_DB_PASSWORD` | `catalog_writer` / **sin default** | Dueño de la base `catalog` (lo crea `docker/mysql/init/02-create-catalog-database.sh`). La contraseña sale de `.env` |
| `CATALOG_COPY_PUBLISHER_DELAY_MS` / `CATALOG_COPY_PUBLISHER_SETTLE_MS` / `CATALOG_COPY_PUBLISHER_BATCH_SIZE` | `1000` / `2000` / `100` | Pausa entre corridas del publicador, edad mínima de un evento para publicarlo y eventos por corrida |

## Copia del catálogo y outbox (M2 · C2)

- El monolito escribe cada cambio de `continent`, `country` y `city` en `flights.catalog_outbox` con triggers, en la
  misma transacción (`V1.11`). La migración encola también todas las filas existentes.
- `CatalogOutboxPublisher` lee el outbox en orden y aplica cada evento en la base `catalog` con un upsert por id, en la
  misma transacción que el checkpoint (`outbox_checkpoint`). Aplicar un evento dos veces da el mismo resultado; si uno
  falla, el publicador se detiene ahí y reintenta en la corrida siguiente.
- Métricas: `catalog.outbox.pending` (eventos sin aplicar) y `catalog.outbox.lag` (atraso de cada evento aplicado).
- Rollback: `CATALOG_COPY_ENABLED=false`. Para borrar la copia, `src/main/resources/db/rollback/U1.0__create_catalog_copy_tables.sql`
  sobre la base `catalog`; al volver a encender el flag se reconstruye desde el primer evento.

## Comandos

Desde la raíz del repositorio:

```bash
mvn -q -B -pl twa-flights-catalog clean package -DskipTests   # compilar
mvn -B -pl twa-flights-catalog test                           # tests (necesita Docker: MySQL de Testcontainers)
make run-catalog                                              # con make start-infra levantado
```

## Docker

El `Dockerfile` copia el jar de `target/`, igual que el del monolito: `mvn -q -B clean package -DskipTests` desde la
raíz compila los dos y `docker compose up -d --build` levanta este servicio (`twa-flights-catalog`, puerto 8081).
Arranca después de que el monolito está sano, porque las tablas las crea Flyway del monolito.
