# Módulo 2 · Clase 2 · Separa los datos

Pasamos de una base de datos compartida a datos por servicio. **Nunca se ejecuta contra producción: todo se prueba antes sobre una copia.** Una persona revisa entre cada prompt.

| Paso | Archivo | Rol | Alcance | Resultado |
|------|---------|-----|---------|-----------|
| 1 | [1-investigar.md](1-investigar.md) | Arquitecto de datos | Solo lectura | Dueño de cada tabla y cruces entre límites con su alternativa (API, evento o copia de lectura) |
| 2 | [2-ejecutar.md](2-ejecutar.md) | Desarrollador que separa los datos | Una rama, un script por etapa | Migraciones por etapa con su rollback, cambio de FK a código IATA y outbox con su publicador |
| 3 | [3-validar.md](3-validar.md) | Revisor escéptico de migraciones | Sobre una copia, conversación nueva | Tabla chequeo / evidencia / estado y veredicto: ¿se puede migrar? |

## Flujo

1. Ejecutá el prompt 1 y revisá la tabla de ownership: cada cruce tiene que tener una alternativa.
2. Ejecutá el prompt 2 una etapa por vez, siempre con su script de rollback, y probalo sobre una copia de la base.
3. Abrí una conversación nueva y ejecutá el prompt 3, que verifica conteos, dueños y el rollback sobre la copia.

En la próxima clase se define cómo se comunican los servicios: REST, eventos con RabbitMQ y tests de contrato.
