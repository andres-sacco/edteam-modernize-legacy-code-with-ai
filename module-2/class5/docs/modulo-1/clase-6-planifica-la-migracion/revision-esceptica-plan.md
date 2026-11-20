# Revisión escéptica del plan de extracción del catálogo

Paso de validación de la clase 6. El objetivo es encontrar **por qué fallaría** el
[plan de extracción](plan-de-extraccion.md), no aprobarlo. En cada etapa se simula el peor escenario y se buscan
dependencias sin definir, pasos sin rollback y datos sin plan. El plan se contrasta con el
[ADR-002](adr-002-extraccion-del-catalogo.md), el código, la suite de tests y los datos semilla del catálogo.

> Convención de rutas: las clases están en `src/main/java/com/twa/flights/`. Se nombran por paquete y clase.
> Sin evidencia en el código o en el plan, un hallazgo queda como **no verificado**. La revisión no cambió código.

---

## 1. Veredicto

**Ajustar.** El enfoque general se sostiene: una costura única (`CatalogApi`), un flag por etapa, criterios numéricos y
ningún borrado de tablas. Pero había **4 riesgos altos**, y uno de ellos reproducía el peor escenario que el plan quiere
evitar: un 200 con lista vacía cuando el catálogo está caído.

Los 4 riesgos altos ya están corregidos en el plan (sección 7 del [plan](plan-de-extraccion.md#7-ajustes-por-la-revisión-escéptica))
y en el ADR-002. Los riesgos medios y bajos siguen **abiertos**.

---

## 2. Datos verificados

| Qué se verificó | Resultado | Evidencia |
|-----------------|-----------|-----------|
| Los códigos de ciudad son únicos, en mayúsculas y de 3 letras (condición de la V1.8) | Sí: 86 códigos, sin duplicados (ni ignorando mayúsculas), todos de 3 letras en mayúsculas | `V1.1__insert_catalog_rows.sql` (30 ciudades) y `V1.5__insert_more_cities.sql` (56) |
| `cities.csv` coincide con la semilla | Sí, 86/86 en código, nombre, zona horaria y país | `src/test/resources/catalog/cities.csv` |
| Los ids de ciudad | Del 1 al 86, sin huecos, **asignados a mano** | `V1.0__create_catalog_tables.sql` (`id bigint not null`, sin `AUTO_INCREMENT`); `shared.model.Base` sin `@GeneratedValue` |
| Los 7 hubs de Microcks existen en la semilla | Sí: `PTY, LIM, BOG, MIA, SAO, SCL, MEX` | `microcks/generate-contracts.py` (lista `PROVIDERS`) |
| Hay FK hacia `city.id` | No | Migraciones V1.0 a V1.7 |
| Prerrequisitos de plataforma (P1 a P3) | Ninguno existe | `docker-compose.yml` con una sola instancia; `pom.xml` sin Spring Cloud, Prometheus ni Caffeine |

---

## 3. Hallazgos

| Paso | Riesgo (peor escenario) | Evidencia | Severidad | Recomendación | Estado |
|------|-------------------------|-----------|-----------|---------------|--------|
| Etapa 1.4 / D4 (503 en C2) | `CatalogUnavailableException` nunca llega al cliente. Hay tres `catch` en el camino: `catch (Exception)` por itinerario en `Provider*Service`, `catch (Exception)` en `makeProviderCalls` (devuelve lista vacía) y `joinResults`. Con el catálogo caído, la búsqueda responde **200 con lista vacía** (riesgo 1 del informe). El plan no nombraba `ItinerariesSearchService` | `search.service.ItinerariesSearchService` · `makeProviderCalls`, `joinResults`; `provider.service.ProviderAlphaService` · `calculateFlightDuration`; plan §3 Etapa 1, punto 4 | **Alta** | Ajustar | Corregido |
| Prerrequisitos P1 a P3 | No existe ninguno. El plan elegía la herramienta de flags recién en M2·C4, pero todos los rollbacks de "≤ 5 min" dependen de ella. `ClusterSearchCleanupJob` (`@Scheduled`) correría en las 2 instancias (que sea inocuo: **no verificado**). La etapa 0 comparaba contra "los 7 días anteriores", que sin P3 no tienen historia | `docker-compose.yml`; `pom.xml`; ADR pregunta abierta 1; plan §4 fila 0 | **Alta** | Ajustar | Corregido |
| Etapa 6 (ABM) | No hay estrategia de `id`: un `POST` sin generador falla o choca. El filtro de `active=false` del camino local se agregaba "en la misma etapa", sin orden respecto del flag: un rollback podía resucitar ciudades dadas de baja. Los hubs protegidos "mientras existan en `generate-contracts.py`" no se pueden comprobar en runtime | `V1.0__create_catalog_tables.sql`; `shared.model.Base`; plan §3 Etapa 6 | **Alta** | Ajustar | Corregido |
| Etapa 7 (doble escritura) | No hay transacción entre las dos bases. Si `catalog` acepta y `flights.city` rechaza, el rollback (`read=shared`) vuelve a la base **que no tiene el dato**. No había reconciliación ni reintento, y no se definía cómo mantener iguales los `country_id` en la base `catalog` | Plan §3 Etapa 7 y §4 fila 7 (versión anterior) | **Alta** | Rehacer (la sincronización) | Corregido (outbox) |
| Etapa 1.2 (lote en C2) | Quien llama al catálogo es `FlightHelper.calculateFlightDuration`, con `getCity` por tramo, no los `Provider*Service`. No se define cómo llega el mapa del lote a `FlightHelper`. Con `catalog.batch.enabled=true` y la caché apagada (1a antes que 1b), las consultas siguen siendo N+1 y el criterio "≤ 4 por búsqueda" no se cumple | `provider.helper.FlightHelper` · `calculateFlightDuration(LegDTO/SegmentDTO)`; plan §4 fila 1a | Media | Ajustar | Abierto |
| Etapa 1.2 (lote en C1) / riesgo 4 | Hoy el validador revisa, código por código, el largo (40011) y después el catálogo (40014): `from=XXX&to=ABCD` da 40014. Con un lote, el resultado depende de si el largo se valida antes, y el plan no lo dice. `severalErrors` no cubre ese caso | `search.validator.AvailabilityRequestValidator` · `validateFromToValidCodes`; `ClustersValidationTest` · `severalErrors`; plan §5 riesgo 4 | Media | Ajustar | Abierto |
| Etapa 1.4 (camino local) | No se definía qué lanza el `CatalogService` local si la base falla; los dos caminos darían códigos distintos ante la misma falla | `catalog.service.CatalogService`; plan §1 principio 4 | Media | Ajustar | Corregido junto con el riesgo del 503 |
| Etapa 0 (volumen) | En este entorno (Microcks, sin tráfico real) no se llega a ≥ 10.000 búsquedas, y el plan no permite avanzar por tiempo: las etapas 0, 3 y 4b quedan bloqueadas sin fecha | Plan §4 filas 0, 3 y 4b; ADR pregunta abierta 2 | Media | Ajustar (definir el tráfico sintético) | Abierto |
| Etapa 1c (índice `UNIQUE`) | Con el borrado lógico, un código desactivado no se puede volver a crear: no había flujo de reactivación | Plan §3 Etapa 6 / D6 | Baja | Ajustar | Corregido (reactivación con `PUT`) |
| Etapa 2 (servicio y mock) | El servicio no especifica la normalización a mayúsculas: la sombra con minúsculas daría `mismatch` o dependería de la collation (riesgo 3). El mock del catálogo en `generate-contracts.py` es trabajo nuevo sin dueño | Plan §3 Etapa 2; `microcks/generate-contracts.py` | Media | Ajustar | Abierto |
| Etapa 2 (datos) | Usuario de solo lectura y permisos parciales sobre `city`: hoy todo usa `root` (**no verificado** en un entorno real) | `application.yml` (`DB_USER:root`) | Baja | Aprobar | Abierto |
| Etapa 3 (sombra) | El "pool aparte" no define tamaño, cola ni política de rechazo: con `sample-rate=1.0` y el servicio lento, se satura (si bloquea la búsqueda: **no verificado**). No se compara `name` | Plan §3 Etapa 3 | Media | Ajustar | Abierto |
| Etapa 4 (canario) | La decisión "una vez por pedido" no viaja sola a los 3 hilos de los proveedores (`Executors.newFixedThreadPool(10)`): con un `ThreadLocal`, C1 y C2 pueden usar caminos distintos. El hash de `from`/`to`/`departure` deja siempre a las mismas rutas en el canario | `search.service.ItinerariesSearchService` (constructor); plan §3 Etapa 4 | Media | Ajustar | Abierto |
| Etapa 5 (caída controlada) | Se exige "caída de una instancia sin 503", pero D8 prohíbe el fallback y no se definen reintentos ni balanceo en `CatalogClient`: el criterio no se cumple nunca | Plan §4 fila 5; ADR D8 | Media | Ajustar | Abierto |
| Etapa 8 (retiro) | El rollback redeploya un artefacto de hasta 30 días. Que arranque con migraciones posteriores aplicadas (`ddl-auto: validate`, Flyway) y con los flags de entonces: **no verificado** | Plan §3 Etapa 8; `application.yml` | Media | Ajustar (probar el rollback antes de borrar) | Abierto |
| Tests | Toda la suite corre el camino local (MySQL de Testcontainers + WireMock): nada prueba `CatalogClient`, la sombra ni el canario. `FlightHelperTest` y `ProvidersSearchTest` dependen de `getCity` y se romperán con el lote. `analisis-tests.txt` es solo una lista de archivos | `support.AbstractIntegrationTest`; `analisis-tests.txt`; plan §5 riesgo 4 | Media | Ajustar | Abierto |
| Duración | Con los mínimos de la tabla, la suma da unos 102 días, no 105 | Plan §4; ADR Consecuencias | Baja | Aprobar | Abierto |

---

## 4. Cómo se corrigieron los riesgos altos

| Riesgo | Cambio en el plan y en el ADR-002 |
|--------|-----------------------------------|
| 503 perdido en C2 | Etapa 1, punto 4: tabla con los cuatro `catch` (validador, `Provider*Service`, `makeProviderCalls`, `joinResults`), qué hacen hoy y qué hacen después. El camino local convierte `DataAccessException` en `CatalogUnavailableException`. Condición de cierre: un test con el catálogo caído que dé 503 y no 200 vacío. Fila 1 en la tabla de etapas. ADR D4 actualizada |
| Prerrequisitos | Etapa P con un criterio de cumplido verificable por prerrequisito. La herramienta de flags se elige antes de la etapa 1 (ADR pregunta abierta 1). La etapa 0 mide el costo de las métricas con una prueba de carga antes y después |
| ABM | `city.id` pasa a `AUTO_INCREMENT` en la misma migración que `active`. Orden de deploys 6.1 a 6.5 con verificación en cada paso y el flag al final. Reactivación con `PUT`. Hubs protegidos en configuración, con un chequeo del build contra `generate-contracts.py`. ADR D6 actualizada |
| Doble escritura | `catalog` es la única primaria: outbox en la misma transacción, relay idempotente con los mismos ids y reconciliación desde el job de paridad. Rollback en orden: cerrar el ABM, esperar el outbox vacío, paridad, cambiar las lecturas. Alerta `CatalogOutboxLag`; la etapa 8 exige el outbox vacío. ADR D2, D9, consecuencias y una alternativa descartada actualizadas |
