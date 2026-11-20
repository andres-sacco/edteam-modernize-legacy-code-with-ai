"""Generates grafana/dashboards/catalog-rollout.json, the dashboard of the rollout of the catalog service (M2 C4).

Taken from scripts/build_dashboards.py of twa-observability, only with the helpers that this dashboard uses. The JSON
of Grafana is long and repetitive: edit this script and regenerate with `make dashboards`.

The panels have no coordinates: the dashboard is a list of rows, each row a list of lines and each line a list of
panels with their width and height. `layout()` calculates x and y.

Order: from what the user of the search sees to the cause.
  1. Estado ahora        flag, rollbacks, guard, B and the alerts
  2. Misma ventana       the local and the remote path of the catalog over the same 5 minutes
  3. Búsqueda            p95 against B, 503 and traffic
  4. Lote, caché e instancias  criteria of 1a/1b and the flag of each instance
  5. Servicio de catálogo the other side of the remote path
  6. Runtime             (collapsed) Jetty and the pools of the monolith
"""

import json
from pathlib import Path

DS = {"type": "prometheus", "uid": "prometheus"}

MONO = 'service="twa-flights-monolith"'
CAT = 'service="twa-flights-catalog"'
RUNBOOK = "https://github.com/andres-sacco/twa-flights-monolith/blob/main/observability/runbooks/catalog-rollout.md"
SEARCH_URI = 'uri="/clusters/itineraries"'


def target(expr, legend="", ref="A", instant=False):
    return {"datasource": DS, "expr": expr, "legendFormat": legend, "refId": ref, "instant": instant,
            "range": not instant}


def panel(kind, title, w, h, targets, unit="short", description="", **extra):
    p = {
        "type": kind,
        "title": title,
        "description": description,
        "datasource": DS,
        "gridPos": {"x": 0, "y": 0, "w": w, "h": h},
        "targets": [target(*t) if isinstance(t, tuple) else t for t in targets],
        "fieldConfig": {"defaults": {"unit": unit}, "overrides": []},
        "options": {},
    }
    if kind == "timeseries":
        p["fieldConfig"]["defaults"]["custom"] = {"lineWidth": 1, "fillOpacity": 10, "showPoints": "never"}
        p["options"] = {"legend": {"displayMode": "list", "placement": "bottom"}, "tooltip": {"mode": "multi"}}
    for k, v in extra.items():
        if k == "thresholds":
            p["fieldConfig"]["defaults"]["thresholds"] = {"mode": "absolute", "steps": v}
            if kind == "timeseries":
                p["fieldConfig"]["defaults"]["custom"]["thresholdsStyle"] = {"mode": "line+area"}
            else:
                p["fieldConfig"]["defaults"]["color"] = {"mode": "thresholds"}
        elif k == "min_max":
            p["fieldConfig"]["defaults"]["min"], p["fieldConfig"]["defaults"]["max"] = v
        elif k == "overrides":
            p["fieldConfig"]["overrides"] = v
        else:
            p[k] = v
    return p


def stat(title, w, h, expr, unit="short", description="", thresholds=None, legend=""):
    options = {"reduceOptions": {"calcs": ["lastNotNull"]}, "graphMode": "none", "colorMode": "background"}
    p = panel("stat", title, w, h, [(expr, legend, "A", True)], unit=unit, description=description, options=options)
    if thresholds:
        p["fieldConfig"]["defaults"]["thresholds"] = {"mode": "absolute", "steps": thresholds}
        p["fieldConfig"]["defaults"]["color"] = {"mode": "thresholds"}
    return p


def alert_list(title, w, h):
    return {"type": "alertlist", "title": title, "gridPos": {"x": 0, "y": 0, "w": w, "h": h},
            "description": "Alertas activas de Alertmanager. Cada una tiene su runbook en observability/runbooks/.",
            "options": {"alertInstanceLabelFilter": "", "datasource": "Alertmanager", "groupMode": "default",
                        "maxItems": 20, "showInstances": True, "sortOrder": 1,
                        "stateFilter": {"firing": True, "pending": True, "normal": False}, "viewMode": "list"}}


