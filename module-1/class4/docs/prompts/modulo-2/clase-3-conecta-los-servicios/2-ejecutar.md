# Prompt 2: ejecutar

**Rol:** Una rama, un paso por vez. Genera clientes, contratos y eventos.

## Prompt

```text
Rol: Actúa como el desarrollador que implementa las integraciones aprobadas.

Contexto: La tabla de decisión revisada, CatalogoApi y el servicio de Catálogo en una rama nueva.

Tarea: Generar el cliente REST con timeout y reintentos, el contrato OpenAPI y el evento CiudadActualizada con su consumidor.

Restricciones: Sin lógica de negocio en el cliente. Evento versionado y consumidor idempotente. Un paso por commit.

Formato: Clientes Java, OpenAPI, esquema JSON del evento y un resumen de lo creado.
```

## Tip

Pedí el esquema del evento con un campo de versión y un identificador único de mensaje, para que el consumidor pueda detectar duplicados.

## Antes de ejecutarlo

```bash
git switch -c integration/catalogo
```

- El stack actual no tiene broker: hay que agregar RabbitMQ a `docker-compose.yml` y a
  `docker-compose-infrastructure.yml` (por ejemplo la imagen `rabbitmq:3-management`, puertos 5672 y 15672).
- Los proveedores ya usan contratos OpenAPI en Microcks (`microcks/`); el contrato del catálogo puede seguir el
  mismo enfoque. Microcks también soporta AsyncAPI para mockear y probar el evento.
