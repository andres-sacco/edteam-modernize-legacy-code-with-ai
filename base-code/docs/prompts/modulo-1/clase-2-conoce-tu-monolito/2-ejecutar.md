# Prompt 2: ejecutar

**Rol:** escribe solo en `/docs`. Generar el mapa sin tocar el código de producción.

## Prompt

```text
Rol: Actuá como el arquitecto que documenta lo investigado para que el equipo lo use como base de la migración.

Contexto: El informe de investigación ya revisado por el equipo, con sus correcciones, y acceso al repositorio.

Tarea: Generar en /docs el mapa de módulos y dependencias, el glosario del dominio, el catálogo de APIs y entidades y la lista de reglas ocultas.

Restricciones: No toques código de producción ni tests. Solo creá o editá archivos en /docs, con un commit chico por archivo.

Formato: Archivos Markdown con diagramas Mermaid y, al final, un resumen de los archivos creados.
```

## Tip

Pedí commits chicos y separados para poder revertir cualquier archivo por separado. Este prompt solo escribe
documentación: ningún cambio de código en esta clase.

## Antes de ejecutarlo

- Adjuntá el informe del prompt 1 **ya revisado y corregido** por una persona.
- Partí de un árbol de trabajo limpio (`git status`), así el prompt 3 puede comparar solo lo que generó este paso.
