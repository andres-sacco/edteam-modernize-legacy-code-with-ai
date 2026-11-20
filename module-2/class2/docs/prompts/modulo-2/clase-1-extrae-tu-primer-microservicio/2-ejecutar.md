# Prompt 2: ejecutar

**Rol:** Una rama, un paso por vez. Crea el proyecto, la API y los tests.

## Prompt

```text
Rol: Actúa como el desarrollador que extrae el servicio según el plan aprobado.

Contexto: El informe ya revisado, CatalogoApi y el repositorio en una rama nueva.

Tarea: Crear el proyecto Spring Boot del catálogo con DTOs, endpoints, configuración, Dockerfile, cliente HTTP y tests.

Restricciones: Comportamiento externo idéntico. Sin entidades JPA en la API. Configuración por variables de entorno.

Formato: Proyecto con estructura de carpetas, un commit por paso y un resumen de lo creado.
```

## Tip

Ejecutalo en pasos: primero el proyecto y la API, después el cliente y al final los tests. Un commit por paso, para poder revertir cualquiera.

## Antes de ejecutarlo

```bash
git switch -c extract/catalogo-service
```

- Indicá en el prompt **qué paso** ejecutar (proyecto y API, cliente o tests).
- Después de cada paso, verificá que el monolito sigue igual: `mvn -B test` y `./scripts/smoke-test.sh` con el stack
  levantado (skill `verify-app`).
