# Revisión escéptica: integración entre el catálogo y el monolito

> Objetivo: probar que la integración **no** está lista. Se revisan el cliente REST con reintentos, el contrato
> OpenAPI del catálogo, el evento `CiudadActualizada` v1 (productor y consumidor) y sus tests, descritos en
> [resumen-integracion.md](resumen-integracion.md). La revisión no corrige nada. Cada fila tiene un archivo, una línea o
> el resultado de una prueba como evidencia. Lo que no se pudo probar queda como "no verificado".

| Ronda | Fecha | Commit revisado | Veredicto |
|---|---|---|---|
| 1 | 2026-11-17 | `e3496a1` (rama `integration/catalogo`) | **No se puede cerrar**: el evento no tiene test de contrato, y un evento válido o una falla transitoria de la base terminan en la DLQ sin forma de reprocesarlos |

## Cómo se probó

- **Tests:** `mvn -B test` desde la raíz. Corre el monolito en los modos LOCAL y REMOTE del catálogo, y el servicio de
  catálogo.
- **Stack:** `docker compose up -d --build` con un override fuera del repo que cambia sólo
  `CATALOG_MODE: REMOTE` en `twa-flights-monolith`. El `docker-compose.yml` del repo lo deja en `LOCAL`, y en ese modo
  la búsqueda no llama al servicio de catálogo. `./scripts/smoke-test.sh` pasó antes de empezar. El snapshot inicial
  dejó 86 ciudades en `city_replica`.
- **Búsqueda de prueba:** `GET /api/flights/clusters/itineraries?from=BUE&to=SCL&departure=<hoy+30>&adults=1&children=0&infants=0`.
- **Caída del servicio:** `docker stop twa-flights-catalog`, que da connection refused, y `docker pause`, que deja el
  servicio sin responder (timeout).
- **Eventos a mano:** se publicaron en el exchange `catalog.events` con la routing key `catalog.ciudad-actualizada.v1`
  por la API de gestión de RabbitMQ (`POST /api/exchanges/%2F/catalog.events/publish`), con el formato del esquema.
- **Eventos reales:** se generaron con `UPDATE`/`INSERT` sobre `flights.city` (el trigger escribe el outbox y el relay
  publica). El duplicado del productor se simuló bajando `catalog.event_relay_checkpoint.last_event_id`.
- **Fallas de infraestructura:** `docker stop rabbitmq`, `docker stop twa-flights-monolith`, borrado de la cola del
  monolito por la API de gestión, y `RENAME TABLE processed_event` durante ~5 s para provocar un error de base en el
  consumidor.
- **Resultado:** se leyó en `city_replica`, en el checkpoint, en las colas (mensajes y consumidores), en la métrica
  `catalog.events.consumed{result}` y en los logs de los dos servicios.
- Al final se bajó el stack (`docker compose down`). La base no persiste. No se cambió código.
- Incidente: un comando pausó `flights-db` por error durante unos segundos. Se reanudó enseguida, volvió a `healthy`
  con `RestartCount 0` y no afecta los resultados.

## Contratos y sus tests

| Contrato | Lado proveedor | Lado consumidor |
|---|---|---|
| `GET /cities/{code}` → 200 (86 ejemplos) y 404 | `CatalogContractTest.java:31,43` | `CatalogClientTest.java:199` |
| `GET /cities?codes=` → 200 (lotes) | `CatalogContractTest.java:55` | `CatalogClientTest.java:220,233` |
| `GET /cities?codes=` → 400 | Sin test contra el contrato (no tiene ejemplo) | El cliente lo trata como `CatalogUnavailableException` (`CatalogClientTest.java:143`), sin compararlo con el contrato |
| 503 en las dos operaciones | `CityControllerErrorsTest.java:34,41` prueba el 503 y el código 50301, pero no lo compara con el esquema `Error` | `CatalogClientTest.java:143` |
| `x-twa-client-retry` (2 intentos, sólo I/O/502/503/504) | — | Sin test: ningún test cuenta los requests |
| `microcks/events/ciudad-actualizada-v1.schema.json` | **Sin test** | **Sin test** |

