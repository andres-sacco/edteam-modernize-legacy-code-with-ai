# Prompt 3: validar

**Rol:** revisor escéptico. Verifica los cambios.

## Prompt

```text
Rol: Actuá como un revisor escéptico: tu trabajo es encontrar errores en el mapa, no confirmarlo.

Contexto: Los archivos generados en /docs, el código fuente y el diff de los commits.

Tarea: Verificar cada afirmación contra el código, confirmar que nada cambió fuera de /docs y detectar módulos inventados u omitidos.

Restricciones: Evidencia con archivo y línea. Sin evidencia, la afirmación queda "no verificada". No corrijas nada.

Formato: Tabla: afirmación, evidencia y estado (verificada, no verificada, incorrecta), más un veredicto final.
```

## Tip

Ejecutalo en una conversación nueva, sin el contexto del prompt anterior, para que no se confirme a sí mismo. Además,
corré `git diff --stat` para comprobar que solo cambiaron archivos de `/docs`.

```bash
# <commit-base> es el commit anterior a los generados por el prompt 2
git diff --stat <commit-base>..HEAD
git diff --stat <commit-base>..HEAD -- . ':(exclude)docs'   # debe salir vacío
```
