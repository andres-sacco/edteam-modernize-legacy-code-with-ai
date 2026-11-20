# Módulo 2 · Clase 4 · Activa progresivamente el nuevo servicio

Migramos tráfico del código local al microservicio sin romper nada, con feature flags. **Todo se prueba en staging antes de tocar la primera etapa real.** Una persona revisa entre cada prompt.

| Paso | Archivo | Rol | Alcance | Resultado |
|------|---------|-----|---------|-----------|
| 1 | [1-investigar.md](1-investigar.md) | SRE de migraciones progresivas | Solo lectura | Tabla de métricas a comparar con valor base, fuente y riesgo |
| 2 | [2-ejecutar.md](2-ejecutar.md) | Ingeniero de monitoreo y rollback | Una rama, un cambio por vez | Etapas con umbrales, alertas, condición de rollback y configuración del flag |
| 3 | [3-validar.md](3-validar.md) | Revisor escéptico | Conversación nueva | Tabla chequeo / evidencia / estado / severidad y veredicto: ¿se puede avanzar de etapa? |

## Flujo

1. Ejecutá el prompt 1 con valores base medidos: sin ellos, los umbrales que proponga la IA serán inventados.
2. Ejecutá el prompt 2 un cambio por commit, con la condición de rollback como regla ejecutable, y probalo en staging.
3. Abrí una conversación nueva y ejecutá el prompt 3 antes de avanzar cada etapa.

En la próxima clase se valida con observabilidad si es seguro apagar el código local.
