# Revisión escéptica: separación de los datos del catálogo

> Objetivo: probar que la migración **no** es segura. Se revisan las etapas 1 a 5 de
> [migraciones-por-etapa.md](migraciones-por-etapa.md): `V1.8` a `V1.12` del monolito y `db/copy/V1.0` del catálogo.
> Todo corre sobre una copia, nunca contra producción. Lo que no se pudo verificar queda como "no verificado".

| Ronda | Fecha | Commit revisado | Veredicto |
|---|---|---|---|
| 1 | 2026-11-17 | `d65f072` | **Sí, con condiciones**: un hallazgo alto en el procedimiento de rollback (fila 14), corregido después de la revisión y todavía sin commitear |

## Cómo se probó

- **Base:** un contenedor `mysql:8.0` descartable (`review-mysql`, puerto 3399, sin volumen y con
  `--log-bin-trust-function-creators=1`). No se usaron `flights-db` ni el stack de compose.
- **Datos:** `copia-flights.sql`, un dump de `flights` en la versión `1.7` (antes de la separación): 7 continentes,
  246 países, 86 ciudades, 3 políticas, 25 carriers y 0 búsquedas guardadas.
- **Migraciones:** las aplicó el **Flyway real**, con el jar del monolito apuntando a la copia
  (`--spring.datasource.url=jdbc:mysql://localhost:3399/flights`). Antes se comprobó que los SQL del jar son iguales a
  los de `src/main/resources` (`unzip -p … | diff`).
- **Copia de lectura:** el jar del catálogo con `CATALOG_COPY_ENABLED=true`. Su Flyway creó la base `catalog` y el
  `CatalogOutboxPublisher` real la llenó desde el outbox.
- **Usuarios:** `catalog_reader` y `catalog_writer`, creados a mano con los mismos grants que
  `docker/mysql/init/*.sh` y `docker/mysql/grants/grant-catalog-reader.sh`.
- **Huella de datos:** se tomó en V1.7, en V1.12, antes del rollback y después del rollback:
  - MD5 del `GROUP_CONCAT` ordenado por id de las **columnas originales** de `continent`, `country` y `city`;
  - `COUNT(*)` exacto por tabla y `CHECKSUM TABLE … EXTENDED` de pricing y `cluster_search`;
  - MD5 del dump de datos de pricing y `cluster_search` (`mysqldump --no-create-info --skip-extended-insert`);
  - esquema con `mysqldump --no-data --triggers --routines --skip-dump-date`, sin `AUTO_INCREMENT=`;
  - versiones de `flyway_schema_history`.
- **Rollback:** los scripts de `db/rollback`, a mano con el cliente `mysql`, en el orden del
  [README](../../../twa-flights-monolith/src/main/resources/db/rollback/README.md). Después se aplicaron de nuevo las
  migraciones y se probó un orden de rollback equivocado.
- Al final se borró el contenedor. No se cambió código durante la revisión.

### Consultas principales

```sql
-- Huella de las columnas originales (igual para country y city, con sus columnas)
SET SESSION group_concat_max_len = 100000000;
SELECT COUNT(*), MD5(GROUP_CONCAT(CONCAT_WS('|', id, code, name) ORDER BY id SEPARATOR '\n')) FROM continent;

-- Backfill: el código de cada fila es el código del padre por id
SELECT COUNT(*) FROM city c LEFT JOIN country k ON k.id = c.country_id WHERE NOT (c.country_code <=> k.code);

-- Paridad flights ↔ catalog (igual para continent y country)
SELECT (SELECT MD5(GROUP_CONCAT(CONCAT_WS('|', id, code, name, time_zone, IFNULL(country_id, '-'),
                                          IFNULL(country_code, '-')) ORDER BY id SEPARATOR '\n')) FROM flights.city)
     = (SELECT MD5(GROUP_CONCAT(CONCAT_WS('|', id, code, name, time_zone, IFNULL(country_id, '-'),
                                          IFNULL(country_code, '-')) ORDER BY id SEPARATOR '\n')) FROM catalog.city);

-- FKs cruzadas entre bases
SELECT COUNT(*) FROM information_schema.key_column_usage
WHERE referenced_table_name IS NOT NULL AND table_schema <> referenced_table_schema
  AND table_schema IN ('flights', 'catalog');
```

## Resultados

