# M2 · C2 · Separa los datos: migraciones por etapa (paso 2)

Ejecución de la separación de datos aprobada en el [informe de investigación](informe-investigacion.md) (cruces 2 y 4,
notas 1 a 3) y en el ADR-002 (D2: primero una copia de lectura, después la base propia). Una etapa por migración, cada
una con su rollback y su commit. Hoy `flights` sigue siendo la primaria del catálogo: la base `catalog` es una
**copia de lectura** que se mantiene con el outbox. Las lecturas del servicio no cambian.

## Etapas

| # | Qué | Script | Rollback | Commit |
|---|-----|--------|----------|--------|
| 1 | `UNIQUE` en `continent.code`, `country.code` y `city.code` (nota 1). DDL en línea (`INPLACE`, `LOCK=NONE`) | `V1.8__unique_catalog_codes.sql` | `U1.8` | `feat(db): unique codes ...` |
| 2 | **FK a código, expand:** `city.country_code` y `country.continent_code`, backfill desde los ids, FKs por código y triggers que mantienen id y código en sincronía | `V1.9__catalog_fk_by_code_expand.sql` | `U1.9` | `feat(db): references ... expand` |
| 3 | **FK a código, contract:** se borran las FKs por id de V1.0. Las columnas por id quedan (las leen las entidades y la versión anterior) | `V1.10__catalog_fk_by_code_contract.sql` | `U1.10` | `feat(db): references ... contract` |
| 4 | **Outbox:** `catalog_outbox` escrito por triggers en la misma transacción que cada cambio, con carga inicial de todas las filas | `V1.11__create_catalog_outbox.sql` | `U1.11` | `feat(db): outbox ...` |
| 2b | **Corrección de V1.9:** los triggers de update comparan los códigos con `COLLATE utf8mb4_0900_bin` en lugar de `BINARY`, que MySQL marca como deprecado. Se crean los triggers nuevos antes de borrar los viejos, así siempre hay un trigger activo | `V1.12__catalog_code_triggers_without_binary.sql` | `U1.12` | `fix(db): triggers ... without BINARY` |
| 5 | **Base propia y publicador:** base `catalog` con Flyway propio (`db/copy`), `CatalogOutboxPublisher` detrás de `catalog.copy.enabled` | `twa-flights-catalog/.../db/copy/V1.0__create_catalog_copy_tables.sql` | `U1.0` o el flag | `feat(catalog): own database ...` |

Los scripts del monolito están en `twa-flights-monolith/src/main/resources/db/migration` y los rollbacks en
`db/rollback` (procedimiento en su [README](../../../twa-flights-monolith/src/main/resources/db/rollback/README.md)).

## Decisiones

- **El código es la clave de integración** (cruce 4). Las referencias del catálogo apuntan a `code`; los ids se
  mantienen iguales en las dos bases (D2) para que las entidades actuales funcionen sin cambios cuando cambien las lecturas.
- **Expand/contract sin downtime:** ninguna etapa cambia una columna que lea la versión anterior del monolito o del
  servicio. Las columnas por id se borran recién en M2 · C5 (D9).
- **Triggers para la sincronía y el outbox:** hoy el único escritor del catálogo es Flyway (semillas como V1.5, que
  solo conocen `country_id`). Un trigger corre en la misma transacción que la escritura: no hay cambio sin evento ni
  evento sin cambio, sin tocar a los escritores. Un id o código inexistente, o los dos de padres distintos, fallan con
  `SQLSTATE 45000`. Un código en minúsculas se guarda como lo tiene el padre (`'br'` → `'BR'`).
- **Publicador idempotente:** cada evento trae la fila completa y se aplica con un upsert por id, en la misma
  transacción que el checkpoint (en la base `catalog`). El usuario `catalog_reader` sigue siendo de solo lectura sobre
  `flights` (solo se le agregó `SELECT` sobre el outbox). Si un evento falla, el publicador no avanza.
- **Misma collation** (`utf8mb4_0900_ai_ci`) en la base nueva (nota 2).

## Verificación

Sobre una copia (`copia-flights.sql`, cargada en un MySQL aparte):

- Cada migración y cada rollback, en orden, dejan **idéntica la huella de datos** (MD5 de todas las filas del catálogo
  y `CHECKSUM TABLE` de pricing y búsquedas).
- Después de los cuatro rollbacks, el **esquema es idéntico al original** (`mysqldump --no-data --triggers`). Se
  reaplicaron las migraciones sin errores.
- Triggers: alta solo con id, solo con código y sin país; cambio de país por id y por código; cambio de id
  (`DELETE` + `UPSERT` en el outbox); rechazo de código inexistente, id inexistente, id y código de padres distintos,
  código duplicado sin distinguir mayúsculas y borrado de un país con ciudades.

En el stack (`docker compose up`):

- Tests del monolito (24 suites) y del servicio de catálogo en verde; `./scripts/smoke-test.sh`: todas OK.
- Paridad `flights` ↔ `catalog`: filas idénticas en las tres tablas (339 eventos aplicados).
- Un alta, un cambio y una baja en `flights.city` aparecen en `catalog` en menos de 5 s.
- Reaplicar el outbox desde el evento 0 deja la copia igual (idempotencia).
- `catalog.outbox.pending` = 0.

## Pendientes y riesgos

- **Orden del outbox:** el id se asigna en el insert y no en el commit. El publicador espera 2 s
  (`settle-ms`) antes de publicar un evento; una transacción más larga que eso con escritores concurrentes podría dejar
  un id menor detrás del checkpoint. Hoy no pasa (Flyway escribe en serie); con el ABM (etapa 6/7 del plan) la
  primaria pasa a `catalog` y el outbox cambia de dirección.
- **El outbox no se limpia:** son pocas filas. La limpieza va con el job de paridad o en M2 · C5.
- **Sin job de paridad automático:** la comparación de esta clase es manual (consultas de arriba).
- **`BINARY expr` deprecado:** V1.12 reemplaza los triggers de update de V1.9 por otros que usan
  `COLLATE utf8mb4_0900_bin`. Esa collation, igual que `BINARY`, distingue mayúsculas, minúsculas y acentos, y no
  ignora los espacios al final. V1.9 no se edita, porque Flyway rechaza una migración que cambió después de aplicarse.
  Por eso una base que arranca de cero todavía muestra el warning 1287 al aplicar V1.9. El warning no se repite en
  V1.12, y cuando V1.12 termina ya no queda ningún trigger con `BINARY`.
- **Tests de MySQL:** el contenedor de Testcontainers del monolito usa `--log-bin-trust-function-creators=1` porque
  su usuario no es root y crea triggers con el binlog encendido. En producción Flyway corre con root.
