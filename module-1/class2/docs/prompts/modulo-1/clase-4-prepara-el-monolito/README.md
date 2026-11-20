# Módulo 1 · Clase 4 · Prepara el monolito

Antes de extraer servicios, ordenamos el monolito por dentro. **Aquí sí se modifica código**: cada paso se ejecuta aislado, en una rama, y se valida. Una persona revisa entre cada prompt.

| Paso | Archivo | Rol | Alcance | Resultado |
|------|---------|-----|---------|-----------|
| 1 | [1-investigar.md](1-investigar.md) | Especialista en refactorización | Solo lectura | Plan de refactor en pasos chicos y reversibles, de menor a mayor riesgo |
| 2 | [2-ejecutar.md](2-ejecutar.md) | Desarrollador que aplica el plan | Una rama, un paso por vez | Clases movidas, interfaz pública del módulo, un commit por paso |
| 3 | [3-validar.md](3-validar.md) | Revisor escéptico | Solo lectura, conversación nueva | Tabla chequeo / evidencia / estado y veredicto sobre el merge |

## Flujo

1. Ejecutá el prompt 1 y revisá el plan: que los pasos sean chicos, ordenados por riesgo y con su forma de verificarlos.
2. Ejecutá el prompt 2 **una vez por cada paso** del plan, nunca el plan completo. Si un paso falla, se descarta el commit y se vuelve al anterior.
3. Abrí una conversación nueva y ejecutá el prompt 3 para comprobar que el refactor no cambió el comportamiento.

En la próxima clase se construye la red de tests que protege antes de mover nada más.
