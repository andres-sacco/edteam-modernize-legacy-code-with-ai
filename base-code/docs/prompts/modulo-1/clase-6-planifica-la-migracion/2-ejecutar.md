# Prompt 2: ejecutar

**Rol:** Escribe solo en `/docs`. Redactar el plan de extracción sin tocar código.

## Prompt

```text
Rol: Actúa como el arquitecto que redacta el plan de extracción aprobado por el equipo.

Contexto: El informe de investigación ya revisado, el context map y las métricas disponibles en ambos caminos.

Tarea: Generar en /docs el plan de extracción: pasos, flag de cada etapa, métricas, criterios de avance y de rollback.

Restricciones: No toques código. Cada paso reversible y sin downtime. Criterios con umbrales numéricos. Solo archivos en /docs.

Formato: Roadmap numerado en Markdown, con una tabla de etapas (flag, métricas, avance, rollback) y un ADR.
```

## Tip

En esta clase "ejecutar" significa redactar el plan: no se migra nada todavía. Pedí umbrales numéricos y un responsable por cada flag.

## Antes de ejecutarlo

- Adjuntá el informe del prompt 1 **ya revisado y corregido** por el equipo.
- Métricas disponibles hoy en el monolito: `/api/flights/actuator/metrics` (por ejemplo
  `http.server.requests`, `jvm.*`, `hikaricp.*`).
- Partí de un árbol de trabajo limpio (`git status`), así el prompt 3 puede comparar solo lo que generó este paso.
