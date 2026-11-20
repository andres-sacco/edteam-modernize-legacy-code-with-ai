# Rollbacks de las migraciones

Flyway Community no ejecuta migraciones de undo: cada `U<versión>__<descripción>.sql` de esta carpeta deshace la
`V<versión>__<descripción>.sql` de `../migration` y se corre **a mano**. Flyway no lee esta carpeta.

## Reglas

1. **Primero sobre una copia** de la base (`docker exec flights-db mysqldump -uroot -pmuppet flights > copia-flights.sql`),
   nunca directo contra producción.
2. **En orden inverso:** se deshace primero la última etapa aplicada. Cada script dice qué tiene que estar deshecho antes.
3. **Primero la aplicación, después la base:** se despliega la versión del monolito sin la migración (o se apaga el
   flag) y recién después se corre el rollback. Cada script borra su fila de `flyway_schema_history`, así la versión
   con la migración la vuelve a aplicar si se despliega de nuevo.

```bash
docker exec -i flights-db mysql -uroot -pmuppet flights < twa-flights-monolith/src/main/resources/db/rollback/U1.11__create_catalog_outbox.sql
```

## Etapas de la separación de datos del catálogo (M2 · C2)

| Etapa | Migración | Rollback | Antes del rollback | ¿Pierde datos? |
|-------|-----------|----------|--------------------|----------------|
| 1 | `V1.8__unique_catalog_codes` | `U1.8` | U1.9 aplicado | No: borra índices |
| 2 | `V1.9__catalog_fk_by_code_expand` | `U1.9` | U1.10 aplicado | No: las columnas por código son copia de las columnas por id |
| 3 | `V1.10__catalog_fk_by_code_contract` | `U1.10` | U1.11 aplicado. El script lista las filas que impedirían recrear las FKs por id | No |
| 4 | `V1.11__create_catalog_outbox` | `U1.11` | U1.12 aplicado. Publicador apagado (`CATALOG_COPY_ENABLED=false`) | No: el outbox se deriva de las tablas del catálogo |
| 2 (corrección) | `V1.12__catalog_code_triggers_without_binary` | `U1.12` | Nada | No: solo cambia los triggers de update de V1.9, con la misma lógica. Hay que correrlo antes de U1.9, porque U1.9 borra los triggers por su nombre original |
| 5 | `twa-flights-catalog/.../db/copy/V1.0__create_catalog_copy_tables` | `twa-flights-catalog/.../db/rollback/U1.0__create_catalog_copy_tables` (sobre la base `catalog`) | Publicador apagado | No: la copia se reconstruye desde el outbox |
| M2 · C3 | `V1.13__create_city_replica` | `U1.13` | Consumidor apagado (`CATALOG_EVENTS_ENABLED=false`) | No: la réplica se reconstruye desde los eventos `CiudadActualizada` |

La etapa 5 también se vuelve atrás **sin tocar la base**: con `CATALOG_COPY_ENABLED=false` el servicio no abre la base
`catalog` ni lee el outbox, y sus lecturas siguen en `flights` como antes.

## Activación progresiva del servicio de catálogo (M2 · C4)

| Etapa | Migración | Rollback | Antes del rollback | ¿Pierde datos? |
|-------|-----------|----------|--------------------|----------------|
| Flag de runtime | `V1.14__create_catalog_rollout` | `U1.14` | Desplegada la versión sin el flag (vuelve a decidir `catalog.mode`) | Sí: el historial de cambios del flag (`catalog_rollout_history`). Exportarlo antes si hace falta |
