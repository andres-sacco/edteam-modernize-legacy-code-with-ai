# Entidades compartidas y su dueño

Conceptos, clases y tablas que hoy usan dos o más contextos del [context map](context-map.md). Para cada uno se define
**un único dueño**: el contexto que decide su forma y sus reglas. Los demás lo consumen por contrato o mantienen su
propia representación.

> Convención de rutas: las clases están en `src/main/java/com/twa/flights/`. Se nombran por paquete y clase.

Tipos de relación con el dueño:

- **Contrato**: el consumidor lo recibe por una API publicada del dueño.
- **Modelo propio**: el consumidor tiene su propia representación y traduce en el borde.
- **Lenguaje publicado**: un valor (código o enum) que todos usan igual y que el dueño documenta.

---

## 1. Resumen

| Concepto | Dueño | Lo usan | Relación de los demás | Estado hoy |
|----------|-------|---------|-----------------------|------------|
| Itinerario (`ItineraryDTO` y su árbol) | **BC1 Búsqueda** | BC2, BC3, tabla `cluster_search` | BC2: lo produce traduciendo (ACL). BC3: modelo propio de cotización | 🔴 Compartido en el kernel, BC3 lo muta |
| Precio por pasajero y markup (`PriceInfoDTO`, `PaxPriceDTO`, `MarkupDTO`) | **BC1** (estructura) / **BC3** (cálculo del markup) | BC2, BC3 | BC3 devuelve el markup por contrato; BC1 lo aplica | 🔴 BC3 escribe dentro del DTO de BC1 |
| Política comercial (`CommercialPolicy`, `CommercialPolicyCarrier`) | **BC3 Pricing** | Solo BC3 (cálculo y administración) | — | 🟢 Sin cruces |
| Ciudad, país, continente (`City`, `Country`, `Continent`) | **BC4 Catálogo** | BC1, BC2 | Contrato (consulta por código) | 🟠 BC2 lee `CityRepository` directo |
| Búsqueda guardada (`ClusterSearch`, `cluster_search`) | **BC1** | Solo BC1 | — | 🟢 Sin cruces |
| Código de carrier | **Lenguaje publicado** (sin maestro) | BC1, BC2, BC3 | Lenguaje publicado | 🟠 Largo inconsistente (2 vs 3) |
| Código de ciudad | **BC4** | BC1, BC2, JSON de `cluster_search` | Lenguaje publicado | 🟠 Ver ciudad |
| `Provider` (enum) | **BC2 Proveedores** | BC1 (clave de deduplicación) | Lenguaje publicado | 🟡 Vive en el kernel |
| `PassengerType`, `CabinClass` | **Lenguaje publicado** (documenta BC1) | BC1, BC2, BC3 | Lenguaje publicado | 🟢 Estables |
| `FlightType` | **BC1** | BC2 (lo asigna al traducir) | Lenguaje publicado | 🟡 Vive en el kernel |
| Id del itinerario | **BC1** (con el proveedor como parte de la clave) | BC2 (lo toma del proveedor), BC3 (cruza el precio) | Contrato | 🟠 Posible colisión entre proveedores (hipótesis) |
| Códigos de error (`ExceptionStatus`, `ErrorDTO`) | **Cada contexto** sus códigos; `ErrorDTO` es lenguaje publicado | Todos | Se reparte por contexto | 🔴 Un enum para todo, el archivo más cambiado |

Leyenda: 🟢 sin problema · 🟡 a mover · 🟠 acoplamiento a corregir · 🔴 bloquea la separación.

---

## 2. Detalle

### 2.1 Itinerario · dueño BC1

- **Qué es:** `ItineraryDTO` → `SegmentDTO` → `LegDTO`, más `PriceInfoDTO`, `BaggageDTO` y sus hijos. Es el contrato
  público de la API de búsqueda y lo que se guarda como JSON en `cluster_search`.
- **Por qué BC1:** es el resultado de la búsqueda y lo que ve el cliente. Las reglas de filtro, orden y paginación se
  definen sobre él.
- **Cómo lo usan los demás:**
  - **BC2** lo construye en `mapper/provider/*ItineraryMapper`. Es correcto: BC2 traduce *hacia* el modelo de BC1.
  - **BC3** hoy lo recibe en `service/PricingService.priceItineraries` (línea 39), le escribe el markup en
    `updatePriceInfo` (líneas 79-90) y descarta los que quedan sin markup (línea 53). La
    `specification/CommercialPolicyCarrierSpecification` (líneas 45-73) navega `firstSegment()`, `lastSegment()` y
    `firstLeg()`. **Debe pasar a un modelo propio de cotización** con solo lo que necesita: carrier, fecha de salida
    del primer segmento, fecha de llegada del último y precios por tipo de pasajero.
- **Evidencia del alcance:** 27 archivos de `src/main` referencian `ItineraryDTO`.

### 2.2 Precio y markup · estructura BC1, cálculo BC3

- La **estructura** (`PriceInfoDTO`, `PaxPriceDTO`, `MarkupDTO`) es parte del itinerario: dueño BC1.
- El **cálculo** del markup (qué política, qué porcentaje, cuánto) es de BC3. Hoy ya existe un modelo intermedio,
  `UpdatedPriceInfoDTO` / `UpdatedPaxPriceDTO` (`PricingService.calculatePrices`, línea 57): es el punto de partida
  natural del contrato de cotización.