def layout(sections):
    """Assigns id and gridPos. sections: [(title of the row or None, collapsed, [line, ...])]; line: [panel, ...]."""
    out, y, next_id = [], 0, 1
    for title, collapsed, lines in sections:
        row = None
        if title is not None:
            row = {"id": next_id, "type": "row", "title": title, "collapsed": collapsed,
                   "gridPos": {"x": 0, "y": y, "w": 24, "h": 1}, "panels": []}
            next_id += 1
            out.append(row)
            y += 1
        for line in lines:
            x, height = 0, 0
            for p in line:
                p["id"] = next_id
                next_id += 1
                p["gridPos"].update({"x": x, "y": y})
                x += p["gridPos"]["w"]
                height = max(height, p["gridPos"]["h"])
                (row["panels"] if collapsed else out).append(p)
            assert x <= 24, f"The line of '{line[0]['title']}' is {x} columns wide"
            y += height
        if collapsed:
            y = row["gridPos"]["y"] + 1
    return out


# Local in blue and remote in orange in every panel of the comparison
PATH_COLORS = [
    {"matcher": {"id": "byRegexp", "options": ".*local.*"},
     "properties": [{"id": "color", "value": {"mode": "fixed", "fixedColor": "blue"}}]},
    {"matcher": {"id": "byRegexp", "options": ".*remote.*"},
     "properties": [{"id": "color", "value": {"mode": "fixed", "fixedColor": "orange"}}]},
]
FLAG_AXIS = [{"matcher": {"id": "byRegexp", "options": "flag.*"},
              "properties": [{"id": "custom.axisPlacement", "value": "right"}, {"id": "unit", "value": "percent"},
                             {"id": "min", "value": 0}, {"id": "max", "value": 100},
                             {"id": "custom.lineStyle", "value": {"fill": "dash", "dash": [10, 10]}},
                             {"id": "color", "value": {"mode": "fixed", "fixedColor": "purple"}}]}]
FLAG = ("catalog:rollout_remote_percentage:max", "flag % remoto", "Z")

estado = [
    [
        stat("Flag (% remoto)", 4, 4, "catalog:rollout_remote_percentage:max", unit="percent",
             description="catalog_rollout_remote_percentage: el porcentaje que aplica cada instancia (el máximo).",
             thresholds=[{"color": "blue", "value": None}, {"color": "orange", "value": 1}]),
        stat("Rollbacks del guard (24 h)", 4, 4,
             f"sum(increase(catalog_rollout_rollbacks_total{{{MONO}}}[24h])) or vector(0)",
             description="catalog_rollout_rollbacks_total. Con más de 0 no se vuelve a subir el porcentaje sin "
                         "revisar la causa (CatalogRolloutRolledBack).",
             thresholds=[{"color": "green", "value": None}, {"color": "red", "value": 1}]),
        stat("Llamadas remotas / min-calls", 4, 4,
             f"min(sum by (instance) (increase(catalog_lookup_seconds_count{{{MONO}, path=\"remote\"}}[2m])) "
             f"* on (instance) catalog_rollout_guard_window_seconds{{{MONO}}} / 120 "
             f"/ on (instance) catalog_rollout_guard_min_calls{{{MONO}}})", unit="percentunit",
             description="Llamadas remotas por ventana del guard sobre min-calls, en la instancia con menos tráfico. "
                         "Debajo de 100 % el guard no evalúa: CatalogRolloutGuardBlind.",
             thresholds=[{"color": "red", "value": None}, {"color": "green", "value": 1}]),
        stat("Línea base B (p95 búsqueda)", 4, 4, "search:latency_seconds:p95_baseline7d", unit="s",
             description="Mediana de los p95 de 5 min de los últimos 7 días con el flag en 0 %. Sin valor: "
                         "CatalogSearchLatency no puede disparar."),
        stat("Historia de B", 4, 4, "search:latency_seconds:p95_baseline7d_hours", unit="h",
             description="Horas con el flag en 0 % que usa B. El plan pide 168 (7 días).",
             thresholds=[{"color": "orange", "value": None}, {"color": "green", "value": 168}]),
        stat("p95 búsqueda ahora", 4, 4, "search:latency_seconds:p95_5m", unit="s",
             description="p95 de las búsquedas con 200 en los últimos 5 minutos."),
    ],
    [alert_list("Alertas activas", 24, 6)],
]

