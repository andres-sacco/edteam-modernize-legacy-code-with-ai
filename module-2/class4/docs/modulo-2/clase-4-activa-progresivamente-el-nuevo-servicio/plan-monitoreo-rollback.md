# Plan de monitoreo y rollback: activación progresiva del catálogo (M2 · C4)

Paso 2 de la clase. Define el criterio de éxito, las alertas y la condición de rollback de cada etapa, y configura el
flag para que el rollback no necesite un deploy. Se basa en el [informe de investigación](informe-investigacion.md) y
en la [tabla de etapas del plan de extracción](../../modulo-1/clase-6-planifica-la-migracion/plan-de-extraccion.md#4-tabla-de-etapas).

Rama `rollout/catalogo-service`, un cambio por commit (ver [Cambios de configuración](#cambios-de-configuración)).

## Cómo funciona el flag

- **Tabla `catalog_rollout`** (V1.14): una fila con `remote_percentage` (0 a 100). Con `NULL` decide `CATALOG_MODE`
  como antes (`LOCAL` = 0 %, `REMOTE` = 100 %). Cada instancia la lee cada **5 s** (`CATALOG_ROLLOUT_REFRESH`).
- **Se cambia sin deploy** con `PUT /api/flights/catalog/rollout` (header `X-Api-Key`, `CATALOG_ROLLOUT_API_KEY`) o
  con un `UPDATE` de la tabla. Cada cambio queda en `catalog_rollout_history` (quién, cuándo, por qué).
- **El reparto es por llamada a `CatalogApi`**, no por búsqueda: con 50 % cada consulta al catálogo va al servicio con
  probabilidad 0,5. La caché, si está encendida, queda delante de los dos caminos.
- **Regla de rollback ejecutable** (`CatalogRolloutGuard`): cada 10 s mira las llamadas remotas de la ventana
  (`catalog.lookup{path=remote}`) y pone el flag en **0 %** para todas las instancias si se cumple alguna regla de la
  tabla de alertas. Sólo vuelve atrás: nunca avanza una etapa.

## Línea base medida en staging (2026-11-18)

Stack de `docker compose` (staging de este proyecto), una instancia, lote y caché apagados, 60 búsquedas
`BUE,MIA ↔ MIA,BUE` por medición. Son valores de un stack local, no la línea base de 7 días del plan.

| Métrica | Local (0 %) | Remoto (100 %) | Fuente |
|---|---|---|---|
| p95 de la búsqueda (servidor) | 53,0 ms | 53,0 ms | `http.server.requests.percentile{uri=/clusters/itineraries,phi=0.95}` |
| Media de una llamada al catálogo | 1,3 ms | 3,5 ms (4,8 ms con 50 %) | `catalog.lookup{path}` (`TOTAL_TIME / COUNT`) |
| Máximo de una llamada | 52,5 ms | 14,8 ms | `catalog.lookup{path}` `MAX` |
| Reparto con el flag en 50 % | 321 llamadas | 339 llamadas (51 %) | `catalog.lookup{path}` `COUNT` |

**B** = 53 ms es el p95 de la búsqueda que usan los umbrales de abajo. Hay que volver a medirlo cuando se enciendan el
lote y la caché (etapas 1a/1b), porque cambian la cantidad de consultas por búsqueda.

## Tabla de etapas

Valores de referencia: **B** = p95 de la búsqueda con 0 % remoto en la misma ventana. Ventana de evaluación del guard:
10 s. "Sostenido" = 6 ventanas seguidas (1 minuto).

| Etapa | Flag | Criterio de éxito (para avanzar) | Alertas activas | Condición de rollback | Rollback y tiempo |
|---|---|---|---|---|---|
| **Pre** · Lote y caché (1a/1b) | `CATALOG_BATCH_ENABLED=true`, `CATALOG_CACHE_ENABLED=true` (env, requieren reinicio) | Etapas 1a/1b del plan cumplidas: ≤ **4** consultas al catálogo por búsqueda y hit ratio ≥ **95 %**. Nueva **B** medida | — (camino local) | Las de 1a/1b del plan | Variable de entorno a `false` + reinicio (≤ 15 min). **No es un flag de runtime** |
| **3** · Sombra | `catalog.mode=shadow` | Bloqueada: el modo sombra y `catalog.shadow.comparisons` no existen | — | — | — |
| **4a** · Canario 5 % | `remote_percentage=5` | 24 h; ≥ **8.000** llamadas remotas (≈ 2.000 búsquedas × 4); errores remotos ≤ **0,1 %**; media remota ≤ **10 ms** y p99 remoto ≤ **50 ms**; p95 búsqueda ≤ **B + 10 %** (58 ms); 503 de búsqueda ≤ **0,1 %**; `catalog.rollout.rollbacks` = **0** | A1–A6 | **Automática:** A1, A2 o A3. **Manual:** A4 (p95 > B + 20 % sostenido) o A5 (503 > 0,5 % sostenido) | Guard → 0 % en ≤ **25 s** desde el primer error (medido con 2 instancias: 15 s).  Manual `PUT` → 0 % en ≤ **5 s** (medido por SQL: 4,8 s) |
| **4b** · Canario 25 % | `remote_percentage=25` | 48 h; ≥ **40.000** llamadas remotas; mismos umbrales que 4a; `hikaricp.connections.pending` del catálogo = **0** | A1–A6 | Igual que 4a | Igual que 4a |
| **4c** · Canario 50 % | `remote_percentage=50` | 48 h con un pico de tráfico (el p99 de req/s de la semana de B); mismos umbrales que 4a | A1–A6 | Igual que 4a | Igual que 4a |
| **5** · 100 % remoto | `remote_percentage=100` | 14 días; p95 búsqueda ≤ **B + 10 %**; errores remotos ≤ **0,1 %**; disponibilidad del servicio ≥ **99,9 %**; 503 de búsqueda ≤ **0,1 %** | A1–A6 | Igual que 4a, contra **B** medida antes de la etapa | Igual que 4a |

## Alertas

| # | Alerta | Condición (umbral numérico) | Fuente | Acción | ¿Ejecutable hoy? |
|---|---|---|---|---|---|
| A1 | `CatalogRemoteErrorsCritical` | Errores remotos > **5 %** y ≥ **3** errores en **1** ventana de 10 s, con ≥ **10** llamadas remotas | `catalog.lookup{path=remote,result=error}` / `catalog.lookup{path=remote}` | Rollback automático a 0 % | **Sí** (guard) |
| A2 | `CatalogRemoteErrors` | Errores remotos > **0,5 %** en **6** ventanas seguidas (1 min), ≥ 10 llamadas | Igual que A1 | Rollback automático a 0 % | **Sí** (guard) |
| A3 | `CatalogRemoteLatency` | Media de las llamadas remotas > **50 ms** en **6** ventanas seguidas (1 min), ≥ 10 llamadas | `catalog.lookup{path=remote}` `TOTAL_TIME/COUNT` | Rollback automático a 0 % | **Sí** (guard) |
| A4 | `CatalogSearchLatency` | p95 de la búsqueda > **B + 20 %** (64 ms) en 2 ventanas de 5 min | `http.server.requests.percentile{uri=/clusters/itineraries,phi=0.95}` | Rollback manual (`PUT` 0 %) | **Sí** (Prometheus, [observabilidad-rollout.md](observabilidad-rollout.md)) |
| A5 | `CatalogUnavailable503` | 503 de búsqueda > **0,5 %** de las búsquedas en 2 ventanas de 5 min | `http.server.requests{status=503}` | Rollback manual (`PUT` 0 %) | **Sí** (Prometheus) |
| A6 | `CatalogRolloutRolledBack` | `catalog.rollout.rollbacks` > **0**, o una línea `ALERT` del guard | Contador + log `ERROR ... ALERT <regla>` | Avisar a la guardia. **No se vuelve a subir** el porcentaje sin revisar la causa | **Sí** (Prometheus + Alertmanager, severidad `page`); a Slack cuando exista el webhook |

Las alertas de deriva de 40014 y de itinerarios descartados del plan (`Catalog40014Drift`, `CatalogSearchEmptyByCatalog`)
**no son ejecutables**: faltan `search.catalog.outcome` y `provider.itineraries.*` (bloqueo 4 del informe).

## Cambios de configuración

| Commit | Cambio | Propiedad / variable | Valor por defecto |
|---|---|---|---|
| `1fecf7f` | Métrica `catalog.lookup{path,operation,result}` con p95/p99; percentiles de la búsqueda | `management.metrics.distribution.percentiles[http.server.requests]` | `0.95,0.99` |
| `837c503` | Flag de runtime: tabla `catalog_rollout` + historial (V1.14, rollback `U1.14`), gauge `catalog.rollout.remote.percentage` | `catalog.rollout.refresh` / `CATALOG_ROLLOUT_REFRESH` | `5000` ms |
| `82a75cb` | `GET/PUT /api/flights/catalog/rollout` con su propia API key | `catalog.rollout.api-key` / `CATALOG_ROLLOUT_API_KEY` | `twa-catalog-rollout-local-key` |
| `e9be1d7` | Regla de rollback (`CatalogRolloutGuard`), contador `catalog.rollout.rollbacks{rule}` | `catalog.rollout.guard.*` / `CATALOG_ROLLOUT_GUARD_*`: `enabled`, `window`, `min-calls`, `max-error-rate`, `critical-error-rate`, `max-mean-latency`, `consecutive-windows` | `true`, `30000` ms, `20`, `0.5` %, `5.0` %, `50` ms, `2` |
| `9f9f53c` | Stack: API key del rollout y guard encendido. `CATALOG_MODE` sigue en `LOCAL` | `docker-compose.yml` | — |
| (revisión) | Ventana del guard más corta, mínimo de errores de A1 y gauges de la regla (`catalog.rollout.guard.window`, `catalog.rollout.guard.min.calls`) | `window`, `min-calls`, `critical-min-errors`, `consecutive-windows` | `10000` ms, `10`, `3`, `6` |
| (revisión) | Stack: 2 instancias detrás de `flights-lb` (P1), lote y caché encendidos (1a/1b), observabilidad (P3) | `docker-compose.yml`, `docker-compose.observability.yml` | `CATALOG_BATCH_ENABLED=true`, `CATALOG_CACHE_ENABLED=true` |

Avanzar una etapa (ejemplo 4a):

```bash
curl -X PUT http://localhost:8080/api/flights/catalog/rollout \
  -H "X-Api-Key: twa-catalog-rollout-local-key" -H "Content-Type: application/json" \
  -d '{"remotePercentage":5,"updatedBy":"equipo-busqueda","reason":"Etapa 4a: canario 5 %"}'
```

Rollback manual (cualquier etapa), por API o por SQL si la API no responde:

```bash
curl -X PUT http://localhost:8080/api/flights/catalog/rollout \
  -H "X-Api-Key: twa-catalog-rollout-local-key" -H "Content-Type: application/json" \
  -d '{"remotePercentage":0,"updatedBy":"guardia","reason":"Rollback: CatalogSearchLatency"}'
```

```bash
docker exec flights-db mysql -uroot -pmuppet flights -e "UPDATE catalog_rollout SET remote_percentage=0, updated_by='guardia', reason='rollback' WHERE id=1"
```

## Prueba en staging (2026-11-18)

| Prueba | Resultado |
|---|---|
| Suite del monolito en los dos modos (`mvn -pl twa-flights-monolith test`) | 218 y 218 tests OK (1 skip esperado en REMOTE) |
| `./scripts/smoke-test.sh` | Todos OK, antes y después de las pruebas |
| `GET/PUT /catalog/rollout` | Sin key: 401. `remotePercentage=101`: 40030. Sin `updatedBy`: 40031 |
| Canario 50 % | 321 llamadas locales y 339 remotas, 60/60 búsquedas en 200 |
| **Rollback automático:** 100 % remoto y `docker stop twa-flights-catalog` | Primer 503 al segundo **1**; el guard dispara `ALERT CatalogRemoteErrorsCritical: 46 remote calls, errors 100.00 %, mean 125.5 ms` y el flag queda en 0 % al segundo **38**; la primera búsqueda siguiente da 200 |
| Rollback manual por SQL (sin la API, como otra instancia) | 25 % aplicado en 2,2 s; 0 % aplicado en **4,8 s** |
| Historial | `catalog_rollout_history` registra cada cambio, incluido `catalog-rollout-guard` con la regla y los valores |

Los `ERROR` del log del monolito durante la prueba son los esperados: `CatalogClient` con el servicio bajado, el `ALERT`
del guard, el `unknown not found` de la prueba de humo de 404 y los `calculating the flight duration` de los
proveedores, que ya existían con el camino local.

## Limitaciones y riesgos que quedan

1. **Durante el corte, los usuarios ven 503 hasta que dispara el guard.** Sin fallback por pedido (decisión 1 del
   plan de extracción), así que se achicó la ventana del guard de 30 s a 10 s: en el simulacro con 2 instancias, lote y
   caché, los usuarios vieron 503 durante **13 s** (25 búsquedas) contra 35 s (40 búsquedas) con la ventana de 30 s.
   El peor caso es ~2 ventanas + el refresco del flag (≤ 25 s).
2. **El reparto es por llamada, no por búsqueda.** Una búsqueda puede mezclar caminos, así que el p95 de la búsqueda no
   se puede separar en canario y control: la comparación de la misma ventana se hace con `catalog.lookup{path}`.
3. **El guard evalúa el tráfico de su instancia.** Con 2 instancias (P1), cada una decide con lo suyo; el rollback de
   una se propaga a la otra por la tabla.
4. **Si la base no responde cuando el guard hace rollback**, el 0 % queda sólo en esa instancia; cuando la base vuelve,
   el refresco lee el valor anterior. El guard lo detecta otra vez en la siguiente ventana, pero no es definitivo.
5. **Percentiles con historia** desde P3 (Prometheus, 35 días). B necesita 7 días con el flag en 0 % y con lote y
   caché encendidos: la historia de antes de 1a/1b también entra en la mediana hasta que pasen los 7 días.
6. **Lote y caché encendidos en el stack** (1a/1b, [medición](evidencia/lote-cache-20261118.md)): 4 consultas por
   búsqueda y 99,8 % de aciertos. Con la caché, **casi no hay llamadas remotas** (sólo los fallos de la caché), así
   que con poco tráfico el guard no llega a `min-calls` y queda ciego: lo avisa `CatalogRolloutGuardBlind`.
7. **Etapa 3 (sombra) bloqueada** y deriva de 40014 / descartes no medibles (ver alertas).
