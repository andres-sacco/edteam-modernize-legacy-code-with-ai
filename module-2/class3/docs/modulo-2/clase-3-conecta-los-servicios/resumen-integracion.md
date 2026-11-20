# Resumen de la integración: cliente REST, contrato y evento CiudadActualizada (M2 · C3)

Paso 2 de la clase, en la rama `integration/catalogo`, un paso por commit. Parte de la
[tabla de decisión](informe-investigacion.md): REST sólo con timeout y reintentos, y eventos con una réplica local
para lo que la búsqueda lee en cada request (F1 y F2), publicados desde el outbox (F3).

## Commits

| Paso | Commit | Qué agrega |
|------|--------|------------|
| 0 | `docs(integration): decision table…` | La tabla de decisión revisada, base de este paso |
| 1 | `feat(catalog): retries…` | Reintentos en `CatalogClient` (ya tenía timeouts) |
| 2 | `feat(contract): complete OpenAPI…` | Contrato OpenAPI del catálogo completo (componentes, 400/503, política de reintentos) |
| 3 | `feat(infra): RabbitMQ…` | RabbitMQ en los dos `docker-compose` y su contraseña en `.env` |
| 4 | `feat(catalog-service): event CiudadActualizada…` | Esquema JSON del evento y relay outbox → RabbitMQ en el servicio de catálogo |
| 5 | `feat(catalog): idempotent consumer…` | Consumidor idempotente en el monolito y réplica `city_replica` |

## 1. Cliente REST con timeout y reintentos

[`CatalogClient`](../../../twa-flights-monolith/src/main/java/com/twa/flights/catalog/client/CatalogClient.java),
configurado en `catalog.remote` de `application.yml`:

| Propiedad | Variable | Default | Qué hace |
|-----------|----------|---------|----------|
| `connectionTimeout` | `CATALOG_SERVICE_CONNECTION_TIMEOUT` | 500 ms | Ya existía |
| `readTimeout` | `CATALOG_SERVICE_READ_TIMEOUT` | 500 ms | Ya existía |
| `maxAttempts` | `CATALOG_SERVICE_MAX_ATTEMPTS` | 2 | Intentos totales por request (1 = sin reintentos) |
| `retryBackoff` | `CATALOG_SERVICE_RETRY_BACKOFF` | 100 ms | Espera entre intentos |

- **Se reintenta:** error de I/O (conexión rechazada, timeout) y 502, 503 y 504. El GET es idempotente.
- **No se reintenta:** 4xx, 500 y un cuerpo inválido. El próximo intento daría lo mismo.
- **Sin lógica de negocio:** el reintento es un bucle de transporte. Después del último intento, el que llama recibe la
  misma `CatalogUnavailableException` que antes (la búsqueda responde 503).
- **Peor caso:** `maxAttempts × (connection + read) + backoffs` = 2 × 1 s + 0,1 s ≈ 2,1 s por request de hasta 50 códigos.

## 2. Contrato OpenAPI

[`microcks/catalog-openapi.json`](../../../microcks/catalog-openapi.json), generado por
`microcks/generate-contracts.py` (el hook impide editarlo a mano).

- `components.schemas`: `City`, `Country`, `Continent` y `Error`, referenciados con `$ref`.
- Respuestas nuevas: `503` en las dos operaciones y `400` en `GET /cities` (sin códigos o más de 50), con el formato
  `Error`. No tienen ejemplos: Microcks sigue simulando sólo el 200 y el 404.
- `x-twa-client-retry` en cada operación: timeouts, intentos y qué se reintenta.
- `servers` local y de docker compose.
- Los ejemplos (86 ciudades y el 404) no cambian y `info.version` sigue en `1.0`: el mock de Microcks mantiene la URL
  `/rest/twa-flights-catalog/1.0` y los tests de contrato de los dos lados siguen pasando.

## 3. Evento CiudadActualizada v1

**Esquema:** [`microcks/events/ciudad-actualizada-v1.schema.json`](../../../microcks/events/ciudad-actualizada-v1.schema.json)
(JSON Schema 2020-12).

```json
{
  "eventId": "6f1c1f2a-4b0e-3c55-9a8e-2d1b5e0c7a11",
  "eventType": "CiudadActualizada",
  "eventVersion": 1,
  "occurredAt": "2026-11-17T21:00:00.000Z",
  "source": "twa-flights-catalog",
  "sequence": 120,
  "operation": "UPSERT",
  "cityId": 1,
  "city": { "code": "BUE", "name": "Buenos Aires", "timeZone": "America/Argentina/Buenos_Aires",
            "country": { "code": "AR", "name": "Argentina", "continent": { "code": "SA", "name": "South America" } } }
}
```

| Campo | Para qué |
|-------|----------|
| `eventId` | Id único del mensaje. Es un UUID v3 de `catalog_outbox:<id>`: si se republica, tiene el mismo id. Va también en `message_id` de AMQP |
| `eventVersion` | Versión del esquema. Va también en el header `eventVersion`. Un cambio incompatible es `v2`, con otra routing key |
| `sequence` | Id del outbox. Crece con cada cambio y ordena los eventos de una misma ciudad |
| `operation` | `UPSERT` (la ciudad existe con estos datos) o `DELETE` (sólo trae `city.code`) |
| `city` | El mismo JSON que `GET /cities/{code}` |