El `grep` de `Rabbit|CityUpdated|CityReplica|ciudad-actualizada|schema.json` en `src/test` de los dos proyectos no
encuentra nada. Microcks tampoco simula el evento.

## Resultados

| # | Chequeo | Evidencia | Estado | Severidad |
|---|---|---|---|---|
| 1 | Suite completa | `mvn -B test`: monolito 218 + 218 (LOCAL/REMOTE, 1 salteado, ya existía), catálogo 20. `BUILD SUCCESS` | OK | — |
| 2 | Contrato REST 200/404: lado proveedor | `CatalogContractTest.java:31,43,55`, contra el mismo `microcks/catalog-openapi.json` (`twa-flights-catalog/pom.xml:128`) | OK | — |
| 3 | Contrato REST 200/404: lado consumidor | `CatalogClientTest.java:199` lee las 86 ciudades del contrato (`twa-flights-monolith/pom.xml:186`) | OK | — |
| 4 | Respuestas 400 y 503 del contrato | No tienen ejemplos y ningún test las compara con el contrato | Falta test | Media |
| 5 | Política de reintentos del cliente | `CatalogClientTest.java:143,154,162` sólo comprueban la excepción. En el stack sí funciona: `Attempt 1 of 2 … retrying` y un solo reintento | Falta test (funciona en el stack) | Media |
| 6 | Contrato del evento | Sin test en el productor (`CityEventMapper`, `CityEventPublisher`) ni en el consumidor (`CityUpdatedListener`, `CityReplicaService`) | **Sin test** | **Alta** |
| 7 | Catálogo caído (connection refused) | 3 búsquedas: `503` con `{"code":50301,…}` en 1,11 s cada una. Al levantar el catálogo vuelve a `200` | OK | — |
| 8 | Catálogo colgado (timeout), 1 + 10 requests concurrentes | Todos `503` en ~1,11 s. Al despausarlo vuelve `200` en 0,06 s | OK | — |
| 9 | Carga sostenida con el catálogo caído | No hay circuit breaker: cada request ocupa un hilo de Jetty (máx. 50, `application.yml`) ~1,1 s. Sin prueba de carga | No verificado | Media |
| 10 | Duplicado real del productor (crash entre el confirm y el commit del checkpoint) | El checkpoint bajó en 3 → el relay republicó la secuencia 390 con el mismo `eventId`. Log `already processed`, `result=duplicate` = 1, la réplica sigue en `Buenos Aires R1`/390 | OK | — |
| 11 | Mismo `eventId` con otro contenido | Descartado (`duplicate` = 2). La réplica no cambia | OK | — |
| 12 | Desorden: secuencia menor (300) o igual (390), con otro `eventId` | Las dos son `outdated`. La réplica no cambia | OK | — |
| 13 | Desorden: el más nuevo llega primero (900011 `NEW` y después 900010 `OLD`) | Queda `NEW`/900011. Un DELETE posterior con 900005 también se descarta | OK | — |
| 14 | Lápida: DELETE 900001 y después un UPSERT viejo 900000 | Queda `deleted=1`/900001: el UPSERT viejo no revive la ciudad | OK | — |
| 15 | Mensaje que no es JSON, y `eventVersion: 2`, con un evento válido detrás | Los dos van a la DLQ, y el válido se aplica (`Santiago R1`/392). Cada inválido se reintenta 3 veces antes de ir a la DLQ | OK | Baja (reintentos inútiles, ya anotado en el resumen) |
| 16 | **Evento válido según el esquema que el consumidor rechaza** | `INSERT` de una ciudad con `country_id NULL`. El esquema permite `country` nulo, pero `CityUpdatedListener.java:77-79` exige país y huso en un UPSERT. Resultado: 3 intentos, DLQ, y la ciudad 9100 nunca llega a `city_replica` | **Falla** | **Alta** |
| 17 | **Falla transitoria de la base del consumidor (~5 s)** | Con `processed_event` renombrada, un `UPDATE` de SCL agota los 3 intentos en ~1,5 s (`spring.rabbitmq.listener.simple.retry` en `application.yml`) y va a la DLQ. La réplica queda en `Santiago R1`/392 con el catálogo en `Santiago R2` y el checkpoint en 397: nadie lo reentrega | **Falla** | **Alta** |
| 18 | Procedimiento para reprocesar la DLQ | No hay script, comando de `Makefile` ni documentación en `docs/modulo-2` | Falta | Alta (va con 16 y 17) |
| 19 | Broker caído durante un cambio | El relay loguea `retried in the next run` y el checkpoint no pasa de 392. Al volver el broker se aplica `Buenos Aires R2`/393 | OK | — |
| 20 | Broker caído: efecto en el health del monolito | `actuator/health` responde **503** aunque la búsqueda sigue en `200`. El healthcheck de compose (`docker-compose.yml:98`) usa ese endpoint: el monolito queda unhealthy por un componente que la búsqueda todavía no usa | Hallazgo | Media |
| 21 | Consumidor caído durante 2 cambios de la misma ciudad | Al arrancar, la réplica queda en `Buenos Aires R4`/395, el último | OK | — |
| 22 | Cola del consumidor inexistente | Borrada la cola, el relay loguea `No queue is bound…` y el checkpoint se queda en 395. Al arrancar el monolito, que vuelve a declarar la cola, se aplica `R5`/396 | OK | — |
| 23 | Cambio de país o continente | No publica `CiudadActualizada`: la réplica queda con datos viejos. Ya está anotado en el resumen como pendiente | No verificado (diseño) | Media |
| 24 | Reset sólo de la base del catálogo con las secuencias reiniciadas | Los `eventId` son UUID v3 del id del outbox (`CityEventMapper.eventId`): chocarían como duplicados y las secuencias quedarían como viejas | No verificado | Media |
| 25 | Commit tardío en el outbox, después de `settle-ms` (2 s) | `CatalogOutboxRepository.java:252` lo reconoce: un id bajo que se confirma tarde queda detrás del checkpoint y no se publica | No verificado | Media |
| 26 | Evento tóxico en el productor (payload inválido en el outbox) | `CityEventMapper.parse` lanza una excepción y `CityEventRelay.relay` reintenta el mismo evento en cada corrida: los siguientes quedan bloqueados | No verificado | Media |

