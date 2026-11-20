# Prompt 2: ejecutar

**Rol:** Escribe solo en `/docs`. Documentar la división aprobada sin tocar código.

## Prompt

```text
Rol: Actúa como el arquitecto que documenta la división aprobada por el equipo.

Contexto: La propuesta de bounded contexts ya revisada y corregida por el equipo, y acceso al repositorio.

Tarea: Generar en /docs el context map (Mermaid), el glosario por contexto, las entidades compartidas con su dueño y un ADR del candidato elegido.

Restricciones: No toques código de producción ni tests. Solo crea o edita archivos en /docs, con un commit por archivo.

Formato: Archivos Markdown con diagramas Mermaid y, al final, un resumen de lo creado.
```

## Tip

Un ADR (Architecture Decision Record) deja registrado qué se decidió y por qué. Este prompt solo escribe documentación: ningún cambio de código.

## Antes de ejecutarlo

- Adjuntá la propuesta del prompt 1 **ya revisada y corregida** por el equipo y el negocio.
- Partí de un árbol de trabajo limpio (`git status`), así el prompt 3 puede comparar solo lo que generó este paso.
