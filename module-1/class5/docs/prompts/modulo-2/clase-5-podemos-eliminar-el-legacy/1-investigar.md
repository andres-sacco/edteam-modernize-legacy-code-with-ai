# Prompt 1: investigar

**Rol:** Solo lectura. Reunir la evidencia para decidir.

## Prompt

```text
Rol: Actúa como ingeniero de confiabilidad que prepara la validación final de una migración.

Contexto: Métricas, logs y trazas de ambos caminos, el estado del flag y el código que aún referencia al camino viejo.

Tarea: Reunir la evidencia para decidir si el código local puede apagarse: comparación antes y después, casos límite y consumidores pendientes.

Restricciones: Solo lectura. Cada dato indica su fuente y su ventana de tiempo. Lo que no esté medido es "sin evidencia".

Formato: Informe con evidencia por señal, consumidores pendientes y riesgos abiertos.
```

## Tip

Adjuntá exportaciones de los dashboards, no solo descripciones. Pedile que marque como "sin evidencia" todo lo que no esté medido.

## Preparar lo que se adjunta

- Exportaciones de las métricas de ambos caminos (ver la clase 4), con su ventana de tiempo.
- Los logs del período evaluado: `docker logs twa-flights-monolith > analisis-logs.txt 2>&1`
- El código que todavía usa el camino viejo, por ejemplo buscando las clases e interfaces del módulo local del
  catálogo con `grep -rn` sobre `src/main/java`.
