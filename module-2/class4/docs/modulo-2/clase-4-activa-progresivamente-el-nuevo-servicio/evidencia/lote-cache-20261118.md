# Medición de las etapas 1a (lote) y 1b (caché) · 2026-11-18

Stack de `docker compose` con observabilidad, 2 instancias del monolito detrás de `flights-lb`, flag en 0 % (camino
local). Tráfico secuencial a través del balanceador, rotando 4 rutas: `BUE,MIA ↔ MIA,BUE` (ida y vuelta),
`BUE → SCL`, `MIA → BUE` y `SCL → LIM`, cada 0,15 s. Valores de Prometheus sobre los últimos 5 minutos al terminar.

| Configuración | Búsquedas | No 200 | Consultas al catálogo por búsqueda | Hit ratio | p95 búsqueda (5 min) | Consulta |
|---|---|---|---|---|---|---|
| Lote + caché (`CATALOG_BATCH_ENABLED=true`, `CATALOG_CACHE_ENABLED=true`), 11 min | 3.922 | 0 | 0,02 | **99,8 %** | 8,7 ms | `catalog:lookups_per_search:rate5m`, `catalog:cache_hit:ratio_rate5m` |
| Sólo lote (`CATALOG_CACHE_ENABLED=false`), 5,5 min | 1.926 | 0 | **4,0** (todas `operation=batch`) | — | 14,7 ms | `catalog:lookups_per_search:rate5m`, `sum by (operation) (rate(catalog_lookup_seconds_count[5m]))` |

Sin lote eran ~11 consultas por búsqueda (informe de investigación, fila 6).

## Contra los criterios del plan de extracción

| Etapa | Criterio | Medido | Estado |
|---|---|---|---|
| 1a | Consultas al catálogo por búsqueda ≤ **4** (1 de C1 + 1 por proveedor) | 4,0 | Cumple (en el límite) |
| 1a | p95 ≤ **B** | 14,7 ms contra B ≈ 20 ms | Cumple en esta corrida |
| 1a | 40014 = B ± 0,1 pp; descartes = B ± 0,5 pp | No medible: faltan `search.catalog.outcome` y `provider.itineraries.*` | **No verificado** |
| 1a / 1b | Sostenido **48 h** | Minutos | **No verificado** (tiempo) |
| 1b | Hit ratio ≥ **95 %** después de 10 min de calentamiento | 99,8 % a los 11 min | Cumple, con tráfico sintético de 4 rutas (3 ciudades en caché): el tráfico real tiene más ciudades y menos aciertos |
| 1b | `cache.size` ≤ 1.000 | 3 | Cumple |
