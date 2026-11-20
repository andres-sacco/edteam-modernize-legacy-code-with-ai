# Revisión escéptica: ¿se puede avanzar de etapa? (M2 · C4)

Paso 3 de la clase. Revisión del [plan de monitoreo y rollback](plan-monitoreo-rollback.md) y del
[informe de investigación](informe-investigacion.md) contra el código y la evidencia guardada. Busca por qué una
etapa saldría mal: si el rollback se probó, si las alertas disparan antes de que el usuario note el problema y si se
compara la misma ventana.

## Cómo se revisó (2026-11-18)

- **Fuentes:** `plan-monitoreo-rollback.md`, `informe-investigacion.md`, los `analisis-*.json` de esta carpeta,
  `CatalogRolloutGuard`, `CatalogRolloutFlag`, `CatalogApiRouter`, `MeteredCatalogApi`, `application.yml`,
  `docker-compose.yml` y los tests del monolito.
- **No se cambió nada.** El stack de `docker compose` estaba apagado (`docker ps -a` no muestra `flights-db` ni
  `twa-flights-monolith`) y no se levantó, así que **no se reprodujo ninguna prueba en vivo**.
- Lo que el plan afirma sin un log, una captura o una consulta guardada queda como **"no verificado"**.

## Chequeos

| # | Chequeo | Evidencia | Estado | Severidad |
|---|---|---|---|---|
| 1 | Se probó el rollback automático | El plan dice "ALERT … flag en 0 % al segundo 38". No hay un log, una captura ni la salida de `catalog_rollout_history` guardados. El único test de integración apaga el guard (`AbstractIntegrationTest`, `catalog.rollout.guard.enabled=false`). `CatalogRolloutGuard` no tiene ningún test | **No verificado** (sólo lo declara el doc) | Alta |
| 2 | Se probó el rollback manual (PUT o SQL) | El plan dice "0 % aplicado en 4,8 s". No hay salida guardada. `CatalogApiRouterTest` sólo usa `CatalogRolloutFlag.fixed(mode)`: ningún test cubre un porcentaje ni el cambio en runtime | **No verificado** | Alta |
| 3 | El rollback se probó con 2 instancias | El plan, limitación 3, dice que hay una sola instancia. El prerrequisito P1 del plan de extracción está sin cumplir. El SQL "como otra instancia" corre en el mismo proceso | **Falla** | Alta |
| 4 | Las alertas disparan antes de que el usuario note el problema (caída) | Según el propio plan, el primer 503 llega al segundo **1** y el rollback al **38**. La limitación 1 acepta 503 durante ≤ 65 s. El usuario lo ve unos 37 s antes que el guard | **Falla** (con la evidencia del propio plan) | Alta |
| 5 | Las alertas disparan antes que el usuario (degradación de latencia) | A3 dispara con una media remota > **50 ms**, y la base es **3,5 ms** (14× la base). Sin lote, una búsqueda hace ~11 llamadas: hasta ~500 ms extra por búsqueda (≈ 10× B = 53 ms) sin que dispare nada. A4 (B + 20 %) no es ejecutable porque no hay Prometheus | **Falla** | Alta |
| 6 | El guard evalúa con el tráfico real del canario | Con `min-calls=20` cada 30 s y el flag en 5 %, hacen falta ~400 llamadas al catálogo por ventana. Con lote (≤ 4 por búsqueda) son ~3,3 búsquedas/s. No hay dato del tráfico real. Si el tráfico es menor, el guard nunca evalúa y queda ciego, y nada avisa de eso (sólo un log en `DEBUG`) | **No verificado** | Alta |
| 7 | "Sostenido" significa 2 ventanas **seguidas** | En `CatalogRolloutGuard.evaluate()`, una ventana con < 20 llamadas hace `return` sin reiniciar `errorBreaches`/`latencyBreaches`. Dos ventanas malas separadas por ventanas sin tráfico cuentan como seguidas | Desvío (lectura del código) | Baja |
| 8 | Alguien recibe las alertas (A6) | El plan dice que es "sólo log y métrica; no hay a quién notificar (sin alertmanager)" | **Falla** | Media |
| 9 | Se compara la misma ventana: línea base | En la tabla de línea base del plan, "Local (0 %)" y "Remoto (100 %)" son **dos corridas separadas** de 60 búsquedas. No son la misma ventana | **Falla** | Alta |
| 10 | Se compara la misma ventana: durante el canario | `catalog.lookup{path}` en Actuator da `COUNT`/`TOTAL_TIME` **acumulados desde el arranque**: el camino local arrastra la historia anterior al canario. Los percentiles salen de una ventana móvil corta, que no se puede alinear. El p95 de la búsqueda no se separa por camino (limitación 2). Sin P3 no hay ventanas de 5 min ni de 24/48 h | **Falla** | Alta |
| 11 | La línea base B es única y está medida | El informe da un p95 de 45,9 ms (cliente, `curl`); el plan usa B = 53 ms (servidor). Ninguna de las dos es la base de 7 días que pide el plan, y el plan obliga a medir B otra vez después de 1a/1b, que no se hizo | **Falla** (provisoria) | Media |
| 12 | Los archivos de análisis respaldan los números | `analisis-latencia-busqueda.json` y `analisis-errores-busqueda.json` pesan **0 bytes**. `analisis-metricas-disponibles.json` es anterior a `1fecf7f`: no contiene `catalog.lookup`, `catalog.rollout.*` ni los percentiles | **Sin evidencia** | Media |
| 13 | Dashboards | No hay ningún dashboard en el repo. Sólo se consulta `/actuator/metrics` a mano | **No existe** | Media |
| 14 | Prerrequisitos de la etapa (1a/1b, P1, P3) | La tabla del propio plan dice que la 4a no empieza antes de 1a/1b. Lote y caché siguen apagados (`CATALOG_BATCH_ENABLED:false`). P1 y P3 están sin cumplir. La etapa 3 (sombra) está bloqueada | **Falla** | Alta |

## Veredicto

**No se puede avanzar de etapa.** Ni a 1a/1b con B ya cerrada, ni mucho menos al canario 4a.

- **El rollback** existe en el código y su lógica es razonable, pero no hay ninguna evidencia guardada de que se haya
  probado y ningún test lo cubre.
- **Las alertas** llegan después que el usuario: unos 37 s de 503 en una caída, y en una degradación de latencia no
  disparan nunca antes de un impacto de ~10×.
- **La misma ventana** no se compara: la base sale de corridas separadas y las métricas de Actuator son acumuladas.

## Qué falta para destrabar

1. Repetir la prueba de rollback y guardar el log del `ALERT` y la salida de
   `SELECT * FROM catalog_rollout_history` en esta carpeta.
2. Bajar A3 a un umbral relativo a la base (por ejemplo, 3× la media remota) o medir la latencia por búsqueda.
3. Avisar cuando el guard está ciego (ventanas seguidas con menos de `min-calls`).
4. Cumplir P3 (Prometheus) para comparar `path=local` contra `path=remote` con `rate()` en la misma ventana, y armar
   el dashboard.
5. Cerrar 1a/1b y volver a medir B.

## Nota

En la máquina donde se hizo la revisión hay contenedores apagados de un proyecto `twa-observability` (Prometheus,
Grafana, Alertmanager). No forman parte de este repo, pero podrían ser el camino a P3 y a A6. No se tocaron.

## Seguimiento

Los cambios para cumplir estos chequeos, el estado de cada uno y el simulacro de rollback con evidencia están en
[observabilidad-rollout.md](observabilidad-rollout.md).
