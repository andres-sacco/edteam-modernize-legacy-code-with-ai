# Prompt 3: validar

**Rol:** Revisor escéptico. Verifica cada etapa.

## Prompt

```text
Rol: Actúa como un revisor escéptico: tu trabajo es encontrar por qué una etapa saldría mal.

Contexto: El plan, las alertas, los dashboards y los resultados de comparar ambos caminos.

Tarea: Confirmar que el rollback se probó, que las alertas disparan antes que el usuario note el problema y que se compara la misma ventana.

Restricciones: Evidencia con captura, consulta o resultado. No cambies nada. Lo no verificable queda "no verificado".

Formato: Tabla: chequeo, evidencia, estado y severidad, más un veredicto: ¿se puede avanzar de etapa?
```

## Tip

Ejecutalo en una conversación nueva y pedí evidencia concreta: la captura de la alerta disparada y el tiempo que tardó el rollback en staging.

## Evidencia a adjuntar

- La captura o el log de la alerta disparada en staging.
- El tiempo medido entre activar la condición de rollback y que el tráfico vuelva al camino viejo.
- Las métricas de ambos caminos sobre **la misma ventana de tiempo**.