| # | Chequeo | Evidencia (consulta → resultado) | Estado | Severidad |
|---|---|---|---|---|
| 1 | Precondiciones de V1.8 | `GROUP BY code HAVING COUNT(*) > 1` en las 3 tablas → 0. Huérfanos (`LEFT JOIN … WHERE k.id IS NULL`) → 0. `city.country_id` y `country.continent_id` en NULL → 0. `commercial_policy_carrier.commercial_policy_id` en NULL → 0 | ✅ Verificado | — |
| 2 | Las migraciones se aplican | Flyway: `Successfully applied 5 migrations to schema flights, now at version v1.12`. La app arrancó, así que Hibernate `validate` acepta el esquema. V1.9 da 8 warnings 1287 (`BINARY expr`), ya documentados. Después de V1.12, 0 triggers con `BINARY` | ✅ Verificado | — |
| 3 | Conteos y checksums, V1.7 → V1.12 | Huella de las columnas originales **idéntica**: continent 7 / `86fb2a74…`, country 246 / `08a0d795…`, city 86 / `51515f63…`. `CHECKSUM TABLE`: `commercial_policy` 1343309033, `commercial_policy_carrier` 4292980825 y `cluster_search` 0, iguales en los dos. Dump de pricing: `8a67d869…` en los dos | ✅ Verificado | — |
| 4 | Backfill de las columnas por código | Código distinto al del padre por id → 0 en `country` y 0 en `city` | ✅ Verificado | — |
| 5 | Carga inicial del outbox | `COUNT(*)` = 339 = 7 + 246 + 86, todos `UPSERT`, y 339 agregados distintos. Los ids van de 1 a 348: son **huecos** del auto-increment, porque InnoDB reserva ids en bloque en un `INSERT … SELECT`. No faltan eventos | ✅ Verificado | Info |
| 6 | Paridad `flights` ↔ `catalog` | Conteos 7/7, 246/246 y 86/86. MD5 de todas las columnas, ids y códigos incluidos → `md5_igual = 1` en las 3 tablas. Checkpoint = `MAX(id)` del outbox | ✅ Verificado | — |
| 7 | Cambios en vivo (alta, cambio y baja) | Alta solo con código (`'ar'` se guarda como `AR`). Cambio de país por código. Cambio de id (9002 → 9003) y baja. Después de cada cambio, paridad = 1 en menos de 6 s | ✅ Verificado | — |
| 8 | Rechazos de los triggers y las FKs | Código inexistente → `ERROR 1644 (45000)`. Id y código de padres distintos → `1644 (45000)`. `'zzz'` cuando existe `ZZZ` → `1062 Duplicate entry`. Borrar `AR` con ciudades → `1451` (`fk_city_country_code`) | ✅ Verificado | — |
| 9 | Idempotencia del publicador | `UPDATE outbox_checkpoint SET last_event_id = 0` y re-publicación completa → paridad = 1, 0 `ERROR` en el log | ✅ Verificado | — |
| 10 | FKs cruzadas | FKs entre bases → **0**. Las 5 FKs están dentro de un mismo dueño: `fk_country_continent_code` y `fk_city_country_code` en cada base (catálogo) y `FKntp5a7llnodxdwlb4lyq37ol2` (pricing). No hay vistas ni rutinas. Los 13 triggers son de tablas del catálogo y solo leen o escriben tablas del catálogo (`continent`, `country`, `catalog_outbox`) | ✅ Verificado | — |
| 11 | Un solo dueño por tabla | **Lógicamente sí:** catálogo (`continent`, `country`, `city`, `catalog_outbox`), pricing (`commercial_policy`, `commercial_policy_carrier`) y búsqueda (`cluster_search`). En el código, las únicas escrituras al catálogo son del publicador sobre la base `catalog` ([CatalogCopyRepository.java](../../../twa-flights-catalog/src/main/java/com/twa/catalog/repository/CatalogCopyRepository.java)). **Físicamente todavía no:** (a) las 3 tablas del catálogo están en `flights` (primaria) y en `catalog` (copia); (b) `catalog_outbox` es del catálogo, pero vive en `flights` y lo crea el Flyway del monolito; (c) `flyway_schema_history` de `flights` es una sola historia para todos los módulos; (d) el monolito se conecta como `root` ([application.yml:24](../../../twa-flights-monolith/src/main/resources/application.yml)), así que nada impide que un módulo escriba las tablas de otro | ⚠️ Parcial | Media |
| 12 | Grants mínimos | `catalog_reader`: `SELECT` solo sobre `city`, `continent`, `country` y `catalog_outbox` de `flights`. `catalog_writer`: `ALL` solo sobre `catalog.*`. Los grants se replicaron a mano; no se ejecutó `grant-catalog-reader.sh` | ❓ No verificado en el stack | — |
| 13 | Rollback en el orden del README: U1.0 (`catalog`) → U1.12 → U1.11 → U1.10 → U1.9 → U1.8 | Esquema comparado con el de V1.7 → **idéntico** (`diff` vacío). Huella de datos antes y después → **idéntica**. Dump de pricing `8a67d869…`. Historia: `1.0 … 1.7`. La fila 9001, escrita durante la migración solo con código, queda con `country_id = 31`: no se pierden escrituras hechas mientras la migración estaba aplicada. U1.11 revoca el `SELECT` sobre el outbox. La base `catalog` queda con 0 tablas | ✅ Verificado | — |
| 14 | **Rollback en un orden equivocado** | El encabezado de U1.11 decía *"first of all"*, pero el README y U1.12 dicen que U1.12 va antes. Se corrió U1.11 → U1.10 → U1.9 sin U1.12. Quedan `trg_city_country_code_update_v2` y `trg_country_continent_code_update_v2`, que leen una columna que ya no existe. `UPDATE city SET name = 'x' WHERE id = 1` → `ERROR 1054 Unknown column 'country_code' in 'NEW'`: todo UPDATE de `city` y `country` falla. Además el monolito no arranca: `Detected resolved migration not applied to database: 1.9, 1.10, 1.11`, porque `1.12` sigue en la historia. **Corregido después de la revisión:** ver más abajo | ❌ → ✅ Corregido (sin commitear) | Alta |
| 15 | Referencias de los rollbacks | U1.11 remitía a `U-catalog-copy.sql`, que no existe. El archivo real es `twa-flights-catalog/src/main/resources/db/rollback/U1.0__create_catalog_copy_tables.sql`. **Corregido después de la revisión** | ❌ → ✅ Corregido (sin commitear) | Baja |
| 16 | Volver a aplicar después del rollback | Flyway: `Successfully applied 5 migrations … now at version v1.12` | ✅ Verificado | — |
| 17 | Rollback de la copia con el publicador encendido | No era una prueba planeada: U1.0 se corrió con el servicio todavía arriba. El publicador falló de forma segura (`BadSqlGrammarException … outbox_checkpoint`, 47 reintentos) y **no escribió nada en `flights`**, porque `catalog_reader` es de solo lectura | ✅ Verificado (falla segura) | Info |

