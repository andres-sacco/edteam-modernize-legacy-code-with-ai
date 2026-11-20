# Prompt 1: investigar

**Rol:** Solo lectura. Proponer contextos con evidencia.

## Prompt

```text
Rol: Actúa como arquitecto de dominio (DDD) que analiza un sistema legacy de búsqueda y cotización de vuelos.

Contexto: Mapa de la Clase 2: módulos Búsqueda, Aerolíneas, Precios y Catálogo de ciudades, con dependencias, tablas y git log.

Tarea: Proponer bounded contexts candidatos con su lenguaje, entidades, datos propios, cohesión y acoplamiento.

Restricciones: Solo lectura. Justifica cada límite con evidencia (archivo y clase). Marca como hipótesis lo que no puedas comprobar.

Formato: Tabla de candidatos con cohesión, acoplamiento y evidencia, más entidades compartidas y preguntas abiertas.
```

## Tip

Adjuntá el mapa de la Clase 2 y el git log. Pedile que explique qué descartó y por qué. Revisá la propuesta con alguien del negocio antes de seguir.

## Preparar lo que se adjunta

- El mapa generado en la clase 2 (los archivos de `/docs`).
- La historia de cambios:

```bash
git log --name-only --pretty=format:"--- %h %ad %s" --date=short -- . > analisis-git-log.txt
```
