#!/usr/bin/env bash
# Drill of the automatic rollback of the catalog service (M2 C4) with evidence. Against the stack with observability
# (make start-observability):
#   1. sets the flag in 100 % remote and sends searches all the time,
#   2. stops twa-flights-catalog and measures when the users see the first error, when CatalogRolloutGuard sets the
#      flag in 0 % and when Alertmanager has CatalogRolloutRolledBack,
#   3. starts the service again, restores the flag and saves the evidence (log ALERT, catalog_rollout_history, queries
#      to Prometheus and Alertmanager, the status of each search) in docs/.../evidencia/rollback-drill-<date>/.
# Usage: ./scripts/rollback-drill.sh

set -u

BASE_URL="${BASE_URL:-http://localhost:8080/api/flights}"
PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:9090}"
ALERTMANAGER_URL="${ALERTMANAGER_URL:-http://localhost:9093}"
API_KEY="${CATALOG_ROLLOUT_API_KEY:-twa-catalog-rollout-local-key}"
ROLLBACK_TIMEOUT="${ROLLBACK_TIMEOUT:-120}"
ALERT_TIMEOUT="${ALERT_TIMEOUT:-120}"

STAMP=$(date +%Y%m%d-%H%M%S)
OUT_DIR="docs/modulo-2/clase-4-activa-progresivamente-el-nuevo-servicio/evidencia/rollback-drill-$STAMP"
mkdir -p "$OUT_DIR"

now() { date +%s.%N; }
since() { python3 -c "import sys; print(f'{float(sys.argv[1]) - float(sys.argv[2]):.1f}')" "$1" "$2"; }
rollout() { curl -s -H "X-Api-Key: $API_KEY" "$BASE_URL/catalog/rollout"; }
set_rollout() {
  curl -s -o /dev/null -w '%{http_code}' -X PUT "$BASE_URL/catalog/rollout" -H "X-Api-Key: $API_KEY" \
    -H "Content-Type: application/json" \
    -d "{\"remotePercentage\":$1,\"updatedBy\":\"rollback-drill\",\"reason\":\"$2\"}"
}
INSTANCES="${INSTANCES:-twa-flights-monolith twa-flights-monolith-2}"
# The flag of each instance (not through the balancer): the rollback ends when all of them apply 0 %
remote_percentage() {
  local max=0 value
  for instance in $INSTANCES; do
    value=$(docker exec "$instance" curl -s -H "X-Api-Key: $API_KEY" http://localhost:8080/api/flights/catalog/rollout |
      python3 -c 'import json, sys; print(json.load(sys.stdin)["effectiveRemotePercentage"])' 2>/dev/null || echo 100)
    [ "$value" -gt "$max" ] && max=$value
  done
  echo "$max"
}
firing() {
  curl -s "$ALERTMANAGER_URL/api/v2/alerts?active=true&filter=alertname%3D%22CatalogRolloutRolledBack%22" |
    python3 -c 'import json, sys; print(len(json.load(sys.stdin)))'
}

echo "Preconditions"
for url in "$BASE_URL/actuator/health" "$PROMETHEUS_URL/-/ready" "$ALERTMANAGER_URL/-/ready"; do
  if ! curl -sf -o /dev/null "$url"; then
    echo "  FAIL  $url does not answer: make start-observability"
    exit 1
  fi
done
PREVIOUS=$(rollout | python3 -c 'import json, sys; v = json.load(sys.stdin)["remotePercentage"]; print("null" if v is None else v)')
echo "  OK    stack up, flag before the drill: $PREVIOUS"

read -r DEPARTURE RETURN <<<"$(python3 -c 'import datetime as d; t = d.date.today(); print(t + d.timedelta(days=60), t + d.timedelta(days=70))')"
SEARCH="$BASE_URL/clusters/itineraries?from=BUE,MIA&to=MIA,BUE&departure=$DEPARTURE,$RETURN&adults=1&children=0&infants=0&amount=3"

START=$(now)
LOG_SINCE=$(date -u +%Y-%m-%dT%H:%M:%SZ)
echo "1. Flag in 100 % remote and searches every 0,2 s"
echo "  PUT 100 -> $(set_rollout 100 'Simulacro de rollback: 100 % remoto')"

SEARCHES="$OUT_DIR/busquedas.tsv"
echo -e "epoch\tstatus" > "$SEARCHES"
(
  while true; do
    status=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "$SEARCH")
    echo -e "$(now)\t$status" >> "$SEARCHES"
    sleep 0.2
  done
) &
TRAFFIC_PID=$!
trap 'kill $TRAFFIC_PID 2>/dev/null; docker start twa-flights-catalog >/dev/null 2>&1' EXIT

# Several windows of the guard with the remote path healthy
sleep 45

echo "2. docker stop twa-flights-catalog"
STOP=$(now)
docker stop twa-flights-catalog >/dev/null

ROLLBACK=""
while [ "$(since "$(now)" "$STOP" | cut -d. -f1)" -lt "$ROLLBACK_TIMEOUT" ]; do
  if [ "$(remote_percentage)" = "0" ]; then
    ROLLBACK=$(now)
    break
  fi
  sleep 1
done
if [ -n "$ROLLBACK" ]; then
  echo "  OK    flag in 0 % in all the instances ($INSTANCES) at $(since "$ROLLBACK" "$STOP") s of the stop"