## Correcciones después de la revisión

Solo cambian comentarios: el SQL que se probó en las filas 13 y 16 es el mismo.

- [U1.11](../../../twa-flights-monolith/src/main/resources/db/rollback/U1.11__create_catalog_outbox.sql): el
  encabezado dice que el script corre después de U1.12 y antes de U1.10, y explica por qué. Ahora apunta al rollback
  real de la copia.
- [U1.9](../../../twa-flights-monolith/src/main/resources/db/rollback/U1.9__catalog_fk_by_code_expand.sql): el
  encabezado nombraba U1.10 y U1.11, pero no U1.12. Ahora dice "after U1.12, U1.11 and U1.10".

Ahora los encabezados y el README dan el mismo orden: U1.0 (si se borra la copia) → U1.12 → U1.11 → U1.10 → U1.9 → U1.8.

## No verificado

- **Orden del outbox con escritores concurrentes:** el riesgo de `settle-ms` que documenta
  [migraciones-por-etapa.md](migraciones-por-etapa.md) no se reproduce con un solo escritor.
- **Bloqueos y tiempos con carga real:** `ALGORITHM = INPLACE, LOCK = NONE` en V1.8 y V1.10, y las FKs por código de
  V1.9, que usan el algoritmo de copia. Se probó sin tráfico concurrente y con pocas filas.
- **Representatividad de la copia:** `copia-flights.sql` es un dump local. `cluster_search` está vacía y pricing solo
  tiene los datos semilla. Producción puede tener otros datos, por ejemplo políticas creadas desde la administración.
- **La versión anterior de la app sobre el esquema migrado o revertido:** las entidades actuales no mapean las columnas
  `*_code`, y la app actual arrancó en V1.12. No se ejecutó un jar de la versión anterior.
- **Grants en el stack:** se replicaron a mano (fila 12).

## Algo raro que no es un error

- Los huecos de ids del outbox (fila 5) aparecen también cuando falla un insert: los rechazos de la fila 8 consumieron
  ids. El publicador ordena por id y no supone ids consecutivos, así que no pierde eventos.
- [migraciones-por-etapa.md](migraciones-por-etapa.md) dice "después de los cuatro rollbacks", pero son cinco
  (U1.8 a U1.12), más U1.0 en la base `catalog`.

## Veredicto: sí, con condiciones

Los datos no se pierden ni cambian. La copia `catalog` queda idéntica a `flights` y sigue los cambios en vivo. No
quedan FKs entre bases. El rollback en el orden documentado deja el esquema y los datos idénticos a V1.7, y las
migraciones se pueden aplicar de nuevo.

Condiciones para migrar en producción:

1. **Commitear las correcciones de los encabezados de U1.11 y U1.9** (fila 14). Sin ellas, un operador que sigue el
   script en lugar del README rompe todos los UPDATE del catálogo y el monolito no vuelve a arrancar.
2. **Repetir las filas 1, 3 y 6 sobre un snapshot real de producción**, no sobre esta copia. Sobre todo, buscar códigos
   duplicados sin distinguir mayúsculas ni acentos, porque hacen fallar V1.8.
3. **Correr `grant-catalog-reader.sh` real** en el stack y revisar `SHOW GRANTS` (fila 12).

Sobre el alcance: esta etapa **no** termina la separación de los datos. `flights` sigue siendo la primaria, las tablas
del catálogo están duplicadas y el usuario `root` del monolito no respeta los límites (fila 11). Para que cada tabla
tenga un solo dueño falta el cambio de dirección de las etapas 6 y 7 del plan.
