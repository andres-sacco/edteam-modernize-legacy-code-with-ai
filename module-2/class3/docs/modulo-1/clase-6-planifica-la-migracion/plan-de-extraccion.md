# Plan de extracción: catálogo de ciudades (BC4)

Paso 2 de la clase 6. Es el plan aprobado para sacar el catálogo geográfico del monolito con **Strangler Fig** y
**Branch by Abstraction**. **No se migra nada en esta clase.** El plan se ejecuta en el módulo 2.

- **Base:** [informe de investigación](informe-investigacion.md) (revisado, con las decisiones 1, 2, 3 y 7 del equipo),
  [context map](../clase-3-que-partes-podemos-separar/context-map.md),
  [comportamientos a proteger](../clase-4-crea-una-red-de-seguridad/comportamientos-a-proteger.md) y
  [reglas de dependencias](../clase-5-prepara-el-monolito/reglas-de-dependencias.md).
- **Decisión de arquitectura:** [ADR-002](adr-002-extraccion-del-catalogo.md). El ADR también cierra las preguntas
  abiertas del informe que este plan necesita resolver.

> Convención de rutas: las clases están en `src/main/java/com/twa/flights/`. Se nombran por paquete y clase.

---

## 1. Principios del plan

1. **`CatalogApi` es la costura.** Los consumidores (C1 `AvailabilityRequestValidator`, C2 `FlightHelper` y los tres
   `Provider*Service`) siguen hablando con `CatalogApi`. Detrás hay dos implementaciones: `CatalogService` (local) y
   `CatalogClient` (remoto). Un flag elige cuál responde.
2. **Cada etapa se vuelve atrás con un flag**, sin redeploy y sin downtime. Las que se vuelven atrás con un rolling
   deploy (0, la propagación del 503 de la 1, 1c y 8) requieren P1, y la 8 además que la versión anterior siga
   desplegable y los datos sigan sincronizados.
3. **Las tablas no se borran en este plan.** El borrado es de M2 · C5, con su propia evidencia.
4. **Los criterios son numéricos y se miden con las mismas métricas en los dos caminos** (local y remoto), con el tag
   `path`. Sin la etapa 0 no hay línea base y no se puede avanzar.
5. **El comportamiento caracterizado no cambia**, salvo el cambio aprobado por el equipo: una caída del catálogo
   responde **503** y no 40014 (decisión 1). Ese cambio se documenta en los tests de caracterización #6 y #26 cuando
   se implemente.

### Prerrequisitos de plataforma (etapa P, antes de la etapa 0)

Hoy **no existe ninguno**: `docker-compose.yml` levanta una sola instancia y el `pom.xml` no tiene Spring Cloud
(`@RefreshScope`), ni `micrometer-registry-prometheus`, ni Caffeine. Todos los rollbacks "por flag en ≤ 5 min" de este
plan dependen de P1 y P2, así que no son una nota al margen: son una **etapa con criterio de cumplido**. Ninguna etapa
del roadmap empieza hasta que su prerrequisito esté cumplido y verificado.

| # | Prerrequisito | Por qué | Se necesita antes de | Criterio de cumplido (se verifica, no se declara) | Dueño |
|---|---------------|---------|----------------------|---------------------------------------------------|-------|
| P1 | El monolito corre con **al menos 2 instancias** detrás de un balanceador | Sin 2 instancias, cualquier deploy (etapas 0, 1c y 8) es downtime | Etapa 0 | Un rolling deploy de una versión sin cambios con tráfico de carga constante: **0** respuestas 5xx y **0** conexiones rechazadas durante el deploy. Revisado que `ClusterSearchCleanupJob` (`@Scheduled`) puede correr en las 2 instancias a la vez (hoy borra por fecha de vencimiento; se confirma que el borrado doble no da error ni deadlock) | Plataforma |
| P2 | Los flags se cambian **en runtime**, sin reiniciar (por ejemplo, `@RefreshScope` + `/actuator/refresh`, o un proveedor de flags) | El rollback por flag tiene que tardar minutos, no un deploy | Etapa 1 | Se cambia un flag de prueba y las **2** instancias lo leen en ≤ **60 s**, sin reinicio; queda registrado quién lo cambió y cuándo; solo el dueño de cada flag (sección 4) tiene permiso | Plataforma |
| P3 | Las métricas se exportan a un backend con historia (por ejemplo, `micrometer-registry-prometheus` + Grafana) | `/actuator/metrics` solo da el valor actual. Los criterios usan percentiles y ventanas de tiempo | Etapa 0 | Retención ≥ **30 días**; se puede consultar el p95 de `http.server.requests{uri=/clusters/itineraries}` en una ventana de 5 min, separado por instancia | Plataforma |
| P4 | Las alertas de la sección 4 están configuradas | El rollback se dispara por alerta, no por alguien mirando un dashboard | Etapa 3 (las de catálogo vacío y 503, antes de la etapa 1) | Cada alerta se dispara **una vez en un simulacro** (por ejemplo, bajando el servicio en un entorno de prueba) y llega a la guardia del dueño de la etapa | Dueño de cada etapa |

