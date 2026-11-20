# Módulo 2 · Clase 1 · Extrae tu primer microservicio

Pasamos del plan a la ejecución: convertimos el catálogo de ciudades en un servicio independiente. **Aquí sí se crea código**: cada paso se ejecuta aislado, en una rama, y se valida. Una persona revisa entre cada prompt.

| Paso | Archivo | Rol | Alcance | Resultado |
|------|---------|-----|---------|-----------|
| 1 | [1-investigar.md](1-investigar.md) | Arquitecto de microservicios | Solo lectura | Qué se mueve, qué se duplica y qué se reemplaza; estructura, DTOs y endpoints propuestos |
| 2 | [2-ejecutar.md](2-ejecutar.md) | Desarrollador que extrae el servicio | Una rama, un paso por vez | Proyecto Spring Boot del catálogo con API, configuración, Dockerfile, cliente HTTP y tests |
| 3 | [3-validar.md](3-validar.md) | Revisor escéptico | Solo lectura, conversación nueva | Tabla chequeo / evidencia / estado / severidad y veredicto: ¿se puede activar? |

## Flujo

1. Ejecutá el prompt 1 y revisá el informe: qué se mueve, qué se duplica y qué se reemplaza por una dependencia propia.
2. Ejecutá el prompt 2 en pasos: primero el proyecto y la API, después el cliente y al final los tests. Un commit por paso.
3. Abrí una conversación nueva y ejecutá el prompt 3, que intenta demostrar que el servicio no es idéntico al módulo.

En la próxima clase se separa la base de datos: cada servicio con sus propios datos.
