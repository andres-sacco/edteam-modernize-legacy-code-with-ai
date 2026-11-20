# Simulacro de rollback automático del catálogo

Generado por `scripts/rollback-drill.sh`. Tiempos desde `docker stop twa-flights-catalog` con el flag en 100 % remoto,
las búsquedas a través del balanceador (`flights-lb`).

| Hito | Tiempo |
|---|---|
| Primera búsqueda con error que ve el usuario | 26.1 s |
| Flag en 0 % en todas las instancias (guard) | 41.4 s |
| Última búsqueda con error | 39.1 s |
| `CatalogRolloutRolledBack` en Alertmanager | 51.4 s |

| Búsquedas | Cantidad |
|---|---|
| Antes del corte, todas | 202 (202 con 200) |
| Con error después del corte | 25 (status: 503) |
| Más de 6 s después del rollback (≥ 1 refresco del flag) | 18 (18 con 200) |

Flag restaurado a `null` al terminar. Archivos: `monolito-alert.log` (línea `ALERT` del guard y cambios del flag de
cada instancia),
`catalog_rollout_history.txt`, `prometheus-*.json` (`prometheus-flag.json` por instancia), `alertmanager-alerta.json` y
`busquedas.tsv` (status de cada búsqueda).
