# Prompt 2: ejecutar

**Rol:** Una rama, un paso por vez. Elimina el flag y el código viejo.

## Prompt

```text
Rol: Actúa como el ingeniero que ejecuta el cierre aprobado por el equipo.

Contexto: El informe ya revisado, la checklist de cierre y el repositorio en una rama nueva.

Tarea: Eliminar el feature flag y el código local, archivar las tablas viejas y actualizar la documentación y la arquitectura final.

Restricciones: Un paso por commit, reversible con git. Archivar antes de borrar. No elimines nada con consumidores pendientes.

Formato: Pull requests chicos, un resumen de lo eliminado y la documentación actualizada.
```

## Tip

Archivá primero el código y las tablas (tag en git y backup de la base) y recién después eliminá. Un pull request por paso para poder revertir cualquiera.

## Antes de ejecutarlo

```bash
# Archivar: tag del código y backup de la base
git tag antes-de-cerrar-catalogo-local
docker exec flights-db mysqldump -uroot -pmuppet flights > backup-antes-de-cerrar.sql

git switch -c cleanup/catalogo-local
```

- Eliminar tablas es un cambio de esquema: va en una **nueva** migración de Flyway, nunca editando una existente.
- Después de cada paso: `mvn -B test` y `./scripts/smoke-test.sh` con el stack levantado (skill `verify-app`).
