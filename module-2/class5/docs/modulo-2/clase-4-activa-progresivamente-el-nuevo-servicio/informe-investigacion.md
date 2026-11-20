# Informe de investigación: métricas para activar progresivamente el catálogo (M2 · C4)

Paso 1 de la clase (solo lectura). Qué métricas comparar entre el camino local (`CatalogService`) y el remoto
(`CatalogClient`), con su valor base, su fuente y el riesgo de cada etapa del
[plan de extracción](../../modulo-1/clase-6-planifica-la-migracion/plan-de-extraccion.md).

## Cómo se midió (2026-11-18)

- Stack de `docker compose` (`CATALOG_MODE=LOCAL`, batch y caché apagados, una instancia de cada servicio).
- Tráfico: 100 búsquedas válidas (`BUE,MIA ↔ MIA,BUE`), 20 búsquedas con `XXX` (error 40014), 200 llamadas a
  `GET /cities?codes=` con 10 códigos y 100 a `GET /cities/{code}` del servicio de catálogo.
- No se cambió código ni configuración.

**Antes de leer la tabla:** los percentiles salen de `curl` del lado del cliente. Actuator sólo da `COUNT`,
`TOTAL_TIME` y `MAX`, sin histograma, y Prometheus no está expuesto. Son valores de un stack local con Microcks. No son
la línea base de 7 días que pide el plan.

## Tabla de métricas

| # | Métrica (local vs remoto) | Valor base | Fuente | Estado | Riesgo asociado (etapa) |
|---|---|---|---|---|---|
| 1 | Latencia de la búsqueda, 200 | p50 26,9 ms · p95 45,9 ms · p99 54,2 ms · máx 777 ms (warm-up). Promedio del servidor 34,6 ms | `curl` (100 pedidos) + `http.server.requests{uri=/clusters/itineraries,status=200}` | Medido (cliente). **p95 del servidor: hipótesis** (no hay histograma) | 4a–5: el criterio "p95 ≤ control + 10 %" no se puede evaluar sin percentiles del servidor ni `path` |
| 2 | Latencia del rechazo 40014 | p50 2,5 ms · p95 5,6 ms · promedio 2,1 ms | `curl` + `http.server.requests{status=400}` | Medido | 4: en el remoto, cada 400 suma un viaje HTTP |
| 3 | Tasa de 5xx de la búsqueda | 0 de 121 (no hay series `status=500` ni `503`) | `http.server.requests` (la consulta con `status:500` da 404) | Medido | 1/4: hoy un catálogo caído podría devolver 200 con la lista vacía, no 503 |
| 4 | Tasa de 40014 | Con este tráfico no tiene sentido (la mezcla es artificial) | `http.server.requests{status=400}` mezcla el 40014 con las otras validaciones | **Hipótesis.** Falta `search.catalog.outcome` | 4: deriva por mayúsculas o códigos de 2 letras (`MI`) entre caminos. No se puede separar del resto de los 400 |
| 5 | Itinerarios descartados por proveedor | **14 `ERROR` "calculating the flight duration"** en ~100 búsquedas (Alpha, Beta y Gamma) | `docker logs twa-flights-monolith` + `logback.events{level=error}`=14 | Medido como log. **Tasa y causa: hipótesis** (no existen `provider.itineraries.discarded` ni `.received`) | **El más grave (4a):** ya hay descartes con el catálogo local, así que la regla "`catalog_unavailable` = 0" no se distingue de los descartes normales. Alpha ni siquiera loguea la excepción |
| 6 | Consultas al catálogo por búsqueda | ~11 `findByCode` por búsqueda (1142 invocaciones). Promedio 1,1 ms y máx 47 ms cada una | `spring.data.repository.invocations{repository=CityRepository,method=findByCode}` | Medido | 4: en remoto sin lote son ~11 HTTP por búsqueda. A unos 3 ms cada uno suma ~+33 ms al p50, y eso rompe "≤ control + 10 %" |
| 7 | Latencia del servicio, `GET /cities/{code}` | p50 2,9 ms · p95 3,8 ms · p99 5,3 ms | `curl` | Medido | 4: es el costo de cada llamada mientras `batch=false` |
| 8 | Latencia del servicio, `GET /cities?codes=` (10 códigos) | p50 5,4 ms · p95 7,8 ms · p99 10,3 ms · máx 148 ms. Servidor: promedio 5,0 ms, máx 109 ms | `curl` + `http.server.requests{uri=/cities}` del catálogo | Medido con carga secuencial. **El criterio a 200 req/s: hipótesis** | 2: el plan pide p99 ≤ 30 ms a 200 req/s y nunca se probó con carga |
| 9 | Llamadas HTTP del monolito al catálogo | 300 llamadas, todas `client.name=microcks` (proveedores), `uri=none` | `http.client.requests` | Medido. **Que aparezca con `client.name` del catálogo: hipótesis** | 3/4: con `uri=none` no hay latencia por endpoint. Se separa sólo por `client.name` |
| 10 | Peor caso de un pedido remoto | 2 × (500 + 500 ms) + 100 ms ≈ 2,1 s por request | `application.yml` (`catalog.remote.*`) | Calculado | 4: sin lote son hasta ~11 de estos en serie, decenas de segundos, contra el `readTimeout` de 5 s de los proveedores |
| 11 | Pool de la base del monolito | activas 0 · pendientes 0 · máx 20 | `hikaricp.connections.*` | Medido (foto puntual) | 1a/4: el N+1 presiona el pool en el local. El remoto lo alivia |
| 12 | Pool de la base del catálogo | pendientes 0 · máx **5** | `hikaricp.connections.*` del catálogo | Medido sin carga | 4b–4c: con un pool de 5 y ~11 consultas por búsqueda, es el primer cuello de botella en un pico |
| 13 | Hilos y memoria del monolito | `jvm.threads.live` 70 · `jvm.memory.used` 288 MB · `jetty.threads.busy` 7 de 50 | Actuator | Medido (foto) | 1b/4: los hilos bloqueados en HTTP al catálogo agotan Jetty |
| 14 | Hit ratio de la caché | No existe (`catalog.cache.enabled=false`) | `cache.gets` aparecería con la caché encendida | **Hipótesis** | 1b: sin línea base no se puede validar el ≥ 95 % |
| 15 | Comparaciones en sombra | No existe | `catalog.shadow.comparisons` está en el plan, no en el código | **Hipótesis** | 3: la etapa no se puede ejecutar |
| 16 | Errores en los logs del catálogo | 0 | `logback.events{level=error}` + `docker logs` | Medido | 2: es la señal de que el servicio está sano sin tráfico |
| 17 | Eventos CiudadActualizada | 86 publicados, 86 aplicados (`result=applied`) | `catalog.events.published` y `catalog.events.consumed` | Medido | Ninguno por ahora: la búsqueda no lee `city_replica` |
| 18 | Atraso de la copia (outbox) | pendientes 0 · promedio 11,3 s · máx 13,5 s (339 eventos) | `catalog.outbox.pending` y `catalog.outbox.lag` | Medido. Probablemente incluye la carga inicial: **hipótesis** | 7: el plan pide p99 ≤ 5 s. El promedio ya lo supera; hay que confirmarlo sin la carga inicial |

