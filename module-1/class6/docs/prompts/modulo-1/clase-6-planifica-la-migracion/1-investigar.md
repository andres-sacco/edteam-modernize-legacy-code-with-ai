# Prompt 1: investigar

**Rol:** Solo lectura. Entender qué implica extraer el catálogo.

## Prompt

```text
Rol: Actúa como arquitecto con experiencia en migraciones Strangler Fig y Branch by Abstraction.

Contexto: Monolito modular, context map de la Clase 3, CatalogoApi de la Clase 4 y la suite de tests de la Clase 5.

Tarea: Analizar qué llamadas, datos y consumidores tiene el catálogo de ciudades y qué riesgos tendría extraerlo.

Restricciones: Solo lectura. Cada hallazgo cita archivo y clase. Distingue llamadas externas e internas. Marca hipótesis.

Formato: Informe con consumidores, datos que mueve, riesgos y preguntas abiertas para el equipo.
```

## Tip

Adjuntá el context map, CatalogoApi y la lista de tests. Pedile que separe llamadas externas (Gateway) de internas (interfaz), porque se rutean distinto.

## Preparar lo que se adjunta

- El context map de la clase 3 (en `/docs`) y la interfaz pública del catálogo creada en la clase 4.
- La lista de tests de la clase 5:

```bash
find src/test -name '*Test.java' | sort > analisis-tests.txt
```