misma_ventana = [
    [
        panel("timeseries", "Llamadas por camino", 12, 8,
              [("catalog:lookup_calls:rate5m", "{{path}}"), FLAG], unit="reqps", overrides=PATH_COLORS + FLAG_AXIS,
              description="catalog.lookup por camino, en la misma ventana de 5 minutos. La línea punteada es el "
                          "flag: con 5 % las remotas tienen que ser ≈ 5 % del total."),
        panel("timeseries", "Errores por camino", 12, 8,
              [("catalog:lookup_error:ratio_rate5m", "{{path}}")], unit="percentunit", overrides=PATH_COLORS,
              thresholds=[{"color": "transparent", "value": None}, {"color": "orange", "value": 0.005},
                          {"color": "red", "value": 0.05}],
              description="result=error / total. 0,5 % sostenido (A2) y 5 % en una ventana (A1) son las reglas del "
                          "guard, que evalúa cada 10 s."),
    ],
    [
        panel("timeseries", "p95 y p99 por camino", 12, 8,
              [("catalog:lookup_latency_seconds:p95_5m", "p95 {{path}}", "A"),
               ("catalog:lookup_latency_seconds:p99_5m", "p99 {{path}}", "B"),
               ('catalog:lookup_latency_seconds:p95_baseline7d{path="local"}', "p95 local B (7 d)", "C")],
              unit="s", overrides=PATH_COLORS,
              description="Desde los buckets del histograma, sobre los mismos 5 minutos para los dos caminos. "
                          "CatalogRemoteLatencyVsLocal: p95 remoto > 3 × el local y > 10 ms."),
        panel("timeseries", "Media por camino", 12, 8,
              [("catalog:lookup_latency_seconds:mean_5m", "{{path}}")], unit="s", overrides=PATH_COLORS,
              thresholds=[{"color": "transparent", "value": None}, {"color": "red", "value": 0.05}],
              description="La métrica de A3 (media remota > 50 ms en 6 ventanas seguidas del guard)."),
    ],
]

busqueda = [
    [
        panel("timeseries", "p95 de la búsqueda contra B", 12, 8,
              [("search:latency_seconds:p95_5m", "p95 5 min", "A"),
               ("search:latency_seconds:p95_baseline7d", "B", "B"),
               ("1.1 * search:latency_seconds:p95_baseline7d", "B + 10 % (criterio de éxito)", "C"),
               ("1.2 * search:latency_seconds:p95_baseline7d", "B + 20 % (A4)", "D"), FLAG],
              unit="s", overrides=FLAG_AXIS,
              description="El reparto es por llamada, no por búsqueda: el p95 de la búsqueda no se separa por "
                          "camino. Se compara contra B, medida con el flag en 0 %."),
        panel("timeseries", "503 de la búsqueda", 12, 8,
              [("search:unavailable:ratio_rate5m", "503 / total")], unit="percentunit",
              thresholds=[{"color": "transparent", "value": None}, {"color": "orange", "value": 0.001},
                          {"color": "red", "value": 0.005}],
              description="Criterio de éxito ≤ 0,1 %; A5 dispara con > 0,5 % durante 10 minutos."),
    ],
    [
        panel("timeseries", "Búsquedas por status", 24, 7,
              [("search:requests:rate5m", "{{status}}")], unit="reqps"),
    ],
]

lote_cache = [
    [
        panel("timeseries", "Consultas al catálogo por búsqueda (1a)", 8, 7,
              [("catalog:lookups_per_search:rate5m", "consultas / búsqueda")],
              thresholds=[{"color": "transparent", "value": None}, {"color": "red", "value": 4}],
              description="catalog.lookup (después de la caché) sobre las búsquedas. Criterio de 1a: ≤ 4. Sin lote "
                          "eran ~11."),
        panel("timeseries", "Hit ratio de la caché (1b)", 8, 7,
              [("catalog:cache_hit:ratio_rate5m", "hit ratio")], unit="percentunit", min_max=(0, 1),
              thresholds=[{"color": "red", "value": None}, {"color": "transparent", "value": 0.95}],
              description="cache.gets de catalog-cities. Criterio de 1b: ≥ 95 % después de 10 min de calentamiento."),
        panel("timeseries", "Flag por instancia", 8, 7,
              [(f"catalog_rollout_remote_percentage{{{MONO}}}", "{{instance}}")], unit="percent", min_max=(0, 100),
              description="Lo que aplica cada instancia. Un rollback de una instancia llega a la otra por la tabla "
                          "catalog_rollout en ≤ 5 s."),
    ],
]

