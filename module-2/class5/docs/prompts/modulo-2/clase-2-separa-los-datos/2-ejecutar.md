# Prompt 2: ejecutar

**Rol:** Una rama, un script por etapa. Genera migraciones, outbox y rollback.

## Prompt

```text
Rol: Actúa como el desarrollador que ejecuta la separación de datos aprobada.

Contexto: El informe ya revisado, el servicio de Catálogo y una copia de la base para pruebas.

Tarea: Generar los scripts de migración por etapas, el cambio de FK a código IATA y la tabla outbox con su publicador.

Restricciones: Pasos reversibles, sin downtime ni pérdida de datos. Un script por etapa, con su script de rollback.

Formato: Scripts versionados, un commit por etapa y un resumen de lo creado.
```

## Tip

Pedí un script por etapa con su rollback y versionalos con Flyway o Liquibase. Ejecutalos primero sobre una copia de la base, nunca contra producción.

## Antes de ejecutarlo

```bash
git switch -c data/separacion-catalogo

# Copia de la base para probar los scripts
docker exec flights-db mysqldump -uroot -pmuppet flights > copia-flights.sql
```

- El proyecto ya usa Flyway (`src/main/resources/db/migration`, versiones `V1.x`). Cada etapa va en una **nueva**
  migración: las existentes no se editan (el hook del proyecto lo bloquea).
- Flyway Community no ejecuta rollbacks: pedí los scripts de rollback como archivos aparte (por ejemplo en
  `src/main/resources/db/rollback/`), fuera de la carpeta que Flyway aplica.