**Topología:** exchange topic `catalog.events`, routing key `catalog.ciudad-actualizada.v1`, cola del monolito
`flights.catalog.ciudad-actualizada` y su DLQ `flights.catalog.ciudad-actualizada.dlq`.

### Productor (servicio de catálogo)

- [`CityEventRelay`](../../../twa-flights-catalog/src/main/java/com/twa/catalog/service/CityEventRelay.java): cada
  segundo lee los eventos de ciudades de `flights.catalog_outbox` en orden, después de su checkpoint.
- [`CityEventPublisher`](../../../twa-flights-catalog/src/main/java/com/twa/catalog/service/CityEventPublisher.java):
  publica el mensaje persistente y `mandatory`, y espera el confirm del broker.
- El checkpoint (`event_relay_checkpoint`, migración `db/copy/V1.1` de la base `catalog`) avanza sólo con el confirm
  y con una cola que recibió el mensaje. Si el broker está caído o el monolito todavía no declaró su cola, el relay se
  detiene en ese evento y reintenta en la próxima corrida: **nunca saltea un evento** (al menos una vez).
- La carga inicial de V1.11 (un UPSERT por ciudad) hace de snapshot: la réplica se llena desde cero.
- [`CityEventMapper`](../../../twa-flights-catalog/src/main/java/com/twa/catalog/mapper/CityEventMapper.java) sólo
  copia campos. El país y el continente se agregan con un `LEFT JOIN` al leer el outbox.
- Flag `CATALOG_EVENTS_ENABLED` (requiere `CATALOG_COPY_ENABLED`). Métricas `catalog.events.published` y
  `catalog.events.relay.checkpoint`.

### Consumidor (monolito)

- [`CityUpdatedListener`](../../../twa-flights-monolith/src/main/java/com/twa/flights/catalog/listener/CityUpdatedListener.java):
  un solo consumidor (orden de la cola). Valida tipo, versión y campos obligatorios. Un mensaje inválido va a la DLQ
  sin bloquear los siguientes. Un error de base se reintenta 3 veces y después va a la DLQ.
- [`CityReplicaService`](../../../twa-flights-monolith/src/main/java/com/twa/flights/catalog/service/CityReplicaService.java),
  en una sola transacción:
  1. **Duplicado:** `INSERT IGNORE` del `eventId` en `processed_event`. Si ya estaba, se descarta.
  2. **Desorden:** si `sequence` ≤ `last_sequence` de la ciudad, se descarta. Un `DELETE` deja una lápida
     (`deleted = 1`), así un UPSERT viejo que llega tarde no revive la ciudad.
  3. Si no, aplica el UPSERT o el DELETE en `city_replica`.
- Migración `V1.13__create_city_replica.sql` y su rollback `U1.13`. Flag `CATALOG_EVENTS_ENABLED`. Métrica
  `catalog.events.consumed{result=applied|duplicate|outdated}`.

## Cómo probarlo

```bash
./scripts/init-env.sh                 # agrega RABBITMQ_PASSWORD a .env
docker compose up -d --build
./scripts/smoke-test.sh
docker exec flights-db mysql -uroot -pmuppet flights -e "SELECT COUNT(*) FROM city_replica"
```

Consola de RabbitMQ: http://localhost:15672 (usuario `twa`, contraseña `RABBITMQ_PASSWORD` de `.env`).

## Verificación (2026-11-17)

| Chequeo | Resultado |
|---------|-----------|
| `mvn clean package` | OK |
| Tests del monolito en los dos modos del catálogo | 218 + 218 sin fallas (1 salteado en REMOTE, ya existía) |
| Tests del servicio de catálogo (incluye el contrato) | 20 sin fallas |
| `./scripts/smoke-test.sh` con el stack completo | Todo OK. Health con `rabbit: UP` en los dos servicios |
| Snapshot inicial | 86 ciudades en `city_replica`, 86 en `processed_event` |
| Cambio de una ciudad (`UPDATE city … BUE`) | Llega a la réplica en segundos (sequence 390) |
| Mismo `eventId` publicado a mano | Descartado (`already processed`) |
| Evento viejo (sequence 300 < 390) | Descartado (`is older`), la réplica no cambia |
| Evento `eventVersion: 2` | 3 intentos y a la DLQ, la cola sigue |
| Broker caído durante un cambio | El relay reintenta cada segundo sin avanzar el checkpoint. Al volver el broker, la réplica recibe el cambio (sequence 391) |

## Pendiente y cosas raras (no se tocaron)

- **La búsqueda todavía no lee la réplica.** Pasar F1 y F2 a la réplica, con flag y con *readiness* hasta cargar el
  snapshot, es de la clase 4.
- **Sólo hay evento de ciudad.** Un cambio de país o continente no publica `CiudadActualizada`. El país y el
  continente del evento son los del momento de la publicación. Hace falta `PaisActualizado` para cubrirlo.
- **Sin tests nuevos** (CLAUDE.md: no se agregan salvo pedido). El esquema del evento no tiene test de contrato y
  Microcks no lo simula (falta el componente async). Es lo primero que va a marcar el paso 3.
- `occurredAt` asume que MySQL corre en UTC, como en el stack de docker.
- `CatalogClient` sigue validando que la ciudad venga completa (código, huso horario y país) y asigna cada código con
  la colación de MySQL. Es lógica previa a este paso y no se movió.
- El listener reintenta también los mensajes inválidos antes de mandarlos a la DLQ: Spring AMQP reintenta toda
  excepción. Son 3 intentos sin efecto.
