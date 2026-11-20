# Prompt 3: validar

**Rol:** Revisor escéptico. Intenta refutar los límites.

## Prompt

```text
Rol: Actúa como un revisor escéptico: tu trabajo es refutar la división, no aprobarla.

Contexto: Los documentos de /docs, el código fuente y los dos flujos principales: buscar y cotizar.

Tarea: Recorrer una búsqueda y una cotización, contar los cruces de límites y buscar ciclos y entidades sin dueño.

Restricciones: Evidencia con archivo y línea. Sin evidencia, queda "no verificado". No corrijas nada ni cambies código.

Formato: Tabla: límite, hallazgo, evidencia, severidad y recomendación (mantener, ajustar o descartar).
```

## Tip

Ejecutalo en una conversación nueva, sin el contexto del prompt anterior. Corré `git diff --stat` para comprobar que solo cambiaron archivos de `/docs`.

```bash
# <commit-base> es el commit anterior a los generados por el prompt 2
git diff --stat <commit-base>..HEAD
git diff --stat <commit-base>..HEAD -- . ':(exclude)docs'   # debe salir vacío
```
