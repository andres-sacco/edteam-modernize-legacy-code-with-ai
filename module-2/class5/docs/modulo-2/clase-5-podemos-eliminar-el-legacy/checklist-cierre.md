# Checklist de cierre del catálogo local (M2 · C5, paso 3)

**Veredicto: NO APROBADO.** No se apaga el código local. Ninguna condición de evidencia de tráfico está cumplida, el
código local es el destino del rollback y el servicio de catálogo todavía lee las tablas del monolito.

**Cómo se revisó:** solo lectura, 2026-11-18 a las 14:04 UTC, contra el stack levantado. Prometheus
(`localhost:9090`) tiene datos desde las 13:07:23 UTC (`prometheus_tsdb_lowest_timestamp_seconds` = 1795007243): unos
57 min. El "diff del cierre" es `antes-de-cerrar-catalogo-local..HEAD` (el tag apunta a `fb10e37`): **solo dos
documentos** (`informe-investigacion.md` y `plan-cierre.md`, +225 líneas). No hay código borrado que revisar.
`main...HEAD` no sirve como diff del cierre: `main` está muchos commits atrás e incluye todo el curso.

Estados: ✅ cumplida · ❌ no cumplida · ⚠️ parcial · ❔ no verificado.

## 1. Evidencia de tráfico y regresión

| # | Condición | Estado | Evidencia |
|---|---|---|---|
| 1 | Canario 4a (5 %, 24 h), 4b (25 %, 48 h), 4c (50 %, 48 h con pico) y 5 (100 %, 14 días) | ❌ | `max(catalog_rollout_remote_percentage)` = 0 ahora. `max_over_time(...[3h:1m])` = 100 solo por los simulacros de 13:12 y 13:41 (informe §1). Historial: 3 filas, todas del simulacro |
| 2 | Lote remoto (`/cities?codes=`) con llamadas exitosas | ❌ | `sum by (operation,path,result) (increase(catalog_lookup_seconds_count[1h]))`: `batch/remote/found` = **0**. Local batch = 7.755 |
| 3 | Error remoto en estado estable < umbral | ❔ | `single/remote/error` = 32 y total remoto error = 43, todos en el corte provocado. No hay ventana estable para medir |
| 4 | p99 remoto ≤ 50 ms en estado estable | ❔ | El único p99 (105,7 ms, informe §2) está inflado por los timeouts del simulacro |
| 5 | Línea base B de 7 días | ❌ | `p95_baseline7d` se calcula con ~57 min de historia. Prometheus usa un volumen (`prometheus-data`), pero no tiene más datos |
| 6 | Paridad de resultados local vs remoto (40014, itinerarios descartados) | ❌ | No existen `search.catalog.outcome` ni `provider.itineraries.*` (informe §2). Sin modo sombra |
| 7 | Disponibilidad del servicio ≥ 99,9 % en 14 días | ❌ | Hay ~57 min de `up`, con caídas provocadas |
| 8 | Logs sin errores del catálogo fuera de los simulacros | ⚠️ | `docker logs ... \| grep -c ERROR`: monolith 14 (0 del catálogo), monolith-2 68 (**51** del catálogo, atribuidos al simulacro de 13:41), catalog 0. No se pudo separar por hora con certeza; la ventana de logs empieza en el rolling restart (13:39) |
| 9 | Alertas activas | ✅ | `/api/v1/alerts`: solo `Watchdog` |
| 10 | Casos límite a través del monolito (`not_found`, mayúsculas, ancho completo) | ❔ | 0 llamadas `result=not_found` en ambos caminos. La prueba de humo pega directo al servicio |

## 2. Estacionalidad

| # | Condición | Estado | Evidencia |
|---|---|---|---|
| 11 | Pasó al menos una temporada alta con el remoto activo | ❌ | Toda la historia es del 2026-11-18, 13:07–14:04 UTC. Nada cubre diciembre–febrero ni julio (temporadas altas de BUE) |
| 12 | Pico de tráfico (criterio de 4c, p99 de req/s de la semana) | ❌ | No hay una semana de datos para calcular el pico |
| 13 | Tráfico representativo | ❌ | Sintético. `scripts/rollback-drill.sh:59` y `scripts/rolling-restart.sh:27` usan una sola ruta (`BUE,MIA`/`MIA,BUE`). `busquedas.tsv` solo guarda `epoch` y `status` |
| 14 | Comportamiento con la caché fría (reinicio en hora pico) | ❔ | Caché con 99,8 % de aciertos (informe §3): el remoto casi no se ejercita y el guard queda ciego (`CatalogRolloutGuardBlind`). Un reinicio con caché fría en un pico no se midió. Pool del catálogo = 5 conexiones (informe C4, fila 12) |

## 3. Referencias que sobreviven al camino viejo

