# Observabilidad del rollout del catálogo (M2 · C4)

Respuesta a la [revisión de avance de etapa](revision-avance-etapa.md). Toma como base el stack de `twa-observability`
(otro proyecto), le saca todo lo que es de otras aplicaciones y le agrega lo que hace falta para medir el rollout del
catálogo. Todo corre dentro del mismo `docker compose` del stack.

```bash
make start-observability     # compila y levanta el stack + Prometheus (9090), Alertmanager (9093) y Grafana (3000)
make test-observability      # tests de las alertas con promtool
make check-observability     # valida reglas, prometheus.yml y alertmanager.yml
make rollback-drill          # simulacro de rollback con evidencia en evidencia/
make dashboards              # regenera el dashboard desde observability/scripts/build_dashboards.py
```

Dashboard: http://localhost:3000/d/catalog-rollout · Runbooks: [observability/runbooks/](../../../observability/runbooks/).

## Qué se tomó de twa-observability y qué se sacó

| Se tomó | Se sacó (es de otras aplicaciones o este stack no lo necesita) |
|---|---|
| Prometheus v3.5.0 con 35 días de retención | Jobs y targets de `twa-flights-catalog` (el de Mongo, puerto 8080, choca con el monolito), `exchange-rates` (8081, choca con el catálogo), `search`, `reservations`, `provider-alpha/beta` y Blackbox |
| Alertmanager con ruteo por severidad, inhibiciones y plantilla de Slack | Reglas, tests, runbooks y dashboards de esos servicios, de `catalog-loader` y de `exchange-rates-loader` |
| Reglas de plataforma: `Watchdog`, `PrometheusRuleEvaluationFailures`, `PrometheusNotificationsFailing`, `AlertmanagerNotificationsFailing` | Pushgateway (jobs batch), Loki + Alloy (logs: necesitan JSON y la etiqueta `com.twa.service`), OTel Collector + Tempo (trazas) y sus reglas |
| Grafana con provisioning y los helpers de `build_dashboards.py` (`panel`, `stat`, `layout`) | Las ~1.900 líneas de dashboards de los otros servicios; la home `twa-flights-catalog.json` |
| `make check` / `make test` | `host.docker.internal`: Prometheus scrapea por el DNS de Docker |

## Cambios

