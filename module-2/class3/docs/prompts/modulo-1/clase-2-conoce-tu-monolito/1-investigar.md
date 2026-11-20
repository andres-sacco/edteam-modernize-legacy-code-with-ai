# Prompt 1: investigar

**Rol:** solo lectura. Descubrir antes de decidir.

## Prompt

```text
Rol: Actuá como arquitecto de software senior que investiga un sistema legacy de búsqueda y cotización de vuelos de una agencia de viajes.

Contexto: Código Java/Spring Boot con lógica por aerolínea, cálculo de precios y catálogo de ciudades. Adjunto paquetes, git log y tablas.

Tarea: Sin modificar nada: dominios candidatos, dependencias, tablas por módulo, reglas ocultas y archivos que cambian juntos.

Restricciones: Solo lectura. Cada hallazgo cita archivo y clase. Lo que no puedas comprobar en el código se marca como "hipótesis".

Formato: Informe con: dominios candidatos, dependencias, datos, reglas ocultas, antipatrones y preguntas abiertas para el negocio.
```

## Tip

Pasale también el resultado de `jdeps` y el `git log` completo (el proyecto es chico) para que pueda detectar acoplamiento
lógico. Revisá el informe a mano antes de pasar al siguiente prompt.

## Preparar lo que se adjunta

Desde la raíz del proyecto (`twa-flights-monolith/`):

```bash
# Paquetes y dependencias entre paquetes
mvn -q -B clean package -DskipTests
jdeps -verbose:package -filter:none -e 'com\.twa\.flights.*' target/classes > analisis-jdeps.txt

# Historia: qué archivos cambian juntos
git log --name-only --pretty=format:"--- %h %ad %s" --date=short -- . > analisis-git-log.txt

# Tablas
cat src/main/resources/db/migration/*.sql > analisis-tablas.sql
```

Guardá el informe resultante (por ejemplo en `informe-investigacion.md`) para revisarlo y corregirlo antes del prompt 2.