catalogo = [
    [
        panel("timeseries", "Requests del servicio por endpoint", 8, 8,
              [(f"sum by (uri, status) (rate(http_server_requests_seconds_count{{{CAT}}}[$__rate_interval]))",
                "{{uri}} {{status}}")], unit="reqps"),
        panel("timeseries", "p95 del servicio por endpoint", 8, 8,
              [(f"histogram_quantile(0.95, sum by (uri, le) (rate(http_server_requests_seconds_bucket{{{CAT}}}"
                f"[$__rate_interval])))", "{{uri}}")], unit="s",
              description="Del lado del servidor: la diferencia con el p95 remoto del monolito es la red y el "
                          "cliente HTTP."),
        panel("timeseries", "Pool de la base del catálogo", 8, 8,
              [(f"max(hikaricp_connections_active{{{CAT}}})", "activas", "A"),
               (f"max(hikaricp_connections_pending{{{CAT}}})", "pendientes", "B"),
               (f"max(hikaricp_connections_max{{{CAT}}})", "máximo", "C")],
              description="Criterio de 4b: pendientes = 0. El pool es de 5 conexiones."),
    ],
]

runtime = [
    [
        panel("timeseries", "Hilos de Jetty del monolito", 12, 7,
              [(f"sum by (instance) (jetty_threads_busy{{{MONO}}})", "ocupados {{instance}}", "A"),
               (f"max(jetty_threads_config_max{{{MONO}}})", "máximo", "B")]),
        panel("timeseries", "Pool de la base del monolito", 12, 7,
              [(f"max(hikaricp_connections_active{{{MONO}}})", "activas", "A"),
               (f"max(hikaricp_connections_pending{{{MONO}}})", "pendientes", "B")]),
    ],
]

dashboard = {
    "uid": "catalog-rollout",
    "title": "Rollout del catálogo",
    "description": "Activación progresiva del servicio de catálogo (M2 C4): flag, guard, comparación de los dos "
                   "caminos en la misma ventana y la búsqueda contra la línea base B.",
    "tags": ["twa", "flights", "catalog", "rollout"],
    "timezone": "browser",
    "schemaVersion": 41,
    "version": 1,
    "editable": False,
    "graphTooltip": 1,
    "refresh": "30s",
    "time": {"from": "now-3h", "to": "now"},
    "templating": {"list": []},
    "annotations": {"list": [
        {"builtIn": 1, "datasource": {"type": "grafana", "uid": "-- Grafana --"}, "enable": True, "hide": True,
         "name": "Annotations & Alerts", "type": "dashboard"},
        {"name": "Cambios del flag", "datasource": DS, "enable": True, "iconColor": "purple",
         "expr": "changes(catalog:rollout_remote_percentage:max[1m]) > 0", "step": "15s",
         "titleFormat": "Cambio del flag", "textFormat": "El porcentaje remoto cambió"},
        {"name": "Rollbacks del guard", "datasource": DS, "enable": True, "iconColor": "red",
         "expr": f"increase(catalog_rollout_rollbacks_total{{{MONO}}}[1m]) > 0", "step": "15s",
         "titleFormat": "Rollback {{rule}}", "textFormat": "CatalogRolloutGuard volvió a 0 %"},
    ]},
    "links": [{"title": "Runbook", "type": "link", "url": RUNBOOK, "targetBlank": True}],
    "panels": layout([
        ("Estado ahora", False, estado),
        ("Misma ventana: local contra remoto", False, misma_ventana),
        ("Búsqueda", False, busqueda),
        ("Lote, caché e instancias", False, lote_cache),
        ("Servicio de catálogo", False, catalogo),
        ("Runtime", True, runtime),
    ]),
}

out = Path(__file__).resolve().parent.parent / "grafana" / "dashboards" / "catalog-rollout.json"
out.write_text(json.dumps(dashboard, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
print(f"wrote {out}")
