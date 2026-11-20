# Prompt 3: validar

**Rol:** Revisor escéptico. Intenta que el plan falle.

## Prompt

```text
Rol: Actúa como un revisor escéptico: tu trabajo es encontrar por qué este plan fallaría.

Contexto: El plan en /docs, el código, la suite de tests y los datos que usa el catálogo de ciudades.

Tarea: Simular el peor escenario en cada paso; buscar dependencias sin definir, pasos sin rollback y datos sin plan.

Restricciones: Evidencia con archivo o sección del plan. No corrijas nada. Lo que no puedas verificar queda "no verificado".

Formato: Tabla: paso, riesgo, evidencia, severidad y recomendación (aprobar, ajustar o rehacer).
```

## Tip

Ejecutalo en una conversación nueva. Pedile el peor escenario de cada paso: qué pasa si el paso 3 falla con el 25% del tráfico, y cómo se vuelve atrás.

```bash
# <commit-base> es el commit anterior a los generados por el prompt 2
git diff --stat <commit-base>..HEAD
git diff --stat <commit-base>..HEAD -- . ':(exclude)docs'   # debe salir vacío
```