## Veredicto: no se puede cerrar

Lo que funciona: el cliente REST ante la caída y el timeout del catálogo (7, 8), con 503 y sin respuestas parciales.
También el consumidor ante duplicados y desorden (10 a 14), con la lápida y la secuencia por ciudad, y la entrega al
menos una vez del productor con el broker o el consumidor caídos (19, 21, 22).

Lo que impide cerrar:

1. **El evento no tiene test de contrato (fila 6).** Cada contrato tenía que tener test, y el del evento tiene cero en
   los dos lados. La fila 16 es justamente lo que ese test habría encontrado.
2. **Hay eventos válidos que se pierden en la DLQ sin forma de recuperarlos (filas 16, 17 y 18).** Pasa con un evento
   que cumple el esquema y con una falla de base de pocos segundos. La réplica queda distinta del catálogo hasta el
   próximo cambio de esa ciudad. Hoy no afecta a los usuarios, porque la búsqueda no lee la réplica, pero bloquea la
   clase 4, que va a empezar a leerla.

Además conviene resolver, antes o junto con lo anterior:

3. Tests de las respuestas 400 y 503 contra el contrato, y de la cantidad de reintentos del cliente (filas 4 y 5).
4. Que el health del monolito no dependa de RabbitMQ mientras la búsqueda no use la réplica (fila 20).
5. Decidir qué hacer con las filas 23 a 26, que quedaron sin verificar.
