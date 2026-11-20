# Informe de evidencia: ¿se puede apagar el camino local del catálogo? (M2 · C5, paso 1)

**Veredicto: no.** No alcanza la evidencia para apagar el código local, y hay consumidores que todavía lo necesitan. El
camino remoto nunca recibió tráfico sostenido: solo corrió unos 3 minutos al 100 %, durante dos simulacros de
rollback. Además, el mecanismo de rollback vuelve justamente al camino local.

**Cómo se reunió:** solo lectura, sin cambios en el código, el flag ni la base. Todas las horas están en UTC del
2026-11-18 (ART = UTC−3).

| Fuente | Ventana disponible |
|---|---|
| Prometheus (`localhost:9090`) | 13:07 → 13:54. El dato más viejo es `prometheus_tsdb_lowest_timestamp_seconds`, unos 47 min |
| Logs de `twa-flights-monolith` y `-2` | 13:39:34 / 13:39:40 → 13:43 (desde el rolling restart) |
| Logs de `twa-flights-catalog` | 13:21:57 → 13:42 |
| Flag y historial | Tablas `catalog_rollout` y `catalog_rollout_history`, leídas a las 13:54 |
| Documentos de la clase 4 | [`plan-monitoreo-rollback.md`](../clase-4-activa-progresivamente-el-nuevo-servicio/plan-monitoreo-rollback.md), [`observabilidad-rollout.md`](../clase-4-activa-progresivamente-el-nuevo-servicio/observabilidad-rollout.md), [`evidencia/`](../clase-4-activa-progresivamente-el-nuevo-servicio/evidencia/) |

Todo el tráfico es **sintético** (4 rutas). No hay tráfico real.

## 1. Estado del flag

| Dato | Valor | Fuente / ventana |
|---|---|---|
| Flag actual | `remote_percentage = NULL` → efectivo **0 %** (manda `CATALOG_MODE=LOCAL`) | `GET /catalog/rollout`, 13:54 |
| Tiempo con remoto > 0 % | Solo **13:12–13:13** y **13:41–13:42**, los dos simulacros | `max(catalog_rollout_remote_percentage)`, 1 h |
| Historial | 3 filas: 100 % (simulacro) → 0 % (guard, `CatalogRemoteErrorsCritical`) → NULL | `catalog_rollout_history` |
| Etapas 4a (5 %), 4b (25 %), 4c (50 %) y 5 (100 % durante 14 días) | **Nunca se ejecutaron** | Historial y Prometheus |
| Configuración por defecto | `CATALOG_MODE: LOCAL` en `docker-compose.yml:95` y `catalog.mode: LOCAL` en `application.yml:91` | Código |

La base no persiste entre corridas de compose, así que no puede haber historial anterior a esta corrida. Si lo hubo,
**no hay evidencia**.

## 2. Comparación antes y después, por señal

Ventana: la última hora (13:07–13:54). Las consultas son `increase(...[1h])` y `histogram_quantile(...[1h])`.

| Señal | Local | Remoto | Lectura |
|---|---|---|---|
| Llamadas al catálogo | 16.861 (9.122 individuales, 7.739 por lote) | 2.312, todas entre **13:12 y 13:14** | El remoto tiene unos 3 min de tráfico, todos dentro del simulacro |
| Errores | 0 | 43 (1,86 %), todos a las 13:14 | Son el corte provocado por el simulacro, no una medición en estado estable |
| Lote remoto con resultado OK | — | **0** (11 errores) | **Sin evidencia** de que `/cities?codes=` funcione con éxito por el camino remoto |
| p95 de una llamada | 1,7 ms | 4,6 ms | El remoto es 2,7× más lento, pero sigue debajo del umbral de 10 ms |
| p99 de una llamada | 2,9 ms | **105,7 ms** | Inflado por los timeouts del corte. **Sin evidencia** del p99 en estado estable (el umbral es ≤ 50 ms) |
| p95 de la búsqueda | 19,6 ms (las dos rutas mezcladas) | — | La búsqueda no se puede separar por camino (limitación 2 de la clase 4) |
| Línea base B | `search:latency_seconds:p95_baseline7d` = 17,1 ms | — | Se calcula con unos 45 min de historia, no con los 7 días que pide el plan |
| 503 de la búsqueda | 43 de 8.391 (0,51 %), todos a las 13:14 | — | Corresponden al simulacro |
| Rollbacks | `catalog_rollout_rollbacks_total` = 1 (`monolith-2`) | — | Es el simulacro de las 13:42 |
| Disponibilidad del servicio (≥ 99,9 % en 14 días) | — | — | **Sin evidencia**: `min_over_time(up[1h])` = 0 para el catálogo, porque se bajó a propósito |
| Trazas | — | — | **Sin evidencia**: no hay tracing (se sacaron OTel y Tempo en la clase 4) |
| Deriva de 40014 y de itinerarios descartados | — | — | **Sin evidencia**: no existen `search.catalog.outcome` ni `provider.itineraries.*` |

**Inconsistencia en la evidencia.** Durante el simulacro de las 13:41, el historial registra "18 remote calls, errors
100 %". Prometheus, en cambio, no muestra ninguna llamada remota en ese minuto: los targets estuvieron caídos
(`up` = 0). Prometheus no es una fuente completa durante los reinicios.

### Logs

| Contenedor | `ERROR` | Detalle | Ventana |
|---|---|---|---|
| `twa-flights-monolith` | 14 | 13 son "calculating the flight duration" de los proveedores (ya existían antes) y 1 es `Duplicate entry` en `cluster_search` (legacy, no es del catálogo) | 13:39–13:43 |
| `twa-flights-monolith-2` | 68 | 50 de `CatalogClient` (`/cities?codes=`) y 1 `ALERT` del guard, los dos del simulacro. El resto son de los proveedores y 1 `Duplicate entry` | 13:39–13:43 |
| `twa-flights-catalog` | 0 | — | 13:21–13:42 |

