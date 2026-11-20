#!/usr/bin/env bash
# Check of the prerequisite P1 of the extraction plan (M2 C4): with constant searches through the balancer
# (flights-lb), restarts one instance of the monolith at a time, the same as a rolling deploy of a version without
# changes, and counts the answers that are not 200 and the connections that fail. P1 is met with 0 of both.
# The evidence goes to docs/.../evidencia/rolling-restart-<date>/.
# Usage: ./scripts/rolling-restart.sh

set -u

BASE_URL="${BASE_URL:-http://localhost:8080/api/flights}"
INSTANCES="${INSTANCES:-twa-flights-monolith-2 twa-flights-monolith}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-240}"

STAMP=$(date +%Y%m%d-%H%M%S)
OUT_DIR="docs/modulo-2/clase-4-activa-progresivamente-el-nuevo-servicio/evidencia/rolling-restart-$STAMP"
mkdir -p "$OUT_DIR"

now() { date +%s.%N; }
healthy() { [ "$(docker inspect -f '{{.State.Health.Status}}' "$1" 2>/dev/null)" = "healthy" ]; }

if ! curl -sf -o /dev/null "$BASE_URL/actuator/health"; then
  echo "FAIL  $BASE_URL does not answer: make start-observability"
  exit 1
fi

read -r DEPARTURE RETURN <<<"$(python3 -c 'import datetime as d; t = d.date.today(); print(t + d.timedelta(days=60), t + d.timedelta(days=70))')"
SEARCH="$BASE_URL/clusters/itineraries?from=BUE,MIA&to=MIA,BUE&departure=$DEPARTURE,$RETURN&adults=1&children=0&infants=0&amount=3"

SEARCHES="$OUT_DIR/busquedas.tsv"
EVENTS="$OUT_DIR/eventos.tsv"
echo -e "epoch\tstatus" > "$SEARCHES"
echo -e "epoch\tevent" > "$EVENTS"

# Two clients at the same time, so there are requests in progress when an instance stops. Status 000: the connection
# failed
for client in 1 2; do
  (
    while true; do
      status=$(curl -s -o /dev/null -w '%{http_code}' --max-time 15 "$SEARCH")
      echo -e "$(now)\t$status" >> "$SEARCHES"
      sleep 0.1
    done
  ) &
  PIDS="${PIDS:-} $!"
done
trap 'kill $PIDS 2>/dev/null' EXIT

sleep 10
for instance in $INSTANCES; do
  echo "Restart $instance"
  echo -e "$(now)\tdocker restart $instance" >> "$EVENTS"
  docker restart "$instance" >/dev/null
  start=$SECONDS
  until healthy "$instance"; do
    if [ $((SECONDS - start)) -gt "$HEALTH_TIMEOUT" ]; then
      echo "  FAIL  $instance is not healthy after $HEALTH_TIMEOUT s"
      break
    fi
    sleep 1
  done
  echo -e "$(now)\t$instance healthy" >> "$EVENTS"
  echo "  $instance healthy after $((SECONDS - start)) s"
  # The balancer marks a failed instance for fail_timeout (10 s)
  sleep 15
done
kill $PIDS 2>/dev/null
wait 2>/dev/null

for instance in $INSTANCES; do
  docker logs --since 10m "$instance" 2>&1 | grep -E 'ERROR|Graceful shutdown|Started .*Application' | cut -c1-300 |
    sed "s/^/$instance /"
done > "$OUT_DIR/monolito.log"
docker logs --since 10m flights-lb 2>&1 | grep -vE '" 200 ' | tail -50 > "$OUT_DIR/flights-lb.log"

python3 - "$SEARCHES" "$EVENTS" "$OUT_DIR/resumen.md" <<'EOF'
import csv, sys
searches, events, out = sys.argv[1:4]
rows = [(float(r["epoch"]), r["status"]) for r in csv.DictReader(open(searches), delimiter="\t")]
evs = [(float(r["epoch"]), r["event"]) for r in csv.DictReader(open(events), delimiter="\t")]
by_status = {}
for _, s in rows:
    by_status[s] = by_status.get(s, 0) + 1
bad = [(t, s) for t, s in rows if s != "200"]
t0 = rows[0][0] if rows else 0
verdict = "**Cumple**" if not bad and rows else "**No cumple**"
with open(out, "w") as f:
    f.write(f"""# Reinicio escalonado del monolito (prerrequisito P1)

Generado por `scripts/rolling-restart.sh`: búsquedas constantes (2 clientes) a través de `flights-lb` mientras se
reinicia una instancia por vez, como un rolling deploy de una versión sin cambios.

| Resultado | Valor |
|---|---|
| Búsquedas | {len(rows)} |
| Por status | {', '.join(f'{k}: {v}' for k, v in sorted(by_status.items()))} |
| Respuestas distintas de 200 (5xx, conexiones fallidas `000`) | {len(bad)} |
| Criterio de P1 (0 respuestas 5xx y 0 conexiones rechazadas) | {verdict} |

| Segundo | Evento |
|---|---|
""")
    for t, e in evs:
        f.write(f"| {t - t0:.1f} | {e} |\n")
    if bad:
        f.write("\n| Segundo | Status |\n|---|---|\n")
        for t, s in bad[:30]:
            f.write(f"| {t - t0:.1f} | {s} |\n")
    f.write("\nArchivos: `busquedas.tsv`, `eventos.tsv`, `monolito.log` (apagado y arranque de cada instancia) y "
            "`flights-lb.log` (lo que el balanceador no respondió con 200).\n")
print(open(out).read())
EOF
