# Plan de cierre del catálogo local (M2 · C5, paso 2)

**Estado: bloqueado.** No se eliminó nada. El [informe de evidencia](informe-investigacion.md) da veredicto "no" y no
hay checklist de cierre aprobada. La regla del paso es "no eliminar nada con consumidores pendientes", y los tres
borrados (flag, código local y tablas) los tienen. En esta rama solo se **archivó** el punto de partida y se dejó el
cierre preparado: qué se borra, en qué orden, qué lo bloquea y cómo se revierte.

## 1. Lo que se archivó

| Qué | Dónde | Cómo se usa para volver |
|---|---|---|
| Tag del código antes del cierre | `antes-de-cerrar-catalogo-local` → `fb10e37` (solo local, no se pusheó) | `git switch -c restaurar antes-de-cerrar-catalogo-local` |
| Backup de las bases `flights` y `catalog` (esquema, datos y triggers) | `backup-antes-de-cerrar.sql` en la raíz, 2026-11-18 14:02 UTC. Git lo ignora | `docker exec -i -e MYSQL_PWD=<root> flights-db mysql -uroot < backup-antes-de-cerrar.sql` |
| Rama del cierre | `cleanup/catalogo-local` | — |

El backup es de una corrida de compose: la base no persiste, así que solo sirve mientras se guarde el archivo. Si el
cierre se ejecuta otro día, hay que repetir el backup justo antes.

## 2. Qué bloquea cada borrado

