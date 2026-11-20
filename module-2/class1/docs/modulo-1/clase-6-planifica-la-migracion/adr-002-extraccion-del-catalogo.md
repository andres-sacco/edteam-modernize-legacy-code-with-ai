# ADR-002: Extracción gradual del catálogo de ciudades (BC4)

- **Estado:** Aceptado
- **Fecha:** 2026-11-16
- **Decisores:** equipo de modernización de TWA Flights (equipos Búsqueda y Catálogo)
- **Base:** [informe de investigación de la clase 6](informe-investigacion.md), revisado con las decisiones 1, 2, 3 y 7
  del equipo
- **Relacionados:** [plan de extracción](plan-de-extraccion.md) ·
  [ADR-001](../clase-3-que-partes-podemos-separar/adr-001-division-en-bounded-contexts.md) ·
  [context map](../clase-3-que-partes-podemos-separar/context-map.md)

---

## Contexto

El ADR-001 dejó a BC4 (catálogo geográfico) como el candidato más fácil de extraer primero: un solo servicio, sin
escrituras en runtime y con una API publicada. La clase 5 dejó esa API como `CatalogApi.getCity(code)` y cortó el
acceso directo de `FlightHelper` a `CityRepository` (X1).

El informe de la clase 6 mostró que el contrato real no es solo `getCity`:

- **C1** (`AvailabilityRequestValidator`) convierte cualquier excepción en 400/40014.
- **C2** (`FlightHelper` vía `Provider*Service`) descarta en silencio el itinerario si la ciudad falta o la consulta
  falla. Con un catálogo remoto caído, la búsqueda respondería 200 con una lista vacía.
- Cada itinerario hace hasta 12 `getCity` por proveedor. Por red, eso es un N+1.
- La comparación sin distinguir mayúsculas depende de la collation de MySQL y el código no tiene `UNIQUE`.

El equipo ya decidió: una caída del catálogo responde **503** (decisión 1), hay **lote y caché** en los consumidores
(decisión 2), el código es **único y sin distinguir mayúsculas** (decisión 3) y el servicio tiene **ABM** de ciudades
(decisión 7).

## Decisión

Extraer BC4 a un servicio propio (`twa-catalog-service`) con **Strangler Fig** sobre la costura `CatalogApi`
(**Branch by Abstraction**), en las 9 etapas del [plan](plan-de-extraccion.md), cada una detrás de un flag y con
criterios numéricos de avance y de rollback.

Este ADR también cierra las preguntas abiertas del informe que el plan necesita resolver:

| # | Decisión | Pregunta del informe que cierra |
|---|----------|---------------------------------|
| D1 | **`CatalogApi` es la única costura.** Dos implementaciones (`CatalogService` local y `CatalogClient` remoto) y un flag `catalog.mode=local\|shadow\|remote` con `catalog.remote.percentage` | 7 (Branch by Abstraction y sombra) |
| D2 | **Primero la misma base, después la base propia.** Etapas 2 a 6: el servicio lee `flights` (solo lectura hasta el ABM). Etapa 7: base `catalog` con Flyway propio como **única primaria de escritura**; `flights.city` se mantiene al día con un **outbox** en la misma transacción y un relay idempotente (mismos ids en las dos bases) mientras exista el camino de vuelta | 6 (estrategia de datos) |
| D3 | **`getCities(codes)` es la operación principal.** BC2 sigue recibiendo `CityDTO` y lee solo `timeZone`; no se agrega `getTimeZone`. El servicio devuelve el id de la zona horaria, no offsets: la duración se sigue calculando en el monolito | 4 (BC2 solo `timeZone`) |
| D4 | **Si el catálogo no responde durante C2, la búsqueda completa responde 503.** No se descartan itinerarios por indisponibilidad (`reason=catalog_unavailable` tiene que ser siempre 0). El descarte por ciudad inexistente o por duración negativa no cambia. La excepción atraviesa los cuatro `catch` que hoy la tragarían (validador, `Provider*Service`, `makeProviderCalls` y `joinResults`); el camino local también la lanza | 1 (qué hace C2 con el 503) |
| D5 | **Caché del consumidor con TTL de 60 s** (Caffeine, máximo 1.000 entradas). No se cachean los códigos inexistentes, así una ciudad nueva no espera el TTL para dejar de dar 40014. Invalidación por evento: fuera de este plan | 2 (TTL o invalidación) |
| D6 | **El código de una ciudad es inmutable y el borrado es lógico** (`active=false`). Los 7 hubs de Microcks no se pueden desactivar. Las ciudades nuevas toman el id de un `AUTO_INCREMENT` (hoy los ids son manuales) y un código desactivado se reactiva, no se vuelve a crear. Una búsqueda guardada en `cluster_search` (TTL 5 min) ya tiene sus duraciones calculadas, así que no la afecta | 3 (borrar o cambiar códigos) |
| D7 | **El 40014 es de la búsqueda (BC1).** El catálogo responde "no existe" (fuera de la lista o 404); BC1 decide que eso es 40014. `CatalogError` se mueve a BC1 cuando se retire el camino local (etapa 8), sin cambiar el código que ve el cliente | 5 (dueño del 40014) |
| D8 | **Sin fallback automático por pedido** del remoto al local. La vuelta atrás es por flag, disparada por alertas. Un fallback por `try/catch` escondería la falla en las métricas y contradice la decisión 1 | — (nueva) |
| D9 | **No se borran tablas en este plan.** `continent`, `country` y `city` quedan en `flights` y al día (outbox) hasta M2 · C5 | — (nueva) |

