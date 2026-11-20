# Simulacro de rollback automático del catálogo

Generado por `scripts/rollback-drill.sh`. Tiempos desde `docker stop twa-flights-catalog` con el flag en 100 % remoto.

| Hito | Tiempo |
|---|---|
| Primera búsqueda con error que ve el usuario | 0.1 s |
| Flag en 0 % (guard) | 35.6 s |
| Última búsqueda con error | 35.5 s |
| `CatalogRolloutRolledBack` en Alertmanager | 51.8 s |

| Búsquedas | Cantidad |
|---|---|
| Antes del corte, todas | 282 (282 con 200) |
| Con error después del corte | 40 (status: 503) |
| Más de 6 s después del rollback (≥ 1 refresco del flag) | 18 (18 con 200) |

Flag restaurado a `null` al terminar. Archivos: `monolito-alert.log` (línea `ALERT` del guard),
`catalog_rollout_history.txt`, `prometheus-*.json`, `alertmanager-alerta.json` y `busquedas.tsv` (status de cada
búsqueda).
