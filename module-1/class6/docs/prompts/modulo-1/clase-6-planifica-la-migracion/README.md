# Módulo 1 · Clase 6 · Planifica la migración

Cerramos el módulo 1 con un plan para extraer el catálogo de ciudades de forma gradual y con vuelta atrás. **Todavía no se migra nada: el resultado de esta clase es un plan revisado.** Una persona revisa entre cada prompt.

| Paso | Archivo | Rol | Alcance | Resultado |
|------|---------|-----|---------|-----------|
| 1 | [1-investigar.md](1-investigar.md) | Arquitecto de migraciones (Strangler Fig, Branch by Abstraction) | Solo lectura | Consumidores, datos, riesgos y preguntas abiertas de extraer el catálogo |
| 2 | [2-ejecutar.md](2-ejecutar.md) | Arquitecto que redacta el plan | Escribe solo en `/docs` | Roadmap con flags, métricas, criterios de avance y rollback, y un ADR |
| 3 | [3-validar.md](3-validar.md) | Revisor escéptico | Solo lectura, conversación nueva | Tabla paso / riesgo / evidencia / severidad / recomendación |

## Flujo

1. Ejecutá el prompt 1 y revisá el informe con el equipo, separando llamadas externas (Gateway) de internas (interfaz).
2. Ejecutá el prompt 2 con el informe corregido. En esta clase "ejecutar" significa redactar el plan en `/docs`.
3. Abrí una conversación nueva y ejecutá el prompt 3, que simula el peor escenario de cada paso.

El plan resultante es lo que se ejecuta en el módulo 2: extraer el catálogo de ciudades, separar los datos, conectar los servicios y activar con feature flags.
