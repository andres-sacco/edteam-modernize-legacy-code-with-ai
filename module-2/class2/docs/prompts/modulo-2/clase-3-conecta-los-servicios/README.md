# Módulo 2 · Clase 3 · Conecta los servicios

Elegimos cómo se comunican los servicios entre sí: REST o eventos con RabbitMQ, con tests de contrato. **La IA propone, el equipo decide**: cada prompt termina con una revisión humana.

| Paso | Archivo | Rol | Alcance | Resultado |
|------|---------|-----|---------|-----------|
| 1 | [1-investigar.md](1-investigar.md) | Arquitecto de integración | Solo lectura | Tabla de decisión por flujo: REST o evento, justificación, riesgo y qué pasa si el otro falla |
| 2 | [2-ejecutar.md](2-ejecutar.md) | Desarrollador de integraciones | Una rama, un paso por vez | Cliente REST con timeout y reintentos, contrato OpenAPI y evento CiudadActualizada con su consumidor |
| 3 | [3-validar.md](3-validar.md) | Revisor escéptico de integraciones | Conversación nueva | Tabla chequeo / evidencia / estado / severidad y veredicto: ¿se puede cerrar la integración? |

## Flujo

1. Ejecutá el prompt 1 y revisá la tabla con el equipo antes de generar nada.
2. Ejecutá el prompt 2 un paso por commit: cliente, contrato y evento con su consumidor.
3. Abrí una conversación nueva y ejecutá el prompt 3, que intenta romper la integración con caídas, duplicados y mensajes desordenados.

En la próxima clase se mueve el tráfico del código local al servicio, de a poco y con feature flags.