| Archivo | Cambio |
|---|---|
| `twa-flights-monolith/pom.xml`, `twa-flights-catalog/pom.xml` | `micrometer-registry-prometheus` |
| `twa-flights-monolith/src/main/resources/application.yml` | Expone `prometheus`; buckets de histograma para `http.server.requests` y `catalog.lookup` (1 ms–10 s y 100 µs–3 s). Los percentiles de Actuator se mantienen |
| `twa-flights-catalog/src/main/resources/application.yml` | Expone `prometheus`; buckets para `http.server.requests` |
| `CatalogRolloutGuard.java` | Una ventana con menos de `min-calls` reinicia los contadores: "2 ventanas seguidas" son seguidas de verdad (hallazgo #7) |
| `docker-compose.observability.yml` | Prometheus, Alertmanager y Grafana |
| `observability/prometheus/rules/catalog-rollout.rules.yml` | Reglas de grabación: los dos caminos en la misma ventana, la búsqueda y la línea base B |
| `observability/prometheus/rules/catalog-rollout-alerts.rules.yml` | A4, A5, A6, `CatalogRemoteLatencyVsLocal`, `CatalogRolloutGuardBlind`, `CatalogRolloutBaselineMissing/Short` y disponibilidad |
| `observability/prometheus/tests/catalog-rollout.test.yml` | 11 casos de `promtool` (disparan y no disparan) |
| `observability/alertmanager/` | Receptores `page` y `ticket` a Slack con el webhook en `secrets/slack-webhook-url` (ignorado por git) |
| `observability/grafana/`, `observability/scripts/build_dashboards.py` | Dashboard `catalog-rollout` |
| `observability/runbooks/` | `catalog-rollout.md` y `platform.md` |
| `scripts/rollback-drill.sh`, `Makefile` | Simulacro con evidencia y los targets de arriba |

## Estado de cada hallazgo

| # | Hallazgo | Qué se hizo | Estado ahora | Evidencia |
|---|---|---|---|---|
| 1 | Rollback automático sin evidencia | `scripts/rollback-drill.sh` lo prueba y guarda el log, el historial y las consultas | **Verificado** | [evidencia/rollback-drill-20261118-101216](evidencia/rollback-drill-20261118-101216/resumen.md): flag en 0 % a los **35,6 s** del corte, `ALERT CatalogRemoteErrorsCritical: 35 remote calls, errors 100.00 %` |
| 2 | Rollback manual sin evidencia | El simulacro restaura el flag por `PUT` y queda en `catalog_rollout_history` | **Verificado** (el `PUT`); el `UPDATE` por SQL no se repitió | `catalog_rollout_history.txt` del simulacro (filas 1 a 3) |
| 3 | Sin 2 instancias | `twa-flights-monolith` y `twa-flights-monolith-2` detrás de `flights-lb` (nginx, reintenta sólo fallas de conexión), apagado ordenado; Prometheus las scrapea por nombre | **Resuelto en el stack local**. P1 cumplido: 883 búsquedas y 0 errores reiniciando una instancia por vez. El rollback de una instancia llegó a la otra por la tabla en 1,8 s | [rolling-restart-20261118-103955](evidencia/rolling-restart-20261118-103955/resumen.md), [rollback-drill-20261118-104106](evidencia/rollback-drill-20261118-104106/resumen.md) |
| 4 | El usuario ve el error antes que la alerta | Se mantiene la decisión 1 (sin fallback por pedido) y se achica la ventana del guard: 10 s, A1 con ≥ 3 errores, "sostenido" = 6 ventanas | **Mitigado, no eliminado**: 503 durante **13 s** (25 búsquedas) contra 35 s (40 búsquedas) antes; flag en 0 % en las 2 instancias a los 15 s del primer error. El usuario sigue viendo el error antes que la notificación (51 s) | [rollback-drill-20261118-104106](evidencia/rollback-drill-20261118-104106/resumen.md) |
| 5 | A3 no ve la degradación | `CatalogRemoteLatencyVsLocal`: p95 remoto > 3 × el local en la misma ventana y > 10 ms. A4 ejecutable contra B | **Resuelto** en reglas | `make test-observability`: casos "3 times slower" y "under 10 ms" |
| 6 | Guard ciego sin aviso | `CatalogRolloutGuardBlind` (< 20 llamadas remotas cada 30 s, por instancia) | **Resuelto** en reglas; el tráfico real sigue sin conocerse | Caso "guard without enough remote calls" |
| 7 | Ventanas "seguidas" que no lo son | Corrección en `CatalogRolloutGuard` | **Resuelto** (sin test unitario del guard) | Suite del monolito |
| 8 | A6 no le llega a nadie | `CatalogRolloutRolledBack` con severidad `page`, que llega a Alertmanager | **Parcial**: la alerta llega a Alertmanager (51,8 s); a Slack sólo cuando exista `secrets/slack-webhook-url`. Sin ese archivo dispara `AlertmanagerNotificationsFailing` | `alertmanager-alerta.json` del simulacro |
| 9 | Línea base de corridas separadas | B como regla: mediana de los p95 de 5 min con el flag en 0 % en 7 días | **Resuelto** en reglas; hoy B tiene minutos de historia (`CatalogRolloutBaselineShort`) | Panel "Historia de B" |
| 10 | Métricas acumuladas, sin la misma ventana | Histogramas + `catalog:lookup_*:5m{path}`: los dos caminos sobre los mismos 5 minutos | **Resuelto** | Panel "Misma ventana: local contra remoto" |
| 11 | Dos B distintas | Una sola B, reproducible por consulta | **Resuelto**; falta medirla después de 1a/1b | `search:latency_seconds:p95_baseline7d` |
| 12 | Archivos de análisis vacíos | El simulacro guarda archivos con datos | **Resuelto** para el rollback; los `analisis-*.json` vacíos siguen vacíos (no se tocaron) | `evidencia/` |
| 13 | Sin dashboard | Dashboard `catalog-rollout` (22 paneles) | **Resuelto** | http://localhost:3000/d/catalog-rollout |
| 14 | Prerrequisitos 1a/1b, P1, P3 | Lote y caché encendidos en el stack; 2 instancias; Prometheus | **P1 y P3 resueltos en local.** 1a: 4,0 consultas por búsqueda; 1b: 99,8 % de aciertos. Faltan las 48 h y los criterios de 40014 y descartes (no hay métricas) | [lote-cache-20261118](evidencia/lote-cache-20261118.md) |

## Veredicto actualizado

**Todavía no se puede avanzar al canario 4a**, pero lo que falta ya no es código ni configuración del stack:

1. **Tiempo:** B necesita 7 días con el flag en 0 % y con lote y caché encendidos (hoy tiene minutos, y la mediana
   todavía incluye la historia de antes de 1a/1b); 1a y 1b piden 48 h cada una.
2. **Webhook de Slack:** cargarlo en `observability/alertmanager/secrets/slack-webhook-url`. Sin eso, las alertas
   `page` sólo se ven en Alertmanager.
3. **Métricas de 40014 y descartes** (`search.catalog.outcome`, `provider.itineraries.*`): los criterios de 1a y las
   alertas `Catalog40014Drift`/`CatalogSearchEmptyByCatalog` las necesitan y no existen.
4. **Riesgo aceptado (#4):** con el catálogo caído, el usuario ve 503 hasta ~25 s (13 s medidos). Es el costo de la
   decisión 1.
5. **Riesgo nuevo:** con la caché al 99,8 %, las llamadas remotas son sólo los fallos de la caché. Con un canario de
   5 % y poco tráfico, el guard no llega a `min-calls` y queda ciego (`CatalogRolloutGuardBlind`), y los ≥ 8.000
   llamadas remotas en 24 h que pide 4a pueden no alcanzarse. Hay que revisar `min-calls` y el criterio de 4a con
   tráfico real.

## Verificación (2026-11-18)

- `mvn -q -B clean package -DskipTests`: OK. `./scripts/smoke-test.sh`: todos OK.
- `mvn -B -pl twa-flights-monolith test`: 218 y 218 tests OK en los dos modos (1 skip esperado en REMOTE). Después de
  los cambios del guard y del stack, `mvn -B test`: monolito 218 y 218 OK, catálogo 20 OK.
- Stack con 2 instancias, lote y caché: `./scripts/smoke-test.sh` OK a través de `flights-lb`; 11 min de tráfico
  (3.922 búsquedas) y 5,5 min sólo con lote (1.926) sin errores.
- `make rolling-restart`: 0 errores (P1). `make rollback-drill`: 13 s de 503, rollback en las 2 instancias.
- `make check-observability` y `make test-observability`: OK (11 casos).
- Prometheus: los 4 targets en `up`; todas las reglas con `health=ok`.
- Grafana: el dashboard se provisiona y cada consulta devuelve series (las del servicio de catálogo sólo con tráfico).
- Simulacro: ver [resumen](evidencia/rollback-drill-20261118-101216/resumen.md).
