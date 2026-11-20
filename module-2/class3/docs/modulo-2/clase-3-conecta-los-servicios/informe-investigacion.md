# Informe de investigación: REST o evento por flujo (M2 · C3)

Paso 1 de la clase: clasificar cada flujo entre el Catálogo de ciudades y el monolito (Búsqueda y Precios) como REST o
evento, justificarlo e indicar qué falla si el otro lado no responde. Criterio: minimizar el acoplamiento temporal
entre servicios. Análisis de solo lectura sobre la rama `module2/class3`.

## Estado actual

- **No hay RabbitMQ en el código.** Ningún `pom.xml` ni `docker-compose*.yml` lo incluye. El único flujo asíncrono es
  el outbox que el catálogo lee por polling sobre la base compartida `flights`.
- **Precios no depende del catálogo.** El paquete `pricing/` no usa ciudades, países ni continentes. Busqué `catalog`,
  `city`, `country`, `origin` y `destination`, y no hay coincidencias.
- **El detalle del itinerario no llama al catálogo.** `GET /clusters/{id}/itineraries/{itineraryId}` lee
  `cluster_search`, que ya guarda la duración calculada. `ClustersService` no aparece entre las clases que usan
  `CatalogApi`.

## Tabla de decisión

| # | Flujo (archivo y clase) | Datos y consumidor | Hoy | Decisión | Justificación | Riesgo de la decisión | Si el otro servicio no responde |
|---|---|---|---|---|---|---|---|
| F1 | Validación de origen y destino: [AvailabilityRequestValidator.java:229](../../../twa-flights-monolith/src/main/java/com/twa/flights/search/validator/AvailabilityRequestValidator.java) → `CatalogApi.getCities` → [CatalogApiRouter](../../../twa-flights-monolith/src/main/java/com/twa/flights/catalog/client/CatalogApiRouter.java) → [CatalogClient](../../../twa-flights-monolith/src/main/java/com/twa/flights/catalog/client/CatalogClient.java) (`GET /cities?codes=`) | Usa el código, el país y el continente de cada ciudad. Lo consume Búsqueda en cada request. | REST síncrono, con caché opcional en [CachedCatalogApi](../../../twa-flights-monolith/src/main/java/com/twa/flights/catalog/client/CachedCatalogApi.java) | **Evento**: réplica local en Búsqueda, cargada con los eventos del catálogo | Son datos de referencia chicos que sólo cambian con Flyway, y se leen en cada búsqueda. Con REST, cada búsqueda depende de que el catálogo esté arriba. ADR-002 D5 ya acepta 60 s de datos viejos por la caché. | **Consistencia eventual.** Una ciudad nueva tarda lo que marca `catalog.outbox.lag` en ser válida, y una ciudad borrada sigue válida ese tiempo. **Arranque en frío:** si la réplica está vacía, todo código da 40014 (400) en vez de 503. Por eso la réplica tiene que reportar *not ready* hasta cargar el snapshot. Hoy el catálogo no expone un endpoint que liste todas las ciudades. | **Hoy:** después del timeout de 500 ms se lanza `CatalogUnavailableException` y la búsqueda devuelve 503. La caché no cubre la caída: los fallos no se cachean y una entrada vencida se pierde. **Con evento:** la búsqueda sigue funcionando con la última réplica. Lo único que se atrasa es la llegada de cambios. |
| F2 | Duración del vuelo: `ProviderAlpha/Beta/GammaService.calculateFlightDuration` → [FlightHelper.java:102](../../../twa-flights-monolith/src/main/java/com/twa/flights/provider/helper/FlightHelper.java) `getCities`. Los métodos de una sola ciudad están en las líneas 72 y 127. | Usa el `timeZone` de cada ciudad de cada leg. Lo consumen los 3 proveedores en paralelo. | REST síncrono. Sin `catalog.batch.enabled`, hace una llamada por ciudad. | **Evento**: la misma réplica de F1 | Es el flujo con más llamadas: cada proveedor pide todas las ciudades de sus legs. El huso horario casi nunca cambia, así que un dato un poco viejo no cambia el resultado. | Si se cambia el huso horario de una ciudad, las duraciones salen mal hasta que llega el evento. Son minutos, para un dato que casi nunca cambia. | **Hoy:** un proveedor recibe `CatalogUnavailableException` y [ItinerariesSearchService.java:81](../../../twa-flights-monolith/src/main/java/com/twa/flights/search/service/ItinerariesSearchService.java) cancela los demás futures. La búsqueda entera falla, aunque Alpha, Beta y Gamma hayan respondido. **Con evento:** no hay dependencia en tiempo de ejecución. |
| F3 | Propagación de cambios: triggers de [V1.11](../../../twa-flights-monolith/src/main/resources/db/migration/V1.11__create_catalog_outbox.sql) → `flights.catalog_outbox` → [CatalogOutboxPublisher](../../../twa-flights-catalog/src/main/java/com/twa/catalog/service/CatalogOutboxPublisher.java) → [CatalogCopyRepository](../../../twa-flights-catalog/src/main/java/com/twa/catalog/repository/CatalogCopyRepository.java) | Cada evento lleva `UPSERT`/`DELETE` con la fila completa de un continente, país o ciudad. Hoy lo consume sólo la copia del catálogo. | Evento por outbox con polling sobre la base compartida | **Evento**, publicado en RabbitMQ (por ejemplo `CiudadActualizada` en un exchange topic `catalog.events`) | El outbox ya garantiza que no hay cambio sin evento, y aplicar un evento dos veces da la misma fila (upsert por id). Con un broker, el catálogo y la réplica de Búsqueda (F1/F2) se suscriben sin leer la base del otro. | Hay que conservar el orden. Hoy es estricto por id del outbox y con checkpoint. En RabbitMQ conviene una sola cola por consumidor, o tolerar el desorden con versión o id del evento. Además, un evento con error frena todo lo que viene después. | **Hoy:** si la base `catalog` o el publisher se caen, el evento queda en el outbox, `catalog.outbox.pending` sube y la copia queda atrasada sin perder nada. **Con RabbitMQ:** si el broker se cae, el relay deja los eventos en el outbox y los reintenta. Si un consumidor se cae, los mensajes se acumulan en su cola. |
| F4 | El catálogo lee las tablas del monolito: `CityRepository` (JPA) del servicio, con el datasource `flights` y el usuario `catalog_reader` ([application.yml](../../../twa-flights-catalog/src/main/resources/application.yml)) | Lee las tablas `city`, `country` y `continent` del monolito. Lo consume la API `/api/catalog/cities`. | Base compartida (ni REST ni evento) | **Evento**: que el catálogo lea su propia copia, alimentada por F3 | Es el acoplamiento más fuerte, porque la base del monolito se vuelve un contrato. ADR-002 D2 ya prevé este cambio. | Durante la transición hay dos fuentes. Mientras `flights` sea la base principal, lo que se lee de la copia puede estar atrasado. | **Hoy:** si MySQL de `flights` se cae, el catálogo responde con error y el monolito devuelve 503 (F1/F2). En modo LOCAL, [CatalogService](../../../twa-flights-monolith/src/main/java/com/twa/flights/catalog/service/CatalogService.java) traduce `DataAccessException` a la misma excepción. |
| F5 | Precios ↔ Catálogo | No hay datos compartidos | No existe | **Ninguno** | No hay dependencia, y no hay que crearla. Si en el futuro hay políticas por país o ruta, usar la misma réplica por eventos, no REST. | Ninguno | No aplica |

## Resumen de la decisión

- **REST** sólo para consultas puntuales sin camino crítico, como el soporte o la API pública del catálogo. No para la
  búsqueda.
- **Eventos con una réplica local** para todo lo que la búsqueda lee en cada request (F1 y F2). Así, que el catálogo
  esté caído pasa de ser una falla 503 a sólo un atraso en los cambios.
- **Transición sugerida:** mientras no exista la réplica, activar `catalog.batch.enabled` y `catalog.cache.enabled`.
  Además, servir la entrada vencida cuando el catálogo falla (*stale-on-error*). Eso es un cambio de comportamiento:
  hoy los fallos no se cachean.

## Cosas raras (no se tocaron)

- El comentario de `V1.11` dice que el publisher está en `com.twa.catalog.outbox.CatalogOutboxPublisher`, pero la
  clase está en `com.twa.catalog.service`.
- En `FlightHelper` (líneas 72 y 127) siguen los métodos que piden una ciudad por llamada. Si alguien todavía los usa,
  cada uno es un viaje de red por leg en modo REMOTE.
- Sin `catalog.batch.enabled`, `CatalogApiRouter` repite el patrón N+1 aunque `CatalogClient` soporte lotes de 50
  códigos.
