# Prompt 2: ejecutar

**Rol:** Una rama, un cambio por vez. Configura umbrales, alertas y el flag.

## Prompt

```text
Rol: Actúa como el ingeniero que prepara el plan de monitoreo y rollback aprobado.

Contexto: La tabla de métricas ya revisada, el feature flag y las etapas del plan.

Tarea: Definir el criterio de éxito y las alertas de cada etapa, y configurar el flag con su condición de rollback.

Restricciones: Umbrales numéricos. El rollback se dispara sin un nuevo despliegue. Un cambio por commit.

Formato: Tabla de etapas con umbrales, alertas y condición de rollback, más los cambios de configuración.
```

## Tip

Pedí un cambio por commit y la condición de rollback expresada como una regla ejecutable, no como un texto. Probalo primero en staging.

## Antes de ejecutarlo

```bash
git switch -c rollout/catalogo-service
```

- En este proyecto, el stack local con `docker compose` cumple el rol de staging.
- El rollback tiene que poder dispararse sin desplegar: el flag no puede ser una propiedad que solo se lee al
  arrancar la aplicación.