## Alternativas consideradas

| Alternativa | Por qué se descartó |
|-------------|---------------------|
| **Big bang:** cambiar `CatalogService` por un cliente HTTP en un solo deploy | No hay vuelta atrás sin redeploy, y el peor caso (200 con lista vacía) se vería en el 100 % del tráfico |
| **Base propia desde el día 1** | Obliga a sincronizar datos antes de saber si el servicio funciona. Mezcla dos riesgos (servicio y datos) en la misma etapa. La misma base permite volver atrás con un flag |
| **Réplica en memoria en el monolito** (cargar las 86 ciudades al arrancar y no llamar al servicio) | Rompe el ABM de la decisión 7: una ciudad nueva no se vería hasta reiniciar. Además el monolito seguiría siendo dueño de los datos |
| **Fallback automático al camino local si el remoto falla** | Esconde las caídas, duplica las fuentes de verdad durante el ABM y contradice la decisión 1 (503) |
| **Mantener el comportamiento actual en C2** (descartar el itinerario si el catálogo no responde) | Es el riesgo más grave del informe: un 200 con lista vacía y sin señal para el cliente |
| **`getTimeZone(code)` separado para BC2** | Agrega una segunda operación sin bajar la cantidad de llamadas, que es lo que importa (el lote ya la baja). Se puede agregar después sin romper el contrato |
| **Doble escritura directa en la etapa 7** (el ABM escribe en `catalog` y después en `flights.city`) | No es atómica: si la segunda escritura falla, `flights.city` queda atrasada y es justo la base a la que vuelve el rollback. Se reemplaza por un outbox en la misma transacción |
| **Invalidar la caché con eventos desde el inicio** | Necesita un broker que hoy no existe. Con 86 ciudades y altas poco frecuentes, 60 s de TTL alcanza. Se revisa en M2 · C3 |

## Consecuencias

**Positivas**

- Cada etapa se vuelve atrás en ≤ 5 minutos con un flag, salvo las etapas 0, 1 (propagación del 503), 1c y 8 (≤ 15 minutos con un
  rolling deploy) y la 7 (≤ 15 minutos: se cierra el ABM y se espera el outbox vacío antes de cambiar las lecturas).
- La sombra compara los dos caminos con tráfico real antes de que un cliente vea el camino remoto.
- El lote y la caché bajan las consultas al catálogo de hasta 12 por itinerario y proveedor a **≤ 4 por búsqueda**, y
  la mayoría salen de la caché. Esto también baja la carga sobre la Hikari del monolito.
- Una caída del catálogo se ve (503 y alertas) en lugar de esconderse detrás de un 40014 o de una lista vacía.

**Negativas y costos**

- **Cambio de comportamiento aprobado:** una falla del catálogo pasa de 40014 a 503. Los tests de caracterización #6 y
  #26 de la clase 4 se actualizan cuando se implemente la etapa 1.
- **D4 cambia otro comportamiento:** hoy una falla al calcular la duración descarta solo ese itinerario; con el
  catálogo caído, la búsqueda completa responde 503. Solo aplica a la indisponibilidad, no a ciudades inexistentes.
- **Infraestructura nueva antes de empezar:** 2 instancias del monolito, flags en runtime, métricas con historia y
  alertas (prerrequisitos P1 a P4 del plan). Hoy no existe ninguno: el plan los trata como una etapa P con criterio de cumplido, y ninguna etapa empieza sin el suyo.
- Durante las etapas 2 a 6 el servicio y el monolito comparten la base: un cambio de esquema en `city` lo sigue
  migrando el monolito (dueño del `flyway_schema_history`), aunque el dueño lógico sea BC4.
- El outbox y el relay de la etapa 7 se mantienen hasta M2 · C5: es código temporal. Una falla del relay no pierde
  escrituras (quedan en el outbox), pero deja `flights.city` atrasada: la alerta `CatalogOutboxLag` cierra el ABM y la
  reconciliación del job de paridad reencola las diferencias.
- Con TTL de 60 s, una ciudad **modificada** (por ejemplo, la zona horaria) puede verse vieja hasta 60 s.
- El tiempo total es largo: con los mínimos de la tabla, de la etapa 0 a la 8 son unos **105 días** (3,5 meses), sin contar
  el desarrollo.

## Preguntas abiertas

| # | Pregunta | Impacto si la respuesta cambia |
|---|----------|--------------------------------|
| 1 | ¿Qué herramienta de flags se usa (P2)? | Define cómo se cambia un flag en runtime y quién tiene permiso. Tiene que decidirse **antes de la etapa 1**: si M2 · C4 llega después, las etapas 1 a 7 esperan |
| 2 | ¿Hay tráfico real para cumplir los mínimos (≥ 10.000 búsquedas en sombra y en el canario 25 %)? | Si no lo hay, las etapas se alargan o se completan con tráfico sintético. El plan no permite avanzar por tiempo sin volumen |
| 3 | ¿El ABM necesita auditoría (quién cambió qué ciudad)? | Agrega una tabla al servicio y un campo al evento de M2 · C3 |
| 4 | ¿Quién es dueño del alta de un proveedor (pregunta 5 del ADR-001)? | Con el ABM, las ciudades de un proveedor nuevo se dan de alta en el servicio y no con una migración del monolito |