else
  echo "  FAIL  the flag is not in 0 % after $ROLLBACK_TIMEOUT s"
fi

# Searches after the rollback: they have to answer 200 with the local path
sleep 10
kill $TRAFFIC_PID 2>/dev/null
wait $TRAFFIC_PID 2>/dev/null

ALERT=""
while [ "$(since "$(now)" "$STOP" | cut -d. -f1)" -lt "$((ROLLBACK_TIMEOUT + ALERT_TIMEOUT))" ]; do
  if [ "$(firing)" -gt 0 ]; then
    ALERT=$(now)
    break
  fi
  sleep 2
done
if [ -n "$ALERT" ]; then
  echo "  OK    CatalogRolloutRolledBack in Alertmanager at $(since "$ALERT" "$STOP") s of the stop"
else
  echo "  FAIL  CatalogRolloutRolledBack is not in Alertmanager"
fi

echo "3. Restore"
docker start twa-flights-catalog >/dev/null
echo "  docker start twa-flights-catalog"
echo "  PUT $PREVIOUS -> $(set_rollout "$PREVIOUS" 'Fin del simulacro de rollback')"
END=$(now)

echo "Evidence in $OUT_DIR"
for instance in $INSTANCES; do
  docker logs --since "$LOG_SINCE" "$instance" 2>&1 | grep -E 'ALERT|rollout of the catalog' | sed "s/^/$instance /"
done > "$OUT_DIR/monolito-alert.log"
# The changes of the drill: the PUT to 100 %, the rollback of the guard and the restore
docker exec flights-db mysql -uroot -pmuppet flights -t -e \
  "SELECT * FROM (SELECT * FROM catalog_rollout_history ORDER BY id DESC LIMIT 5) h ORDER BY id" \
  > "$OUT_DIR/catalog_rollout_history.txt" 2>/dev/null
curl -s "$PROMETHEUS_URL/api/v1/query_range" --data-urlencode 'query=catalog_rollout_remote_percentage{service="twa-flights-monolith"}' \
  --data-urlencode "start=${START%.*}" --data-urlencode "end=${END%.*}" --data-urlencode 'step=5' \
  > "$OUT_DIR/prometheus-flag.json"
curl -s "$PROMETHEUS_URL/api/v1/query" --data-urlencode 'query=catalog_rollout_rollbacks_total' \
  > "$OUT_DIR/prometheus-rollbacks.json"
curl -s "$PROMETHEUS_URL/api/v1/query_range" --data-urlencode 'query=catalog:lookup_error:ratio_rate5m' \
  --data-urlencode "start=${START%.*}" --data-urlencode "end=${END%.*}" --data-urlencode 'step=15' \
  > "$OUT_DIR/prometheus-errores-por-camino.json"
curl -s "$ALERTMANAGER_URL/api/v2/alerts?filter=alertname%3D%22CatalogRolloutRolledBack%22" > "$OUT_DIR/alertmanager-alerta.json"

python3 - "$SEARCHES" "$STOP" "${ROLLBACK:-}" "${ALERT:-}" "$OUT_DIR/resumen.md" "$PREVIOUS" <<'EOF'
import csv, sys
searches, stop, rollback, alert, out, previous = sys.argv[1:7]
stop = float(stop)
rows = [(float(r["epoch"]), r["status"]) for r in csv.DictReader(open(searches), delimiter="\t")]
before = [s for t, s in rows if t < stop]
errors = [(t, s) for t, s in rows if t >= stop and s != "200"]
after = [s for t, s in rows if rollback and t > float(rollback) + 6]
first_error = f"{errors[0][0] - stop:.1f} s" if errors else "ninguno"
last_error = f"{errors[-1][0] - stop:.1f} s" if errors else "ninguno"
fmt = lambda v: f"{float(v) - stop:.1f} s" if v else "no llegó"
statuses = sorted({s for _, s in errors})
with open(out, "w") as f:
    f.write(f"""# Simulacro de rollback automático del catálogo

Generado por `scripts/rollback-drill.sh`. Tiempos desde `docker stop twa-flights-catalog` con el flag en 100 % remoto,
las búsquedas a través del balanceador (`flights-lb`).

| Hito | Tiempo |
|---|---|
| Primera búsqueda con error que ve el usuario | {first_error} |
| Flag en 0 % en todas las instancias (guard) | {fmt(rollback)} |
| Última búsqueda con error | {last_error} |
| `CatalogRolloutRolledBack` en Alertmanager | {fmt(alert)} |

| Búsquedas | Cantidad |
|---|---|
| Antes del corte, todas | {len(before)} ({sum(1 for s in before if s == '200')} con 200) |
| Con error después del corte | {len(errors)} (status: {', '.join(statuses) or '—'}) |
| Más de 6 s después del rollback (≥ 1 refresco del flag) | {len(after)} ({sum(1 for s in after if s == '200')} con 200) |

Flag restaurado a `{previous}` al terminar. Archivos: `monolito-alert.log` (línea `ALERT` del guard y cambios del flag de
cada instancia),
`catalog_rollout_history.txt`, `prometheus-*.json` (`prometheus-flag.json` por instancia), `alertmanager-alerta.json` y
`busquedas.tsv` (status de cada búsqueda).
""")
print(open(out).read())
EOF