## 3. Casos límite

| Caso | Evidencia | Estado |
|---|---|---|
| Código desconocido (`not_found`) | Prometheus: 0 llamadas con `result=not_found` en los dos caminos. La prueba de humo pega directo al servicio (`/cities/XXX` → 404) | **Sin evidencia** a través del monolito en la ventana |
| Mayúsculas, minúsculas y letras de ancho completo | La suite corre en los dos modos (218/218, según `plan-monitoreo-rollback.md`). No se reprodujo | Solo documentado |
| Servicio caído | Los dos simulacros (13:12 y 13:41). El usuario ve 503 durante 13 s y después el flag vuelve al **camino local** | Verificado, y depende de que exista el código local |
| Caché encendida (99,8 % de aciertos) | Casi no hay llamadas remotas, así que el guard queda ciego (`CatalogRolloutGuardBlind`) | Riesgo abierto |
| Pico de tráfico (criterio de 4c) | — | **Sin evidencia** |

## 4. Consumidores pendientes del camino viejo

| # | Consumidor | Dónde | Por qué bloquea |
|---|---|---|---|
| 1 | `CatalogApiRouter` instancia `CatalogService` como camino `local` | `catalog/client/CatalogApiRouter.java:49-52` | Es el único consumidor directo en `src/main`. `AvailabilityRequestValidator` y `FlightHelper` usan la interfaz `CatalogApi`, así que no les afecta |
| 2 | Rollback: `CatalogRolloutGuard` y el `PUT` manual ponen el flag en **0 %**, y 0 % significa local | `CatalogRolloutFlag`, `CatalogMode` | Sin el código local, **no queda adónde hacer rollback** |
| 3 | El valor por defecto es `LOCAL` | `application.yml:91`, `docker-compose.yml:95` | Hoy todo el tráfico pasa por el camino local |
| 4 | **El servicio de catálogo lee `flights.city`, `country` y `continent`** | `twa-flights-catalog/src/main/resources/application.yml:38` (`DB_NAME:flights`); compose: "The reads are still from flights" | Las tablas del monolito siguen siendo la fuente del servicio. No se pueden archivar ni borrar |
| 5 | Flyway del monolito crea las tablas y el servicio solo las valida. Los triggers de `catalog_outbox` (V1.11) alimentan la copia en la base `catalog` y los eventos | Migraciones V1.0, V1.9–V1.12, compose | La base `catalog` es una copia y todavía no es la principal (339 eventos en el outbox; el último a las 13:21:51) |
| 6 | `city_replica` (V1.13) recibe los eventos, pero "la búsqueda no lee la réplica todavía" | `application.yml:129-130` | Esta réplica se puede usar como camino de reemplazo, pero hoy no está conectada |
| 7 | Tests: `CatalogServiceTest`, `CatalogServiceBatchTest`, `CatalogServiceUnavailableTest`, `CatalogApiRouterTest`, `CatalogUnavailableSearchTest` y `AbstractIntegrationTest` (la suite en los dos modos) | `twa-flights-monolith/src/test` | Hay que reescribirlos o borrarlos junto con el código |
| 8 | Observabilidad: B, `CatalogRemoteLatencyVsLocal` y `p95_baseline7d{path="local"}` comparan contra el camino local | `observability/prometheus/rules/*.yml` | Quedan sin referencia |
| 9 | El contrato de Microcks del catálogo se genera desde las semillas de Flyway del monolito (V1.1, V1.5) | `microcks/generate-contracts.py:15-16` | Si se mueven o archivan las tablas o las semillas, cambia el contrato |

## 5. Riesgos abiertos

1. **No hay ninguna etapa de canario medida.** Los criterios del plan piden 24 h a 5 %, 48 h a 25 %, 48 h a 50 % con
   un pico y 14 días a 100 %. Hay unos 3 min a 100 %, todos dentro de un simulacro.
2. **Apagar el código local elimina el rollback.** Hoy, el guard pasa una caída a 503 de 13 s y después recupera con
   el camino local. Sin el camino local, una caída del servicio es un 503 sostenido. La decisión 1 del plan de
   extracción no tiene fallback por pedido.
3. **El servicio no es independiente del monolito.** Lee la base `flights` y depende de su Flyway. Apagar el código
   local no reduce el acoplamiento de datos: primero hay que pasar las lecturas del servicio a la base `catalog`.
4. **El lote remoto no tiene ninguna llamada exitosa medida**, y el p99 remoto en estado estable no tiene evidencia.
5. **Las métricas de negocio no existen** (40014 y descartes). No se puede demostrar que el remoto devuelve lo mismo
   que el local. El modo sombra sigue bloqueado.
6. **La evidencia es frágil.** Hay solo 47 min de Prometheus, la base no persiste, todo el tráfico es sintético y
   Prometheus tiene huecos durante los reinicios.
7. **Del legacy, sin tocar:** `Duplicate entry` en `cluster_search` y los errores de duración de vuelo de los
   proveedores. Son anteriores y no tienen que ver con el catálogo.

## 6. Qué haría falta para cambiar el veredicto

- Correr 4a → 4b → 4c → 5 con tráfico representativo y guardar las exportaciones de Prometheus de cada ventana.
- Pasar las lecturas del servicio a la base `catalog` y sacarle la dependencia de Flyway del monolito.
- Definir el rollback sin el camino local: por ejemplo, con la réplica `city_replica` o una versión anterior del
  servicio.
- Instrumentar `search.catalog.outcome` y `provider.itineraries.*` y medir la deriva entre los dos caminos.
