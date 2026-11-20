# Revisión escéptica de la división en bounded contexts

Paso de validación de la clase 3. El objetivo es **refutar** la división del
[ADR-001](adr-001-division-en-bounded-contexts.md), no aprobarla. Se recorren los dos flujos principales (buscar y
cotizar), se cuentan los cruces de límites y se buscan ciclos y entidades sin dueño. Se contrasta contra el
[context map](context-map.md), las [entidades compartidas](entidades-compartidas.md) y el
[informe de investigación](informe-investigacion.md).

> Convención de rutas: las clases están en `src/main/java/com/twa/flights/`. Se nombran por paquete y clase.
> Sin evidencia en el código, un hallazgo queda como **no verificado**. No se cambió código ni documentos.

---

## 1. Veredicto

La división en cuatro contextos **se sostiene en lo grueso**. Cada tabla tiene un solo dueño y cada migración (V1.0 a
V1.7) toca las tablas de un solo contexto. El único acceso por SQL a tablas de otro contexto es `FlightHelper`.

El código contradice dos afirmaciones de los documentos:

- **"Ninguno depende de BC1"** ([informe](informe-investigacion.md), fila BC1): BC2 y BC3 dependen de tipos de BC1.
- **"Ninguna entidad sin dueño"** ([entidades compartidas](entidades-compartidas.md#3-entidades-sin-dueño)): el precio
  tiene tres contextos que lo escriben.

Además, el **contrato de cotización** propuesto no coincide con el código.

---

## 2. Recorrido y conteo de cruces

### 2.1 Búsqueda nueva (sin `id`)

**9 cruces sobre 4 límites. 3 van en sentido contrario al mapa objetivo** (filas 4, 7 y 8).

| # | Cruce | Evidencia |
|---|-------|-----------|
| 1 | BC1 → BC4: validación de origen y destino, 1 llamada por código | `validator/AvailabilityRequestValidator` líneas 47 y 220 |
| 2 | BC1 → BC2: 3 proveedores en paralelo, con el pedido de BC1 (`AvailabilityRequestDTO`) | `service/ItinerariesSearchService` línea 59 |
| 3 | BC2 → BC4 por SQL: 2 consultas por tramo y 2 por segmento | `helper/FlightHelper` líneas 65-66 y 73-74 |
| 4 | BC2 → BC1 (tipos): construye `ItineraryDTO` y deriva `FlightType` del pedido | `mapper/provider/ProviderMapperHelper` líneas 11 y 25-26 |
| 5 | BC1 usa datos de BC2 y BC4 para deduplicar: `Provider` y `flightDuration` en la clave | `helper/ItineraryKeyGeneratorHelper` líneas 24 y 48 |
| 6 | BC1 → BC3: cotización | `service/ClustersService` línea 80 |
| 7 | BC3 lee el modelo de BC1, con 1 consulta por itinerario | `specification/CommercialPolicyCarrierSpecification` líneas 53 y 68; `service/PricingService` líneas 60-64 |
| 8 | BC3 escribe en el modelo de BC1 y descarta itinerarios | `service/PricingService` líneas 53 y 82-91 |
| 9 | BC1 guarda lo que calculó BC3 (markup) dentro de `cluster_search` | `repository/impl/ClustersRepositoryImpl` línea 58 |

### 2.2 Otros recorridos

- **Paginación (con `id`):** 1 cruce. El validador vuelve a consultar BC4 aunque no busca de nuevo
  (`controller/ClustersController` línea 37 → `AvailabilityRequestValidator` línea 47).
- **Detalle (`getItinerary`):** 0 cruces. Devuelve el precio que se guardó al buscar (`ClustersService` línea 69).
- **Cotización (`PricingService.priceItineraries`):** 4 cruces. Lee el árbol de `ItineraryDTO` (BC1). Usa `subtotal` y
  `total`, que calculó BC2. Escribe en `PaxPriceDTO` (BC1). Decide qué se descarta en la lista de BC1.

---

## 3. Hallazgos

Severidad: **Alta** = bloquea o invalida una decisión del ADR · **Media** = hay que resolverlo antes de separar ·
**Baja** = ajuste de documentación.

| Límite | Hallazgo | Evidencia | Severidad | Recomendación |
|--------|----------|-----------|-----------|---------------|
| BC1 ↔ BC3 | **Ciclo.** BC1 llama a BC3 y BC3 importa y modifica los DTOs de BC1. Ya está documentado como desvío 2 del context map | `ClustersService` línea 80; `PricingService` líneas 15-20, 53 y 82-91 | Alta | **Mantener** el límite. El plan del ADR (contrato propio) lo resuelve |
| BC1 ↔ BC2 | **Ciclo no documentado.** BC1 llama a BC2. BC2 depende de `AvailabilityRequestDTO` e `ItineraryDTO`, y BC1 depende del enum `Provider`. Contradice "ninguno depende de BC1" | `facade/ProviderFacade` líneas 6-7; `ProviderMapperHelper` líneas 11 y 29-44; `ItineraryKeyGeneratorHelper` línea 24 | Alta | **Ajustar.** BC2 necesita su propio modelo de pedido. `AvailabilityRequestDTO` debe entrar en la tabla de entidades compartidas |
| BC3 (contrato) | **El contrato de cotización no coincide con el código.** Los docs dicen "fecha de llegada del último segmento". El código usa la llegada del **primer tramo** del último segmento. Con escalas, un contrato construido según los docs cambia qué política vigente aplica | `CommercialPolicyCarrierSpecification` líneas 52-53 y 68 | Alta | **Ajustar** el contrato en el ADR, el context map y entidades compartidas, sin cambiar el comportamiento |
| BC2 / BC3 / BC1 | **El precio no tiene un solo dueño.** BC2 escribe `subtotal` y `total`. BC3 reescribe `total` y `markup`. BC1 deduplica con el total **sin markup** y filtra u ordena con el total **con markup**. Refuta "ninguna entidad sin dueño" | `ProviderMapperHelper` líneas 80-81; `PricingService` líneas 82-91 y 124; `helper/FlightDuplicationCheckHelper` línea 46; `helper/ItinerarySortHelper` línea 22; `helper/ItineraryFilterHelper` línea 72 | Alta | **Ajustar.** Separar tres conceptos: precio neto (BC2), markup (BC3) y precio de venta (BC1) |
| BC1 / BC3 | **Cotización congelada sin dueño.** La paginación y el detalle nunca vuelven a cotizar. En la práctica, la vigencia del precio es el TTL de BC1 (5 minutos). Un cambio de política no afecta búsquedas ya guardadas | `ClustersService` líneas 69 y 90; `ClustersRepositoryImpl` líneas 29 y 58 | Media | **Ajustar.** Definir quién es dueño de la vigencia de una cotización. Se relaciona con la pregunta abierta 1 del ADR |
| BC3 → BC1 | **N+1 al separar.** Hoy hay 1 consulta por itinerario. Como API remota, serían N llamadas por búsqueda. El ADR prevé llamadas en lote solo para BC4 | `PricingService` líneas 60-64 | Media | **Ajustar.** El contrato de BC3 tiene que aceptar un lote |
| BC1 ← BC2 ← BC4 | **Dependencia transitiva.** La clave de deduplicación y el orden por duración usan `flightDuration`. BC2 lo calcula con datos de BC4. Si una ciudad no está en BC4, el itinerario desaparece antes de llegar a BC1 | `ItineraryKeyGeneratorHelper` línea 48; `ItinerarySortHelper` línea 57; `service/ProviderAlphaService` línea 56 | Media | **Mantener**, pero documentar que BC1 depende de BC4 también por esta vía |
| BC1 | **La deduplicación solo une itinerarios del mismo proveedor**, porque la clave incluye `Provider`. Pone en duda la razón para que viva en BC1 | `ItineraryKeyGeneratorHelper` línea 24 | Baja | **Mantener**, con una nota en el glosario. Confirmar con negocio si es intencional |
| BC1 / BC2 | **`FlightType` es de BC1 pero lo decide BC2**, a partir del `to` del pedido | `ProviderMapperHelper` líneas 25-26 | Baja | **Ajustar** la regla de dueño: o lo asigna BC1 o pasa a BC2 |
| BC1 / BC3 | **Posible colisión de ids entre proveedores.** El detalle y la cotización buscan el itinerario solo por id, y los mappers copian el id del proveedor. Los mocks de Microcks generan `uuid()`, así que no se puede reproducir | `ClustersService` línea 69; `PricingService` línea 46; `AlphaItineraryMapper` línea 57; `BetaItineraryMapper` línea 68; `GammaItineraryMapper` línea 64 | Media | **No verificado.** Dejarlo para la red de seguridad |
| Infraestructura | **Clases sin contexto asignado:** `serializer/JsonSerializer` (solo la usa BC1), `WebConfiguration`, `SwaggerConfiguration`, `AppInfoService` y `DocumentationController`. Ningún documento de la clase 3 las nombra | `ClustersRepositoryImpl` línea 58; búsqueda en `docs/modulo-1/clase-3-que-partes-podemos-separar/` | Baja | **Ajustar** el inventario |
| BC4 | La API de BC4 alcanza para BC2: `CityDTO` ya incluye la zona horaria | `dto/CityDTO` línea 5 | — | **Mantener** |
| BC3 | Cálculo y administración comparten la entidad y el repositorio. Esto respalda unirlos en un contexto | `service/CommercialPolicyService` línea 38; `PricingService` línea 31 | — | **Mantener** |
| Datos | Cada migración toca tablas de un solo contexto | `src/main/resources/db/migration/V1.0` a `V1.7` | — | **Mantener** |

---

## 4. Observaciones fuera de los límites

No se cambiaron. Quedan para la red de seguridad.

- `PricingService` líneas 70-71 vuelve a filtrar por carrier aunque la `Specification` ya lo hace.
- `ClustersRepositoryImpl` línea 53 guarda la búsqueda en otro hilo. Una página pedida inmediatamente después de la
  búsqueda podría devolver 502 (`SEARCH_NOT_FOUND_IN_REPOSITORY`). **No verificado.**
