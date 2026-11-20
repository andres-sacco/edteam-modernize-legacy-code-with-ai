# Prompt 1: investigar

**Rol:** Solo lectura. Qué medir y con qué valores base.

## Prompt

```text
Rol: Actúa como SRE especializado en migraciones progresivas.

Contexto: Métricas actuales del monolito y del Catálogo de ciudades, el feature flag y las etapas planificadas.

Tarea: Identificar qué métricas comparar entre ambos caminos, sus valores base y los riesgos de cada etapa.

Restricciones: Solo lectura. Cada métrica indica de dónde sale. Marca como hipótesis lo que hoy no se mide.

Formato: Tabla de métricas con valor base, fuente y riesgo asociado.
```

## Tip

Adjuntá capturas o exportaciones de los dashboards de ambos caminos. Sin valores base medidos, los umbrales que proponga la IA serán inventados.

## Preparar lo que se adjunta

- El plan de extracción de la clase 6 del módulo 1 (etapas y flags, en `/docs`).
- Las métricas que hoy expone el monolito por Actuator (`health`, `info` y `metrics`):

```bash
B=http://localhost:8080/api/flights/actuator/metrics
curl -s $B | python3 -m json.tool > analisis-metricas-disponibles.json
curl -s "$B/http.server.requests?tag=uri:/clusters/itineraries" > analisis-latencia-busqueda.json
curl -s "$B/http.server.requests?tag=uri:/clusters/itineraries&tag=status:500" > analisis-errores-busqueda.json
```

Generá tráfico antes de medir (por ejemplo, varias corridas de `./scripts/smoke-test.sh`): sin requests, las métricas
de `http.server.requests` no existen. Prometheus no está expuesto todavía.
