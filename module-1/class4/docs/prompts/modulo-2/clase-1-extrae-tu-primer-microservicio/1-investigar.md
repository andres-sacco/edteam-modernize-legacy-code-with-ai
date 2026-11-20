# Prompt 1: investigar

**Rol:** Solo lectura. Entender qué se mueve y qué no.

## Prompt

```text
Rol: Actúa como arquitecto de microservicios especializado en Spring Boot.

Contexto: Módulo Catálogo de ciudades, CatalogoApi, sus tablas, sus consumidores (Búsqueda y Precios) y la suite de tests.

Tarea: Identificar qué clases, configuración y dependencias pertenecen al catálogo y cuáles comparte con el monolito.

Restricciones: Solo lectura. Cita archivo y clase de cada dependencia. Distingue lo que se mueve, se duplica o se reemplaza.

Formato: Informe con estructura propuesta, DTOs, endpoints y lista de dependencias a resolver.
```

## Tip

Adjuntá CatalogoApi, las clases del módulo y la salida de jdeps. Pedile que separe lo que se mueve, lo que se duplica y lo que debe reemplazarse por una dependencia propia.

## Preparar lo que se adjunta

- La interfaz pública del catálogo (creada en la clase 4 del módulo 1) y las clases del módulo.
- La salida de jdeps y las tablas del catálogo:

```bash
mvn -q -B clean package -DskipTests
jdeps -verbose:package -filter:none -e 'com\.twa\.flights.*' target/classes > analisis-jdeps.txt
cat src/main/resources/db/migration/*.sql > analisis-tablas.sql
find src/test -name '*Test.java' | sort > analisis-tests.txt
```