| # | Condición | Estado | Evidencia |
|---|---|---|---|
| 15 | Ningún código de `src/main` usa el camino local | ❌ | `CatalogApiRouter.java:18,41,49,52` (importa e instancia `CatalogService`, `PATH_LOCAL`). `CatalogMode.java:8`. Fuera de `catalog/`, nadie importa `catalog.model`, `.repository` ni `.service` (grep vacío) |
| 16 | El valor por defecto no es `LOCAL` | ❌ | `application.yml:91` `${CATALOG_MODE:LOCAL}`, `docker-compose.yml:95` `CATALOG_MODE: LOCAL`. Hoy **todo** el tráfico productivo va por el camino local |
| 17 | Hay un rollback que no dependa del camino local | ❌ | El guard y el `PUT /catalog/rollout` llevan el flag a 0 % = local. `city_replica` (V1.13) no está conectada a la búsqueda |
| 18 | El servicio no lee las tablas del monolito | ❌ | `twa-flights-catalog/.../application.yml:38` `DB_NAME:flights`. La base `catalog` (línea 77) es la copia |
| 19 | Tests sin dependencia del camino local | ❌ | `CatalogServiceTest`, `CatalogServiceBatchTest`, `CatalogServiceUnavailableTest`, `CatalogApiRouterTest` y la suite en dos modos |
| 20 | Observabilidad sin comparación contra `path="local"` | ❌ | `catalog-rollout.rules.yml:82`, `catalog-rollout-alerts.rules.yml:77,79`, `build_dashboards.py:168` (panel "p95 local B (7 d)"), `catalog-rollout.test.yml:147-199` |
| 21 | El contrato de Microcks no depende de las semillas del monolito | ❌ | `microcks/generate-contracts.py:15-16` (informe §4, #9) |
| 22 | Documentación al día | ⚠️ | `docker-compose.yml:160` dice "does not receive traffic yet (catalog.mode=local)", desactualizado desde la clase 4 |

## 4. Archivo y reversibilidad

| # | Condición | Estado | Evidencia |
|---|---|---|---|
| 23 | Tag antes del cierre | ✅ | `git rev-parse antes-de-cerrar-catalogo-local` = `fb10e37`. ❔ Si está pusheado: no verificado (el plan dice que es solo local) |
| 24 | Backup de `flights` y `catalog` | ⚠️ | `backup-antes-de-cerrar.sql`, 138.910 bytes, "Dump completed on 2026-11-18 14:02:29": 2 bases, 18 `CREATE TABLE`, 13 líneas `TRIGGER`. Ignorado por git (`.gitignore:17`): solo existe en esta máquina |
| 25 | Restauración del backup probada | ❔ | No se restauró en ningún entorno |
| 26 | Un paso por commit, reversible con `git revert` | ✅ (vacío) | No hay pasos de borrado en la rama |

## 5. Señales operativas encontradas en la revisión

- **`flights-lb` está `unhealthy`** (`docker ps`). El healthcheck (`docker-compose.yml:150`) hace
  `wget http://localhost:8080/...` dentro del contenedor y falla con "Connection refused", aunque desde el host
  `localhost:8080/api/flights/actuator/health` da 200. Probablemente `localhost` resuelve a IPv6 y nginx escucha solo
  en IPv4 (`flights-lb.conf:13`, `listen 8080;`). No es del catálogo, pero un balanceador que se ve caído invalida
  cualquier alerta o drill que dependa de su estado. No se cambió.
- **Inconsistencia de fuentes** (informe §2): durante el simulacro de 13:41 el historial registra 18 llamadas remotas
  y Prometheus ninguna. Prometheus no es fuente completa durante reinicios.

## Veredicto final

**NO APROBADO.** 2 condiciones cumplidas de 26 (alertas y tag). Las razones de mayor peso para no apagar:

1. No hay ni un minuto de tráfico remoto en estado estable: el remoto solo corrió dentro de dos cortes provocados.
2. El lote remoto tiene **cero** respuestas exitosas medidas.
3. Ninguna estacionalidad cubierta: ~1 h de datos sintéticos de una ruta, un día de noviembre.
4. Apagar el código local elimina el único rollback.
5. El servicio de catálogo depende de las tablas `flights.city/country/continent`. Borrar el camino local no reduce el
   acoplamiento de datos.

## Pendientes, en orden

1. Arreglar o explicar el healthcheck de `flights-lb` antes de cualquier canario.
2. Instrumentar `search.catalog.outcome` y `provider.itineraries.*` (paridad).
3. Definir y probar con `scripts/rollback-drill.sh` un rollback sin camino local (`city_replica` o versión anterior
   del servicio).
4. Correr 4a → 4b → 4c → 5 con tráfico representativo (varias rutas, códigos desconocidos, mayúsculas y ancho
   completo) y exportar Prometheus de cada ventana, incluyendo al menos una temporada alta o una prueba de carga del
   pico, con la caché fría.
5. Pasar las lecturas del servicio a la base `catalog` y sacar la dependencia del Flyway del monolito.
6. Probar la restauración de `backup-antes-de-cerrar.sql` y guardarlo fuera de esta máquina. Rehacerlo el día del
   cierre.
7. Volver a correr el paso 1 y esta checklist. Recién con todo en ✅ se ejecuta el plan de cierre (pasos A → E).
