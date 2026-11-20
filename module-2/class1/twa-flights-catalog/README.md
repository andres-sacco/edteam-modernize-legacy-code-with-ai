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