**La herramienta de flags (P2) se elige antes de la etapa 1**, no en M2 · C4. Si la decisión de M2 · C4 llega después,
las etapas 1 a 7 esperan: no se empieza con un mecanismo provisorio que después haya que migrar en medio del plan.

**Rollback de la etapa P:** no aplica (es infraestructura sin tráfico del catálogo). Si P1 falla en la verificación, el
monolito sigue con 1 instancia y el plan no arranca.

---

## 2. Métricas

### 2.1 Métricas que ya existen (actuator)

| Métrica | Tags útiles | Para qué |
|---------|-------------|----------|
| `http.server.requests` | `uri=/clusters/itineraries`, `status`, `outcome` | Latencia de la búsqueda (p95, p99), tasa de 4xx y 5xx |
| `hikaricp.connections.active`, `hikaricp.connections.pending` | `pool` | Presión sobre la base (hoy el catálogo hace una consulta por `getCity`) |
| `jvm.threads.live`, `jvm.memory.used` | — | Efecto de la caché y de los hilos del cliente HTTP |
| `http.client.requests` | `client.name`, `uri`, `status`, `outcome` | Aparece cuando el `CatalogClient` use un `RestTemplate`/`RestClient` construido por Spring |

### 2.2 Métricas nuevas (se agregan en la etapa 0, en los dos caminos)

| Métrica | Tipo | Tags | Qué mide |
|---------|------|------|----------|
| `catalog.lookup` | Timer | `path=local\|remote`, `operation=single\|batch`, `result=found\|not_found\|error` | Cada consulta a `CatalogApi`, en cualquiera de los dos caminos |
| `catalog.lookup.codes` | Distribution summary | `path` | Cantidad de códigos por consulta en lote |
| `cache.gets`, `cache.size`, `cache.evictions` | Caffeine con `recordStats` | `cache=catalog-cities`, `result=hit\|miss` | Efectividad de la caché del consumidor |
| `catalog.shadow.comparisons` | Counter | `result=match\|mismatch\|remote_error`, `field` | Resultado de la comparación en sombra (etapa 3) |
| `search.catalog.outcome` | Counter | `outcome=ok\|not_found_40014\|unavailable_503` | Qué le pasó al pedido en la validación de C1 |
| `provider.itineraries.discarded` | Counter | `provider=alpha\|beta\|gamma`, `reason=duration_negative\|city_not_found\|catalog_unavailable\|other` | Hoy el descarte solo deja un `LOGGER.error` en `Provider*Service.calculateFlightDuration`. Es la señal del riesgo más grave (200 con lista vacía) |
| `provider.itineraries.received` | Counter | `provider` | Denominador de la tasa de descarte |

En el servicio de catálogo: `http.server.requests` (por `uri`), `hikaricp.*` de su propia base, `catalog.admin.changes`
(Counter, `operation=create\|update\|delete`) y, desde la etapa 7, `catalog.outbox.pending` (Gauge, filas sin aplicar)
y `catalog.outbox.lag` (Timer, de `created_at` a `applied_at`).

### 2.3 Línea base (B)

La etapa 0 mide durante **7 días** en el monolito actual: p95 y p99 de la búsqueda, tasa de 5xx, tasa de 40014, tasa
de descarte por proveedor y `hikaricp.connections.pending`. Los umbrales relativos de la sección 3 se expresan contra
esa línea base, con la notación **B(métrica)**.

---

## 3. Roadmap

### Etapa 0 · Línea base y observabilidad

- **Qué:** con P1 y P3 ya cumplidos (etapa P), agregar las métricas de 2.2 al camino local y medir la línea base B
  durante 7 días.
- **Costo de las métricas nuevas:** no se puede comparar contra "los 7 días anteriores" porque antes de P3 no hay
  historia. Se mide con una **prueba de carga** sobre el mismo stack, antes y después del commit de las métricas
  (misma carga, 3 corridas de cada lado).
- **Cambio de comportamiento:** ninguno. Solo se agregan contadores y timers.
- **Rollback:** revertir el commit. No hay flag porque nada cambia para el cliente.

### Etapa 1 · Endurecer el contrato dentro del monolito

Se prepara `CatalogApi` para un consumidor remoto, todavía con la implementación local.

1. Agregar `getCities(Collection<String> codes)` a `CatalogApi` (decisión 2). Devuelve un mapa código → `CityDTO`; los
   códigos que no existen no aparecen en el mapa.
2. C1 valida `from` y `to` con **una** llamada en lote. C2: cada `Provider*Service` junta los códigos de todos sus
   itinerarios y hace **una** llamada en lote antes de calcular las duraciones.
