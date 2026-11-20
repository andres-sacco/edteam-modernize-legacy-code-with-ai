# Prompt 1: investigar

**Rol:** Solo lectura. Medir los cruces y planificar.

## Prompt

```text
Rol: Actúa como especialista en refactorización de monolitos Java/Spring Boot, con foco en modularización.

Contexto: Paquetes actuales, matriz de dependencias (jdeps) y los contextos de la Clase 3: Búsqueda, Aerolíneas, Precios y Catálogo.

Tarea: Identificar imports cruzados, clases con varias responsabilidades y tablas compartidas, y proponer un plan de refactor.

Restricciones: Solo lectura. Pasos pequeños y reversibles, de menor a mayor riesgo, cada uno con sus archivos afectados.

Formato: Tabla de pasos: qué mover, qué interfaz crear, archivos afectados, riesgo y cómo verificarlo.
```

## Tip

Adjuntá la salida de jdeps y la lista de contextos de la Clase 3. Pedile que ordene los pasos del menos riesgoso al más riesgoso y que indique cómo verificar cada uno.

## Preparar lo que se adjunta

- El context map y el glosario por contexto de la clase 3 (en `/docs`).
- La matriz de dependencias entre paquetes:

```bash
mvn -q -B clean package -DskipTests
jdeps -verbose:package -filter:none -e 'com\.twa\.flights.*' target/classes > analisis-jdeps.txt
```