| Borrado | Consumidor pendiente (informe §4) | Condición para destrabar |
|---|---|---|
| Feature flag | El flag es el **único rollback**: el guard y el `PUT` lo ponen en 0 % (#2). El default es `LOCAL` (#3) | Etapas 4a → 4b → 4c → 5 medidas y un rollback que no dependa del camino local |
| Código local | `CatalogApiRouter` lo instancia como camino `local` (#1). Seis tests y la suite en dos modos (#7). Reglas de Prometheus que comparan contra `path="local"` (#8) | El flag eliminado antes (paso A) |
| Tablas `continent`, `country`, `city` | **El servicio de catálogo las lee** (`DB_NAME:flights`, #4). Los triggers de `catalog_outbox` alimentan la base `catalog` (#5). El contrato de Microcks sale de sus semillas (#9) | El servicio lee de la base `catalog` y la base `catalog` es la principal |

## 3. Orden del cierre, un PR por paso

Cada paso es un commit y un PR propio, se verifica con `mvn -B test` y `./scripts/smoke-test.sh`, y se revierte con
`git revert <commit>`. Los pasos D y E son de esquema: van en migraciones **nuevas** y su reversión es la `U1.<n>` de
`db/rollback/`, porque Flyway no deshace un `git revert`.

### Prerrequisitos (antes del paso A, no son parte del borrado)

1. Correr el canario 4a (5 %, 24 h), 4b (25 %, 48 h), 4c (50 %, 48 h con un pico) y 5 (100 %, 14 días), y guardar las
   exportaciones de Prometheus de cada ventana.
2. Pasar las lecturas de `twa-flights-catalog` a la base `catalog` y sacarle la validación del Flyway del monolito.
3. Definir el rollback sin camino local (por ejemplo, la réplica `city_replica` de V1.13 o una versión anterior del
   servicio) y probarlo con `scripts/rollback-drill.sh`.
4. Instrumentar `search.catalog.outcome` y `provider.itineraries.*` para medir la deriva.
5. Volver a correr el paso 1 de la clase y obtener la checklist aprobada del paso 3.

### A · Eliminar el feature flag

El router llama siempre al cliente remoto. Se borran:

- `catalog/client/CatalogRolloutFlag`, `CatalogRolloutGuard`, `CatalogMode` y
  `client/configuration/CatalogRolloutGuardConfiguration`.
- `catalog/controller/CatalogRolloutController`, `controller/documentation/CatalogRolloutResources`,
  `dto/CatalogRolloutDTO`, `dto/CatalogRolloutRequestDTO`, `repository/CatalogRolloutRepository` y
  `configuration/CatalogRolloutApiKeyInterceptor`, más su registro en `shared/configuration/WebConfiguration` y
  `SwaggerConfiguration`.
- Las claves `catalog.mode` y del rollout en `application.yml`, y `CATALOG_MODE` y la clave del rollout en
  `docker-compose.yml`.
- Observabilidad del rollout: `observability/prometheus/rules/catalog-rollout*.rules.yml`,
  `prometheus/tests/catalog-rollout.test.yml`, `grafana/dashboards/catalog-rollout.json`,
  `runbooks/catalog-rollout.md`, su ruta en `alertmanager.yml`, `build_dashboards.py` y `scripts/rollback-drill.sh`.
- Tests que fijan el flag: `CatalogApiRouterTest` y `CatalogUnavailableSearchTest` se reescriben.

Las tablas `catalog_rollout` y `catalog_rollout_history` quedan hasta el paso D.

### B · Eliminar el código local

- `catalog/service/CatalogService`, `repository/CityRepository`, `mapper/CityMapper` y `model/City`, `Country` y
  `Continent`.
- En `CatalogApiRouter`: el parámetro `CatalogService`, `RoutedCatalogApi` y `PATH_LOCAL`.
- Tests: `CatalogServiceTest`, `CatalogServiceBatchTest`, `CatalogServiceUnavailableTest` y el modo `LOCAL` de
  `AbstractIntegrationTest` (la suite deja de correr dos veces).
- Las reglas `p95_baseline7d{path="local"}` y `CatalogRemoteLatencyVsLocal`, si sobrevivieron al paso A.

Se mantienen: `CatalogApi`, `CatalogClient`, `CachedCatalogApi`, `MeteredCatalogApi`, `CityCodes`, los DTO y la
réplica (`CityReplicaService`, `CityReplicaRepository`, `CityUpdatedListener`).

### C · Archivar las tablas (migración `V1.15`)

`RENAME TABLE` a `archived_continent`, `archived_country`, `archived_city`, `archived_catalog_outbox`,
`archived_catalog_rollout` y `archived_catalog_rollout_history`, y `DROP TRIGGER` de los triggers del outbox (V1.11,
V1.12). La reversión `U1.15` las renombra de vuelta y recrea los triggers. `city_replica` no se toca: es la copia del
monolito.

### D · Borrar las tablas archivadas (migración `V1.16`)

Solo después de una ventana de retención sin accesos a `archived_*` (se mide con el `performance_schema` de MySQL o
el log de consultas). Antes, un backup nuevo de esas tablas. Para revertir, se restauran desde ese backup.

### E · Documentación y arquitectura final

- `CLAUDE.md` y el README del monolito: el catálogo pasa a ser solo el servicio (puerto 8081) y la base `flights`
  queda con pricing y búsquedas.
- `microcks/generate-contracts.py`: hoy lee las semillas V1.1 y V1.5 del monolito. Los archivos de migración no se
  borran, así que el contrato no cambia, pero la fuente pasa a ser la base `catalog`.
- El comentario de `docker-compose.yml` sobre el catálogo ("It does not receive traffic yet") y `docs/README.md`.

## 4. Arquitectura final, cuando se destrabe

```
                   ┌──────────────────────────┐   REST /cities   ┌───────────────────────┐
cliente ──> LB ──> │ twa-flights-monolith (x2)│ ───────────────> │ twa-flights-catalog   │
                   │ búsqueda · pricing       │                  │ dueño del catálogo    │
                   │ CatalogClient + caché    │ <── evento ───── │ outbox → RabbitMQ     │
                   │ city_replica             │ CiudadActualizada└──────────┬────────────┘
                   └───────────┬──────────────┘                             │
                         base flights                                 base catalog
               (pricing, cluster_search, city_replica)        (continent, country, city)
```

Hoy la arquitectura sigue siendo la de la clase 4: el monolito decide por flag entre el catálogo local y el servicio,
y el servicio lee la base `flights`.

## 5. Para mencionar, sin cambiarlo

- El comentario de `docker-compose.yml:160` dice que el servicio "does not receive traffic yet (catalog.mode=local)",
  pero desde la clase 4 recibe tráfico por el flag.
- La prueba de humo pega directo al servicio para los casos `not_found`: no cubre ese caso a través del monolito.
