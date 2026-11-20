# Prompt 1: investigar

**Rol:** Solo lectura. Decidir qué proteger primero.

## Prompt

```text
Rol: Actúa como ingeniero de QA experto en testing de sistemas legacy Java/Spring Boot.

Contexto: Módulos Búsqueda, Aerolíneas, Precios y Catálogo ya modularizados, con ejemplos reales de entradas y respuestas.

Tarea: Identificar los comportamientos a proteger, ordenarlos por riesgo y señalar qué dificulta testearlos.

Restricciones: Solo lectura. No escribas tests todavía. Cada comportamiento cita archivo y clase; lo no comprobable es "hipótesis".

Formato: Tabla: comportamiento, módulo, riesgo, tipo de test (caracterización, integración o contrato) y dificultad.
```

## Tip

Adjuntá ejemplos reales de búsquedas y cotizaciones, y los logs si existen. Revisá la lista con alguien del negocio: ellos saben qué flujos no pueden fallar.

## Preparar lo que se adjunta

Con el stack levantado (`docker compose up -d --build`), grabá ejemplos reales de entradas y respuestas:

```bash
mkdir -p ejemplos
B=http://localhost:8080/api/flights/clusters/itineraries
# Fechas futuras (hoy + 60 y + 70 días): la búsqueda rechaza fechas pasadas
read -r IDA VUELTA <<<"$(python3 -c 'import datetime as d; t = d.date.today(); print(t + d.timedelta(days=60), t + d.timedelta(days=70))')"
curl -s "$B?from=BUE,MIA&to=MIA,BUE&departure=$IDA,$VUELTA&adults=2&children=1&infants=1&amount=5" > ejemplos/busqueda-ida-y-vuelta.json
curl -s "$B?from=BUE&to=SCL&departure=$IDA&adults=1&children=0&infants=0&cabin=BUSINESS" > ejemplos/busqueda-solo-ida-business.json
curl -s "$B?from=BUE&to=XXX&departure=$IDA&adults=1&children=0&infants=0" > ejemplos/busqueda-ciudad-invalida.json
docker logs twa-flights-monolith > ejemplos/logs.txt 2>&1
```

La carpeta `ejemplos/` está ignorada por git: es material para adjuntar, no parte del proyecto.
