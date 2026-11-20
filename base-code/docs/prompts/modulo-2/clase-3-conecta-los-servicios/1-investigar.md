# Prompt 1: investigar

**Rol:** Solo lectura. Decidir REST o evento por flujo.

## Prompt

```text
Rol: Actúa como arquitecto de integración de microservicios con Spring Boot y RabbitMQ.

Contexto: Los flujos entre el Catálogo de ciudades y el monolito (Búsqueda y Precios), con sus datos y consumidores.

Tarea: Clasificar cada flujo como REST o evento, justificarlo e indicar qué falla si el otro lado no responde.

Restricciones: Solo lectura. Cada flujo cita archivo y clase. Minimizar el acoplamiento temporal entre servicios.

Formato: Tabla de decisión por flujo: tipo, justificación, riesgo y qué pasa si el otro servicio falla.
```

## Tip

Pedile que justifique cada decisión con la pregunta "¿necesita la respuesta para seguir?". Revisá la tabla con el equipo antes de generar nada.

## Preparar lo que se adjunta

- El informe de ownership y cruces de la clase 2 y el diff de la extracción de la clase 1.
- Las llamadas actuales del monolito al catálogo:

```bash
git diff --stat main...HEAD
```
