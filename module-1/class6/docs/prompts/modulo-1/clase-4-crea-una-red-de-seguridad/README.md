# Módulo 1 · Clase 5 · Crea una red de seguridad

Antes de seguir moviendo código, necesitamos tests que avisen si rompemos algo. **Los tests no tocan el código de producción: documentan lo que ya existe.** Una persona revisa entre cada prompt.

| Paso | Archivo | Rol | Alcance | Resultado |
|------|---------|-----|---------|-----------|
| 1 | [1-investigar.md](1-investigar.md) | Ingeniero de QA | Solo lectura | Comportamientos a proteger ordenados por riesgo, con tipo de test y dificultad |
| 2 | [2-ejecutar.md](2-ejecutar.md) | Desarrollador que escribe los tests | Escribe solo en `src/test` | Tests JUnit 5 de caracterización, un commit por módulo |
| 3 | [3-validar.md](3-validar.md) | Revisor escéptico | Solo lectura, conversación nueva | Tabla test / hallazgo / evidencia / severidad y veredicto: ¿la red permite migrar? |

## Flujo

1. Ejecutá el prompt 1 y revisá la lista con alguien del negocio: ellos saben qué flujos no pueden fallar.
2. Ejecutá el prompt 2 módulo por módulo. Cada test se corre contra el sistema actual y tiene que pasar.
3. Abrí una conversación nueva y ejecutá el prompt 3, que intenta demostrar que la red tiene agujeros.

Con la red en su lugar, en la próxima clase se planifica la migración del catálogo de ciudades.
