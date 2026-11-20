# Prompt 2: ejecutar

**Rol:** Escribe solo en `src/test`. Generar los tests sin tocar el código de producción.

## Prompt

```text
Rol: Actúa como el desarrollador que escribe la red de tests aprobada por el equipo.

Contexto: La lista de comportamientos ya revisada, el código de los módulos y respuestas grabadas de las aerolíneas.

Tarea: Generar tests JUnit 5 de caracterización que documenten el comportamiento actual, un módulo por vez y por riesgo.

Restricciones: No corrijas bugs ni cambies código de producción. Stubs solo para lo externo. Fija fechas y datos.

Formato: Clases de test en src/test, un commit por módulo y una tabla con los casos cubiertos.
```

## Tip

Usá respuestas grabadas de las aerolíneas con WireMock. Ejecutalo módulo por módulo y corré cada test contra el sistema actual: tienen que pasar.

Para fijar las fechas sin tocar producción: el validador usa `LocalDate.now()` y no recibe un `Clock`, así que
inyectarlo sería un cambio de código de producción. Calculá en cada test las fechas de búsqueda relativas a hoy (por
ejemplo, hoy + 60 días). Las respuestas grabadas pueden tener fechas fijas, porque el validador solo mira la fecha del
request. Si el equipo decide inyectar un `Clock`, eso es un refactor aparte, con su propio commit y revisión.

El `pom.xml` solo trae `spring-boot-starter-test`: WireMock (y H2 o Testcontainers, si los tests levantan el contexto
de Spring sin la MySQL del stack) se agregan como dependencias con `<scope>test</scope>`, sin tocar el código de
producción.

## Antes de ejecutarlo

- Las respuestas grabadas de las aerolíneas se pueden obtener directamente de Microcks (`make start-infra`). Cada
  proveedor responde con su propio formato:

```bash
mkdir -p src/test/resources/providers
read -r IDA VUELTA <<<"$(python3 -c 'import datetime as d; t = d.date.today(); print(t + d.timedelta(days=60), t + d.timedelta(days=70))')"
Q="origin=BUE&destination=MIA&departureDate=$IDA&returnDate=$VUELTA&adults=1&children=1&infants=1"
for p in alpha beta gamma; do
  curl -s "http://localhost:8585/rest/provider-$p/1.0/itineraries?$Q" > src/test/resources/providers/$p-ida-y-vuelta.json
done
```

- Para correr los tests: `mvn -B test`.
