# Runbooks · rollout del servicio de catálogo

Alertas de `observability/prometheus/rules/catalog-rollout-alerts.rules.yml`. Etapas, umbrales y prueba del rollback
en [plan-monitoreo-rollback.md](../../docs/modulo-2/clase-4-activa-progresivamente-el-nuevo-servicio/plan-monitoreo-rollback.md).
Dashboard: http://localhost:3000/d/catalog-rollout.

**Rollback manual (cualquier alerta):**

```bash
curl -X PUT http://localhost:8080/api/flights/catalog/rollout \
  -H "X-Api-Key: twa-catalog-rollout-local-key" -H "Content-Type: application/json" \
  -d '{"remotePercentage":0,"updatedBy":"guardia","reason":"Rollback: <alerta>"}'
```

Si la API no responde: `docker exec flights-db mysql -uroot -pmuppet flights -e "UPDATE catalog_rollout SET
remote_percentage=0, updated_by='guardia', reason='rollback' WHERE id=1"`. Cada instancia lo lee en ≤ 5 s.

## CatalogRolloutRolledBack

El guard (`CatalogRolloutGuard`) ya puso el flag en 0 %. La etiqueta `rule` dice por qué: `CatalogRemoteErrorsCritical`
(> 5 % de errores y al menos 3 errores en una ventana de 10 s), `CatalogRemoteErrors` (> 0,5 % en 6 ventanas seguidas)
o `CatalogRemoteLatency` (media > 50 ms en 6 ventanas seguidas).

1. Confirmar que el flag quedó en 0 % en el panel "Flag" y en `catalog_rollout_history`
   (`SELECT * FROM catalog_rollout_history ORDER BY id DESC LIMIT 5`). Si la base no respondía, el 0 % quedó sólo en
   esa instancia: hacer el rollback manual.
2. Buscar la causa: `docker logs twa-flights-catalog`, el panel "Servicio de catálogo" y la línea `ALERT` en
   `docker logs twa-flights-monolith`.
3. **No volver a subir el porcentaje** hasta tener la causa y la corrección.

## CatalogSearchLatency

p95 de la búsqueda > B + 20 % durante 10 minutos con tráfico remoto (A4). El guard no lo ve: mira cada llamada al
catálogo, no la búsqueda. Rollback manual y revisar el panel "p95 y p99 por camino": si el remoto no es más lento, la
causa es otra (proveedores) y se puede volver a la etapa cuando se normalice.

## CatalogUnavailable503

> 0,5 % de las búsquedas con 503 durante 10 minutos con tráfico remoto (A5). Los cortes del servicio los corta antes el
guard (A1/A2); si esta alerta llega, el guard no actuó: revisar `CatalogRolloutGuardBlind` y hacer el rollback manual.

## CatalogRemoteLatencyVsLocal

p95 del camino remoto > 3 veces el del local en la misma ventana, y > 10 ms. Es la degradación que A3 (media > 50 ms)
no ve: con ~11 llamadas por búsqueda sin lote, 3 veces más por llamada se nota en la búsqueda antes de llegar a 50 ms.
Rollback manual si sigue; revisar el pool del catálogo (`hikaricp_connections_pending`) y el p95 del servidor.

## CatalogRolloutGuardBlind

Una instancia tiene el flag por encima de 0 % pero menos llamadas remotas por ventana que `min-calls` (10 cada 10 s),
o no tiene el guard encendido: el guard saltea todas sus ventanas y el rollback automático está apagado de hecho.
Opciones: subir el porcentaje (si la etapa lo permite), bajar `CATALOG_ROLLOUT_GUARD_MIN_CALLS` (más falsos positivos)
o volver a 0 % hasta tener tráfico. No avanzar de etapa con
esta alerta activa.

## CatalogRolloutBaselineMissing

El flag está por encima de 0 % y no hay línea base B (no hay historia con el flag en 0 %), así que
`CatalogSearchLatency` no puede disparar. Volver a 0 %, dejar correr tráfico con el flag en 0 % (7 días para la base
del plan) y recién entonces subir. `CatalogRolloutBaselineShort` (info) avisa cuando B tiene menos de 7 días.

## TwaMonolithDown

Prometheus no puede scrapear el monolito (o no encuentra ninguna instancia: `TwaMonolithAbsent`). Sin el monolito no
hay ninguna de las métricas del rollout. `docker ps`, `docker logs twa-flights-monolith`.

## TwaCatalogServiceDown

El servicio de catálogo no responde. Con el flag por encima de 0 % es un page: el guard debería volver a 0 % en menos
de 25 s (`CatalogRolloutRolledBack`); si no llega, rollback manual. Con el flag en 0 % es un ticket
(`TwaCatalogServiceDownNoTraffic`): la búsqueda no lo usa.