3. Normalizar el código a mayúsculas en `CatalogService` (decisión 3), sin depender de la collation de MySQL.
4. Agregar `CatalogUnavailableException` y hacer que **llegue al cliente** como 503 (decisión 1 y ADR-002, D4).
   Hoy hay cuatro lugares que se la tragarían, y los cuatro cambian en el mismo commit:

   | # | Dónde | Qué hace hoy | Qué hace después |
   |---|-------|--------------|------------------|
   | a | `search.validator.AvailabilityRequestValidator` · `validateCodeWithCatalog` | `catch (Exception)` → 40014 | Relanza `CatalogUnavailableException`; el resto sigue en 40014 |
   | b | `provider.service.Provider*Service` · `calculateFlightDuration(ItineraryDTO)` (los tres) | `catch (Exception)` → descarta el itinerario y deja un `LOGGER.error` | Relanza `CatalogUnavailableException`; el resto sigue descartando (ciudad inexistente, duración negativa) |
   | c | `search.service.ItinerariesSearchService` · `makeProviderCalls` | `catch (Exception)` → lista vacía del proveedor | Relanza `CatalogUnavailableException`; el resto (falla del proveedor) sigue dando lista vacía |
   | d | `search.service.ItinerariesSearchService` · `joinResults` | `catch (TWAException \| ExecutionException)` → log | Si la causa de la `ExecutionException` es `CatalogUnavailableException`, la relanza (cancela los otros `Future`) |

   Un `@ExceptionHandler` la convierte en **503** con un código de error propio. El camino **local** también la
   lanza: `CatalogService` convierte `DataAccessException` (base caída, timeout de Hikari) en
   `CatalogUnavailableException`, así los dos caminos responden lo mismo ante la misma falla.

   **Condición para cerrar la etapa 1:** un test de integración con el catálogo caído (en el camino local, con la base
   del catálogo inaccesible) que compruebe que la búsqueda responde **503** y **no** 200 con lista vacía, y que
   `provider.itineraries.discarded{reason=catalog_unavailable}` queda en 0. Se agrega junto con la actualización de
   los tests #6 y #26.
5. Caché del consumidor (Caffeine) delante de `CatalogApi`: TTL 60 s, máximo 1.000 entradas, sin caché de los
   códigos inexistentes (ADR-002, D5).
6. Nueva migración `V1.8__unique_city_code.sql` con un índice `UNIQUE` sobre `city.code`. Antes de aplicarla se
   verifica que los 86 códigos son únicos y están en mayúsculas.

- **Flags:** `catalog.batch.enabled` (default `false`) y `catalog.cache.enabled` (default `false`). Se encienden por
  separado. La propagación del 503 (punto 4) **no** tiene flag: es la corrección del riesgo más grave y tiene que
  estar activa antes de que el camino remoto responda (etapa 4). Su rollback es revertir el commit (rolling deploy,
  ≤ 15 min).
- **Rollback:** apagar el flag. El índice `UNIQUE` se revierte con una migración nueva que lo borra (no se edita la
  V1.8). En MySQL 8, crear o borrar ese índice sobre 86 filas es online (`ALGORITHM=INPLACE, LOCK=NONE`).

### Etapa 2 · Servicio de catálogo desplegado sin tráfico

- **Qué:** desplegar `twa-catalog-service` con al menos 2 instancias. Lee las tablas `continent`, `country` y `city`
  de la **misma base** `flights` con un usuario de **solo lectura** (estrategia Strangler con la misma base; ADR-002,
  D2). Publica:
  - `GET /api/catalog/cities?codes=BUE,MIA` → lista de ciudades (las que no existen no aparecen).
  - `GET /api/catalog/cities/{code}` → ciudad o 404.
  - El JSON reproduce la forma actual de `CityDTO` (`code`, `name`, `timeZone`, `country{code, name,
    continent{code, name}}`) sin depender del kernel `shared`.
- **Contrato para tests:** un mock del servicio en Microcks, generado con `microcks/generate-contracts.py` y con los
  datos de `src/test/resources/catalog/cities.csv`.
- **Verificación de datos:** un job compara las 86 filas del servicio contra `cities.csv` y contra `getCity` local.
- **Flag:** `catalog.mode=local` (default). El monolito no llama al servicio.
- **Rollback:** dar de baja el servicio. El monolito no se entera.

### Etapa 3 · Sombra (shadow)

- **Qué:** el monolito responde con `CatalogService` y además llama al `CatalogClient` en un pool aparte, con
  timeout de 300 ms, y compara `code`, `timeZone`, `country.code` y `continent.code`. El resultado remoto **se
  descarta siempre**.
- **Flag:** `catalog.mode=shadow` y `catalog.shadow.sample-rate` (0.0 a 1.0; arranca en `0.10` y sube a `1.0`).
- **Incluye a propósito:** códigos en minúsculas, códigos inexistentes (`XXX`), de 2 letras (`MI`) y los 7 hubs de
  Microcks (`PTY, LIM, BOG, MIA, SAO, SCL, MEX`).
