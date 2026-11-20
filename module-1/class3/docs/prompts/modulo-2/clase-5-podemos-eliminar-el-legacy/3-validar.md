# Prompt 3: validar

**Rol:** Revisor escéptico. Busca razones para no apagar.

## Prompt

```text
Rol: Actúa como un revisor escéptico: tu trabajo es encontrar razones para NO apagar el código local.

Contexto: El informe, la checklist, los dashboards y el diff del cierre.

Tarea: Buscar señales de regresión, referencias que sobreviven al camino viejo y estacionalidad todavía no cubierta.

Restricciones: Evidencia con consulta, panel o archivo. No apruebes si falta evidencia. Lo no verificable queda "no verificado".

Formato: Checklist de cierre con estado por condición, veredicto final y pendientes.
```

## Tip

Ejecutalo en una conversación nueva. Pedile que revise especialmente la estacionalidad (¿ya pasó una temporada alta?) y los consumidores que todavía usan el camino viejo.

```bash
git diff --stat main...HEAD
git diff main...HEAD > analisis-diff-cierre.txt
```