## Riesgos de cada etapa

| Etapa | Riesgo principal | ¿Se puede medir hoy? |
|---|---|---|
| 0 Línea base | No hay métricas nuevas (`catalog.lookup`, `search.catalog.outcome`, `provider.itineraries.*`) ni historia | No |
| 1a/1b Lote y caché | Encenderlas cambia el nº de consultas (~11 → ≤ 4) y por lo tanto la línea base | Parcial (filas 6 y 11) |
| 2 Sin tráfico | El p99 a 200 req/s no está probado y el pool del catálogo es de 5 | Parcial (fila 8) |
| 3 Sombra | No hay modo `shadow` ni métrica de comparación | No |
| 4 Canario | No hay `catalog.remote.percentage` ni tag `path`. Los descartes ya existen hoy (fila 5). Sin lote, la latencia sube (filas 6 y 10) | No |
| 5 100 % remoto | Una sola instancia: la "caída controlada sin 503" no es posible | No |

## Bloqueos

1. **El flag no es de runtime.** `CatalogApiRouter` lee `catalog.mode` con `@Value` en el constructor
   ([CatalogApiRouter.java](../../../twa-flights-monolith/src/main/java/com/twa/flights/catalog/client/CatalogApiRouter.java)).
   Volver atrás requiere reiniciar, y el plan pide rollback sin deploy en ≤ 5 min (prerrequisito P2).
2. **El flag sólo tiene `LOCAL` y `REMOTE`.** Faltan `shadow` y el porcentaje que necesitan las etapas 3 y 4.
3. **Prerrequisitos P1 y P3 sin cumplir.** Hay una sola instancia y no hay Prometheus, así que no hay percentiles ni
   ventanas de 5 min.
4. **Descartes que ya existen en el camino local.** Las 14 líneas `ERROR` de duración de vuelo aparecen con `LOCAL`. Hay
   que conocer su tasa base antes de usar "200 con lista vacía" como alerta. Si no, `CatalogSearchEmptyByCatalog` va a
   disparar en falso o se va a ignorar.

## Archivos de esta carpeta

- `analisis-metricas-disponibles.json`: lista de métricas de Actuator del monolito.
- `analisis-latencia-busqueda.json` y `analisis-errores-busqueda.json`: están **vacíos**, se generaron sin tráfico. Los
  valores de este informe salen de las consultas a Actuator hechas después de generar el tráfico descrito arriba.