- **Rollback:** `catalog.mode=local`.

### Etapa 4 · Canario del camino remoto

- **Qué:** un porcentaje de las **búsquedas** usa el `CatalogClient` para C1 y C2. La decisión se toma una vez por
  pedido con un hash determinístico de `from`, `to` y `departure`, así C1 y C2 de una misma búsqueda no mezclan los dos
  caminos. Como la paginación vuelve a validar con los mismos parámetros (comportamiento #31), las páginas siguen el
  mismo camino que la búsqueda original.
- **Sin fallback automático por pedido:** si el servicio no responde, la búsqueda responde 503 (decisión 1). Volver
  al camino local es un rollback por flag, no un `try/catch`, para que las métricas no escondan la falla.
- **Flag:** `catalog.mode=remote` y `catalog.remote.percentage`: **5 → 25 → 50 → 100**.
- **Rollback:** `catalog.remote.percentage=0` (o `catalog.mode=local`).

### Etapa 5 · 100 % remoto con el camino local listo

- **Qué:** `catalog.remote.percentage=100` durante al menos **14 días**. `CatalogService` sigue en el monolito como
  rollback.
- **Rollback:** `catalog.remote.percentage=0`.

### Etapa 6 · ABM de ciudades en el servicio

- **Qué:** se habilitan `POST`, `PUT` y `DELETE` sobre `/api/catalog/cities` en el servicio, con `X-Api-Key`
  (decisión 7). Reglas (ADR-002, D6):
  - El código es **inmutable**: no se puede cambiar con `PUT`.
  - El borrado es **lógico** (`active=false`). Una ciudad inactiva responde como inexistente. Como el índice `UNIQUE`
    de la etapa 1c incluye a las inactivas, un código desactivado **no** se vuelve a dar de alta con `POST`: se
    reactiva con `PUT` (`active=true`).
  - No se pueden borrar ni desactivar los 7 hubs. La lista vive en la configuración del servicio
    (`catalog.protected-codes`) y un chequeo del build compara esa lista con los hubs de `generate-contracts.py`.
  - Solo se escribe `city`. `country` y `continent` siguen siendo de solo lectura. Las entidades del servicio **no**
    usan `cascade = ALL` (riesgo 9).
- **Identificador de las ciudades nuevas:** hoy `city.id` es `bigint not null` **sin** `AUTO_INCREMENT`
  (`V1.0__create_catalog_tables.sql`) y `shared.model.Base` no tiene `@GeneratedValue`: las 86 filas tienen ids
  manuales (1 a 86). La misma migración que agrega `active` pasa `city.id` a `AUTO_INCREMENT` (arranca en 87). El
  servicio usa `@GeneratedValue(strategy = IDENTITY)`; el monolito nunca inserta ciudades, así que su entidad no
  cambia (`ddl-auto: validate` no lo exige). Ninguna tabla tiene una FK hacia `city.id`. En MySQL 8 ese `ALTER` copia
  la tabla (`ALGORITHM=COPY`): con 86 filas tarda milisegundos y bloquea solo escrituras, que en este momento no
  existen. Ningún cliente del ABM manda el `id`: si lo manda, el servicio responde 400.
- **Orden de los deploys** (cada paso se verifica antes del siguiente; el flag se enciende al final):

  | Paso | Qué | Dónde | Verificación antes de seguir |
  |------|-----|-------|------------------------------|
  | 6.1 | Migración nueva del monolito (siguiente `V1.<n>` libre): columna `active` con default `true` y `city.id` con `AUTO_INCREMENT`. El monolito sigue siendo dueño del `flyway_schema_history` hasta la etapa 7 | Monolito (rolling deploy) | La migración figura como aplicada en `flyway_schema_history`; las 86 filas tienen `active=true`; `SHOW CREATE TABLE city` muestra `AUTO_INCREMENT=87` |
  | 6.2 | `CatalogService` local filtra `active=false` (`getCity` y `getCities`) | Monolito (rolling deploy) | Las **2** instancias corren la versión nueva (`/actuator/info`); con todas las filas en `true` el comportamiento no cambia (smoke test y suite) |
  | 6.3 | El servicio filtra `active=false` en sus lecturas y trae el ABM apagado | Servicio | Paridad 86/86 contra el camino local |
  | 6.4 | Permiso de escritura **solo** sobre `city` para el usuario del servicio | Base | Un `UPDATE` sobre `country` con ese usuario falla |
  | 6.5 | `catalog.admin.enabled=true` | Flag del servicio | Criterios de la sección 4 |

  **Si 6.2 no está en todas las instancias, 6.5 no se enciende.** Sin el filtro en el camino local, un rollback a
  `catalog.remote.percentage=0` resucitaría las ciudades desactivadas.
- **Flag:** `catalog.admin.enabled` (default `false`) en el servicio.
- **Rollback:** `catalog.admin.enabled=false`. Las ciudades creadas o desactivadas quedan en la tabla compartida y el
  camino local las ve igual (con el filtro de 6.2), así que volver a `catalog.remote.percentage=0` no pierde ni
  resucita datos. La migración de 6.1 no se revierte: la columna con default `true` y el `AUTO_INCREMENT` no cambian
  el comportamiento del monolito.

### Etapa 7 · Base propia del servicio

- **Qué:** el servicio pasa a su base `catalog`, con su propio Flyway (un baseline nuevo con el esquema y la foto de
  los datos). Las migraciones V1.0, V1.1 y V1.5 del monolito **no se editan**.
- **Por qué no una doble escritura directa:** escribir en `catalog` y después en `flights.city` desde el mismo pedido
  no es atómico (no hay transacción entre dos bases). Si la segunda escritura falla, `flights.city` queda atrasada y
  justo esa es la base a la que vuelve el rollback. Por eso la copia hacia `flights.city` se hace con un **outbox**.
- **Cómo se sincroniza:**
  - **Una sola primaria de escritura: `catalog`.** El ABM escribe la fila de `city` y una fila en
    `catalog.city_outbox` (`id`, `city_id`, `operation`, `payload`, `created_at`, `applied_at`) **en la misma
    transacción**. Si la transacción falla, el ABM responde 5xx y no queda nada escrito en ninguna base.
  - **Un relay** (dentro del servicio, una sola instancia activa con un lock en la base) lee las filas con
    `applied_at` nulo en orden de `id` y las aplica en `flights.city` con un **upsert por `id`** (idempotente: aplicar
    dos veces da el mismo resultado). Reintenta con backoff y no avanza a la fila siguiente hasta aplicar la actual.
  - **Mismos ids en las dos bases.** La foto copia `continent`, `country` y `city` **con sus ids**, y la tabla `city`
    de `catalog` arranca con el mismo `AUTO_INCREMENT` que `flights.city` (etapa 6). El relay escribe el `id` y el
    `country_id` que asignó `catalog`, así las FK de `flights.city` siguen apuntando al mismo país. `country` y
    `continent` no se escriben en ninguna de las dos bases; el checksum diario los compara en las dos.
  - **Reconciliación:** el job de paridad compara filas y hash de `(id, code, name, time_zone, country_id, active)`.
    Ante una diferencia, la fuente de verdad es `catalog`: el job la reencola en el outbox (no corrige a mano).
- **Pasos:**

  | Paso | Qué | Verificación antes de seguir |
  |------|-----|------------------------------|
  | 7.1 | `catalog.admin.enabled=false` (ABM congelado) | `catalog.admin.changes` en 0 durante 5 min |
  | 7.2 | Crear la base `catalog` y cargar la foto con los ids | Paridad 100 % (filas y hash) entre las dos bases |
  | 7.3 | `catalog.datasource.write=own` (primaria `catalog` + outbox + relay) y `catalog.admin.enabled=true` | Un alta de prueba aparece en `flights.city` en ≤ **5 s** |
  | 7.4 | 7 corridas diarias de paridad al 100 % con el ABM abierto | Atraso del relay ≤ 5 s en el p99; 0 filas del outbox con más de 60 s sin aplicar |
  | 7.5 | `catalog.datasource.read=own` | Criterios de la sección 4 |

- **Flags:** `catalog.datasource.write=shared|own` y `catalog.datasource.read=shared|own` (en el servicio). Mientras
  `write=shared` (hasta 7.3) el ABM sigue escribiendo directo en `flights.city` como en la etapa 6.
- **Rollback de las lecturas (después de 7.5):** `catalog.datasource.read=shared`, **solo** con el outbox vacío. El
  procedimiento es: (1) `catalog.admin.enabled=false`; (2) esperar `catalog.outbox.pending=0`; (3) correr la paridad;
  (4) `catalog.datasource.read=shared`. Con el ABM cerrado no entran escrituras nuevas, así que `flights.city` no puede
  quedar atrasada después del cambio. El ABM se vuelve a abrir cuando se decide en qué base sigue la primaria.
- **Rollback completo de la etapa (volver a la base compartida como primaria):** los pasos 1 a 3 anteriores y después
  `catalog.datasource.write=shared` y `catalog.datasource.read=shared`. La base `catalog` queda sin uso (no se borra
  hasta decidir de nuevo).
- **Si el relay no puede aplicar una fila** (por ejemplo, la base `flights` caída): no se pierde, queda en el outbox y
  se reintenta. La acción de la alerta `CatalogOutboxLag` es cerrar el ABM (`catalog.admin.enabled=false`) para que el
  atraso no siga creciendo, y la etapa 8 no avanza mientras haya filas pendientes.
- El outbox y el relay se mantienen hasta M2 · C5.

### Etapa 8 · Retirar el camino local del monolito

- **Qué:** borrar `CatalogService`, `CityRepository`, las entidades y el mapper de `catalog` en el monolito.
  `CatalogApi` queda con una sola implementación: `CatalogClient`. Las tablas **no** se borran.
- **Flag:** ninguno (es borrar código). Se habilita solo con la etapa 7 estable, el relay encendido y
  `catalog.outbox.pending=0`.
- **Rollback:** redeploy de la versión anterior del monolito (artefacto conservado 30 días), que sigue leyendo
  `flights.city` al día gracias al outbox. Es un rolling deploy (P1): sin downtime.

---

## 4. Tabla de etapas

Ventana de evaluación: **5 minutos** salvo que se indique otra. "Sostenido" = el umbral se rompe en **2 ventanas
seguidas**. Todos los porcentajes de la búsqueda se miden sobre `uri=/clusters/itineraries`.

| # | Etapa | Flag (valor) | Dueño del flag | Métricas | Criterio de avance | Criterio de rollback | Cómo se vuelve atrás | Tiempo de rollback |
|---|-------|--------------|----------------|----------|--------------------|----------------------|----------------------|--------------------|
| P | Plataforma | — | Plataforma | Las de cada criterio de cumplido | P1 a P4 cumplidos con los criterios de la tabla de prerrequisitos; herramienta de flags elegida | — | No aplica | — |
| 0 | Línea base | — | Equipo Búsqueda (BC1) | Todas las de 2.1 y 2.2 con `path=local` | 7 días de datos; ≥ 10.000 búsquedas medidas; las métricas nuevas no suben el p95 más de **2 %** en la prueba de carga antes/después | p95 de la búsqueda sube > 2 % | Revertir el commit | ≤ 15 min (rolling deploy) |
| 1 | 503 del catálogo | — (código) | Equipo Búsqueda (BC1) | `search.catalog.outcome`, `provider.itineraries.discarded{reason=catalog_unavailable}`, 5xx | Test de integración con el catálogo caído: 503 y no 200 vacío; `catalog_unavailable` = 0 en 48 h | Cualquier 503 por catálogo con la base sana; cualquier `catalog_unavailable` > 0 | Revertir el commit | ≤ 15 min (rolling deploy) |
| 1a | Lote | `catalog.batch.enabled=true` | Equipo Búsqueda (BC1) | `catalog.lookup` (operation), `hikaricp.connections.active`, p95 búsqueda, `search.catalog.outcome`, `provider.itineraries.discarded` | 48 h con: consultas al catálogo por búsqueda ≤ **4** (1 de C1 + 1 por proveedor); p95 ≤ **B**; tasa de 40014 = **B ± 0,1 pp**; tasa de descarte = **B ± 0,5 pp** | Tasa de 40014 o de descarte fuera de rango, sostenido; o cualquier 5xx nuevo con `CatalogApi` en el stack | Flag a `false` | ≤ 5 min |
| 1b | Caché | `catalog.cache.enabled=true` | Equipo Búsqueda (BC1) | `cache.gets` (hit/miss), `cache.size`, `catalog.lookup` | 48 h con: hit ratio ≥ **95 %** después de 10 min de calentamiento; `cache.size` ≤ 1.000; 40014 = **B ± 0,1 pp** | Hit ratio < 80 % sostenido; o `jvm.memory.used` > **B + 10 %** | Flag a `false` | ≤ 5 min |
| 1c | Índice único | Migración V1.8 | Equipo Catálogo (BC4) | Resultado de Flyway, `catalog.lookup{result=error}` | Migración aplicada; 0 errores de `catalog.lookup` en 24 h | Falla la migración o aparecen errores de `catalog.lookup` | Migración nueva que borra el índice | ≤ 15 min |
| 2 | Servicio sin tráfico | `catalog.mode=local` | Equipo Catálogo (BC4) | `http.server.requests` del servicio, job de paridad | 2 instancias `UP` durante 72 h; job de paridad **86/86** iguales en ≥ 3 corridas; p99 de `GET /cities?codes=` (10 códigos) ≤ **30 ms** en prueba de carga a **200 req/s** | Paridad < 86/86 | Dar de baja el servicio | Inmediato (sin tráfico) |
| 3 | Sombra | `catalog.mode=shadow`, `catalog.shadow.sample-rate` 0.10 → 1.0 | Equipo Catálogo (BC4) | `catalog.shadow.comparisons`, `http.client.requests{client.name=catalog}`, p95 búsqueda | ≥ **72 h** y ≥ **10.000** comparaciones con sample 1.0; **0** `mismatch`; `remote_error` ≤ **0,1 %**; p99 remoto ≤ **50 ms**; p95 búsqueda ≤ **B + 5 %** | 1 `mismatch` (se investiga antes de seguir); `remote_error` > 1 % sostenido; p95 búsqueda > B + 5 % | `catalog.mode=local` | ≤ 5 min |
| 4a | Canario 5 % | `catalog.mode=remote`, `catalog.remote.percentage=5` | Equipo Búsqueda (BC1) | Comparación canario vs. control en la misma ventana: p95/p99 búsqueda, 5xx, `search.catalog.outcome`, `provider.itineraries.discarded`, `http.client.requests` | 24 h y ≥ **2.000** búsquedas en el canario; p95 ≤ control **+ 10 %** y ≤ control + 50 ms; 503 por catálogo ≤ **0,1 %**; 5xx total ≤ **0,5 %**; 40014 = control **± 0,1 pp**; descarte = control **± 0,5 pp**; `catalog_unavailable` en descartes = **0** | p95 > control + 20 % sostenido; 503 por catálogo > **0,5 %** sostenido; 5xx > 1 % sostenido; 40014 o descarte fuera de rango sostenido; **cualquier** búsqueda 200 con lista vacía atribuible al catálogo | `catalog.remote.percentage=0` | ≤ 5 min |
| 4b | Canario 25 % | `catalog.remote.percentage=25` | Equipo Búsqueda (BC1) | Igual que 4a | 48 h y ≥ **10.000** búsquedas en el canario; mismos umbrales que 4a; `hikaricp.connections.pending` del servicio = 0 en el p99 | Igual que 4a | `catalog.remote.percentage=0` | ≤ 5 min |
| 4c | Canario 50 % | `catalog.remote.percentage=50` | Equipo Búsqueda (BC1) | Igual que 4a | 48 h; mismos umbrales que 4a; incluye un pico de tráfico (el p99 de req/s de la semana de B) | Igual que 4a | `catalog.remote.percentage=0` | ≤ 5 min |
| 5 | 100 % remoto | `catalog.remote.percentage=100` | Equipo Búsqueda (BC1) | Igual que 4a, ahora contra **B** | 14 días; p95 ≤ **B + 10 %**; disponibilidad del servicio ≥ **99,9 %**; 503 por catálogo ≤ **0,1 %**; 1 prueba de caída controlada de una instancia del servicio sin 503 | Igual que 4a, contra B | `catalog.remote.percentage=0` | ≤ 5 min |
| 6 | ABM | `catalog.admin.enabled=true` (después de 6.1 a 6.4) | Equipo Catálogo (BC4) | `catalog.admin.changes`, `http.server.requests` del ABM, `cache.gets`, `catalog.lookup` | 6.1 a 6.4 verificados; 14 días; 0 errores 5xx del ABM; un alta de ciudad se ve en la búsqueda en ≤ **60 s** (TTL de la caché); una baja lógica deja de verse en los **dos** caminos; 0 cambios en `country`/`continent` (`checksum` diario) | Cualquier cambio en `country` o `continent`; un 5xx en el ABM sostenido; una ciudad dada de alta no visible a los 120 s; una ciudad inactiva visible en cualquier camino | `catalog.admin.enabled=false` | ≤ 5 min |
| 7 | Base propia | `catalog.datasource.write=own` (con outbox), luego `catalog.datasource.read=own` | Equipo Catálogo (BC4) | Paridad (filas + hash), `catalog.outbox.pending`, `catalog.outbox.lag`, `catalog.lookup` p99, `hikaricp.*` de las dos bases | Paridad 100 % en 7 corridas diarias seguidas y `catalog.outbox.lag` p99 ≤ **5 s** antes de cambiar las lecturas; después, 14 días con paridad 100 % y p99 de `catalog.lookup` remoto ≤ **50 ms** | 1 diferencia de paridad que la reconciliación no corrige en 1 h; outbox con filas sin aplicar > 60 s | ABM cerrado → outbox vacío → paridad → `catalog.datasource.read=shared` | ≤ 15 min |
| 8 | Retirar el camino local | — (código) | Equipo Búsqueda (BC1) | Igual que la etapa 5 y `catalog.outbox.pending` | 30 días con los umbrales de la etapa 5 y el outbox vacío al cierre de cada día → listo para M2 · C5 | Igual que la etapa 5 | Redeploy de la versión anterior | ≤ 15 min (rolling deploy) |

### Alertas que disparan el rollback (P4)

| Alerta | Condición | Acción |
|--------|-----------|--------|
| `CatalogSearchEmptyByCatalog` | `provider.itineraries.discarded{reason=catalog_unavailable}` > 0 en 5 min | Rollback inmediato de la etapa activa. Es el riesgo más grave del informe |
| `CatalogUnavailable503` | 503 por catálogo > 0,5 % de las búsquedas, 2 ventanas | Rollback de la etapa activa |
| `Catalog40014Drift` | Tasa de 40014 fuera de B ± 0,1 pp (o control ± 0,1 pp en el canario), 2 ventanas | Rollback y comparar los códigos rechazados entre caminos |
| `CatalogLatency` | p95 de la búsqueda > B + 20 %, 2 ventanas | Rollback de la etapa activa |
| `CatalogShadowMismatch` | `catalog.shadow.comparisons{result=mismatch}` > 0 | No avanzar. Se investiga el `field` |
| `CatalogParity` | Job de paridad con diferencias que la reconciliación no corrige en 1 h | Rollback de la etapa 7 |
| `CatalogOutboxLag` | `catalog.outbox.pending` > 0 con una fila de más de 60 s | Cerrar el ABM (`catalog.admin.enabled=false`) y revisar el relay. La etapa 8 no avanza |

---

## 5. Cómo se cubren los riesgos del informe

| Riesgo del informe | Dónde se cubre |
|--------------------|----------------|
| 1. Contrato "`null` + NPE atrapada" | Etapa 1, punto 4 (`CatalogUnavailableException` → 503, con los cuatro `catch` que la tragaban); test con el catálogo caído; alertas `CatalogUnavailable503` y `CatalogSearchEmptyByCatalog` |
| 2. Latencia N+1 | Etapa 1 (lote y caché); criterio "≤ 4 consultas por búsqueda" |
| 3. Mayúsculas por collation | Etapa 1 (normalización); sombra con códigos en minúsculas |
| 4. Orden de validación (40014 antes de 40012) | La validación sigue siendo síncrona y en el mismo lugar; `ClustersValidationTest.severalErrors` corre en las etapas 1 y 4 |
| 5. Longitud del código (`MI`, columna de 4) | El servicio no valida formato: un código que no existe simplemente no aparece. Sombra con `MI` |
| 6. Código sin `UNIQUE` | Etapa 1c (índice `UNIQUE`) |
| 7. Datos semilla en un Flyway compartido | Etapa 7 (baseline nuevo en la base propia, sin editar V1.0/V1.1/V1.5) |
| 8. tzdata y Joda-Time | El servicio devuelve el **id** de la zona (`America/Argentina/Buenos_Aires`), no offsets. El cálculo de la duración sigue en el monolito con su tzdata |
| 9. `cascade = ALL` | Etapa 6: el servicio no usa cascada y solo escribe `city`; checksum diario de `country`/`continent` |
| 10. Tests acoplados a MySQL | Etapa 2: mock de Microcks y `cities.csv` como contrato de datos |
| 11. Kernel compartido | Etapa 2: el JSON reproduce la forma de `CityDTO` sin depender de `shared` |

## 6. Lo que queda fuera

- Borrar `continent`, `country` y `city` de la base `flights` y apagar el outbox y el relay (M2 · C5).
- Invalidar la caché con un evento de "ciudad modificada" en lugar del TTL (M2 · C3, si el TTL de 60 s no alcanza).
- Revisar Bariloche (`BRC`) con `America/Argentina/Salta` (rareza del informe; es un dato, no parte de la migración).
- Cambiar la zona horaria a un dato del proveedor (pregunta 4 del ADR-001).

## 7. Ajustes por la revisión escéptica

La [revisión escéptica](revision-esceptica-plan.md) del plan contra el código, la suite de tests y los datos semilla
encontró cuatro riesgos altos. Esta versión del plan los corrige; los riesgos medios y bajos de esa revisión siguen
abiertos.

| Riesgo alto | Evidencia | Cambio en el plan |
|-------------|-----------|-------------------|
| La `CatalogUnavailableException` se pierde en C2 y la búsqueda responde 200 con lista vacía | `catch (Exception)` en `Provider*Service.calculateFlightDuration`, en `ItinerariesSearchService.makeProviderCalls` y en `joinResults` | Etapa 1, punto 4: los cuatro `catch` cambian en el mismo commit, el camino local también lanza la excepción y un test con el catálogo caído es condición de cierre. Fila 1 en la tabla de etapas |
| Los prerrequisitos P1 a P3 no existen y los rollbacks dependen de ellos | `docker-compose.yml` con una sola instancia; `pom.xml` sin Spring Cloud, Prometheus ni Caffeine | Etapa P con criterio de cumplido por prerrequisito; la herramienta de flags se elige antes de la etapa 1; la etapa 0 mide su costo con una prueba de carga |
| El ABM no tiene cómo asignar ids y el orden de los deploys no está definido | `city.id` sin `AUTO_INCREMENT` (`V1.0__create_catalog_tables.sql`); `Base` sin `@GeneratedValue` | Etapa 6: `AUTO_INCREMENT` en la misma migración que `active`, pasos 6.1 a 6.5 con verificación y el flag al final; reactivación con `PUT` |
| La doble escritura no es atómica y el rollback vuelve a la base atrasada | Etapa 7 de la versión anterior (dos escrituras sin transacción común) | Etapa 7: primaria única `catalog` con outbox en la misma transacción, relay idempotente con los mismos ids, reconciliación, y rollback solo con el ABM cerrado y el outbox vacío. Alerta `CatalogOutboxLag` |