- La regla "itinerario sin markup no se vende" (R02) es una decisión de BC3 que hoy se ejecuta filtrando la lista de
  BC1. En el contrato, BC3 debe **informar** que no hay política y BC1 decidir qué hacer.

### 2.3 Política comercial · dueño BC3

- Tablas `commercial_policy` y `commercial_policy_carrier` (FK entre ellas, `V1.2`, `V1.7`).
- La usan el cálculo (`PricingService`) y la administración (`CommercialPolicyService`, `CommercialPolicyController`).
  Las dos caras quedan **dentro** de BC3 (ver [ADR-001](adr-001-division-en-bounded-contexts.md)).
- **Dependencia por datos:** `V1.6` agregó carriers de Gamma a la política "General". Dar de alta un proveedor
  requiere una política para sus carriers.

### 2.4 Ciudad · dueño BC4

- Tablas `continent`, `country`, `city`. Se cargan por migraciones (`V1.0`, `V1.1`, `V1.5`).
- **BC1** la consume bien, por servicio: `validator/AvailabilityRequestValidator` línea 220 llama a
  `CatalogService.getCity`.
- **BC2** la consume mal: `helper/FlightHelper` inyecta `CityRepository` (línea 28) y llama a `findByCode` en las
  líneas 65-66 y 73-74. Al separar, BC2 tiene que pedir la zona horaria a la API de BC4.

### 2.5 Búsqueda guardada · dueño BC1

- `model/ClusterSearch`, tabla `cluster_search`, `repository/impl/ClustersRepositoryImpl` (`TOKEN_TTL = 5`),
  `job/ClusterSearchCleanupJob`. Nadie fuera de BC1 la usa.
- Guarda los itinerarios como JSON: si cambia la forma de `ItineraryDTO`, las búsquedas guardadas viejas pueden dejar
  de leerse. El TTL de 5 minutos acota ese riesgo.

### 2.6 Códigos compartidos (lenguaje publicado)

| Código | Valores | Dueño | Observación |
|--------|---------|-------|-------------|
| Carrier | 2 caracteres (`AR`, `LA`...) | Lenguaje publicado, sin maestro | `varchar(3)` en `commercial_policy_carrier` (`V1.2`), 2 en `CommercialPolicyValidator`. Si negocio pide un maestro de aerolíneas, el dueño se define entonces |
| Ciudad | IATA (`BUE`, `MIA`) | BC4 | Hasta 3 en la API, 4 en la tabla |
| `Provider` | `ALPHA`, `BETA`, `GAMMA` | BC2 | BC1 lo usa en la clave de deduplicación (`ItineraryKeyGeneratorHelper`). Sumar un proveedor hoy toca el kernel |
| `PassengerType` | `ADULT`, `CHILD`, `INFANT` | Lenguaje publicado | Estable |
| `CabinClass` | `ECONOMY`, `PREMIUM_ECONOMY`, `BUSINESS` | Lenguaje publicado | Estable. Cada proveedor usa sus códigos y BC2 traduce |
| `FlightType` | `ONE_WAY`, `ROUND_TRIP`, `MULTIPLE_DESTINATIONS` | BC1 | `MULTIPLE_DESTINATIONS` nunca se asigna |

### 2.7 Id del itinerario · dueño BC1

- BC2 copia el id que manda el proveedor (R18). BC3 lo usa para volver a cruzar el precio calculado con el itinerario:
  `PricingService` línea 46 (`iti.getId().equals(...)` con `findFirst`).
- **(Hipótesis no comprobada)** si dos proveedores devuelven el mismo id, el markup puede aplicarse al itinerario
  equivocado. El contrato de cotización debe usar una clave que incluya el proveedor, o devolver el resultado en el
  mismo orden de entrada. No se cambia el código: queda para la red de seguridad (clase 5).

### 2.8 Códigos de error · cada contexto los suyos

| Rango actual | Contexto dueño |
|--------------|----------------|
| `400_01` a `400_22` (validación del pedido, salvo `400_14`) | BC1 |
| `400_14` `INCOMPLETE_CATALOG_INFORMATION` | BC4 define el caso; BC1 lo devuelve al validar |
| `404_01`, `404_02` (búsqueda o itinerario inexistente) | BC1 |
| `400_23` a `400_29`, `401_01`, `404_03`, `404_04` (administración de políticas y API key) | BC3 |

`ErrorDTO` (`code`, `shortDescription`, `reasons`) queda como formato publicado común para que la API pública no cambie.

---

## 3. Entidades sin dueño

Ninguna: todas las tablas tienen exactamente un contexto dueño.

| Tabla | Dueño |
|-------|-------|
| `cluster_search` | BC1 |
| `commercial_policy` | BC3 |
| `commercial_policy_carrier` | BC3 |
| `continent` | BC4 |
| `country` | BC4 |
| `city` | BC4 |
| `flyway_schema_history` | Infraestructura (una por base cuando se separen) |

Lo que queda sin dueño claro son **procesos**, no entidades: el alta de un proveedor cruza BC2, BC3 y BC4 (ver
[context map, sección 4](context-map.md#4-dependencias-por-datos)).
