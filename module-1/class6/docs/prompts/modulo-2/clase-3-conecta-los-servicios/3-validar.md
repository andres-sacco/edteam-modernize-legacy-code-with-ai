# Prompt 3: validar

**Rol:** Revisor escéptico. Intenta romper la integración.

## Prompt

```text
Rol: Actúa como un revisor escéptico de integraciones entre servicios.

Contexto: Contratos, cliente, consumidor y tests, con el catálogo y el broker disponibles para pruebas.

Tarea: Verificar que cada contrato tiene test, simular la caída del servicio y probar eventos duplicados y desordenados.

Restricciones: Evidencia con archivo, línea o resultado de la prueba. No corrijas nada. Lo no verificable es "no verificado".

Formato: Tabla: chequeo, evidencia, estado y severidad, más un veredicto: ¿se puede cerrar la integración?
```

## Tip

Ejecutalo en una conversación nueva. Pedile pruebas concretas: apagar el catálogo, enviar el mismo evento dos veces y enviar dos actualizaciones en orden inverso.

```bash
# Caída del catálogo con el stack levantado
docker compose stop <servicio-catalogo>
./scripts/smoke-test.sh
docker compose start <servicio-catalogo>
```

Para duplicados y desorden, publicá los mensajes a mano desde la consola de RabbitMQ (http://localhost:15672) o con
`rabbitmqadmin`, repitiendo el mismo identificador de mensaje y alterando el orden de dos versiones.
