# ADR-001: División del monolito en cuatro bounded contexts

- **Estado:** Aceptado
- **Fecha:** 2026-11-15
- **Decisores:** equipo de modernización de TWA Flights, con revisión de negocio
- **Base:** [informe de investigación de la clase 3](informe-investigacion.md) y entregables de la
  [clase 2](../clase-2-conoce-tu-monolito/mapa-modulos-dependencias.md)
- **Relacionados:** [context map](context-map.md) · [glosario por contexto](glosario-por-contexto.md) ·
  [entidades compartidas](entidades-compartidas.md)

---

## Contexto

TWA Flights es un monolito Spring Boot con una única base `flights`. El código está organizado por capa técnica
(`controller`, `service`, `repository`, `helper`...), no por dominio, y comparte un kernel (`dto`, `enums`,
`exception`) que usan todos los módulos. Para migrar a microservicios primero hay que decidir **dónde están los
límites**: qué partes tienen lenguaje, datos y reglas propias.

La clase 2 identificó seis módulos lógicos (D1 Búsqueda, D2 Proveedores, D3 Pricing cálculo, D4 Administración de
pricing, D5 Catálogo, D6 Sesión de búsqueda) y el kernel compartido. El análisis de la clase 3 midió cohesión y
acoplamiento de cada uno con evidencia del código y del git log.

Hechos que pesan en la decisión:

- **Pricing tiene dos caras sobre los mismos datos.** El cálculo (`PricingService`) y la administración
  (`CommercialPolicyService`) usan la misma entidad, el mismo repositorio y las mismas invariantes.
- **La sesión de búsqueda no tiene lenguaje propio.** `ClustersRepository` recibe y devuelve DTOs de la API, y su ciclo
  de vida (TTL de 5 minutos, limpieza, paginación) es de la búsqueda.
- **Los proveedores ya funcionan como capa anticorrupción.** Un proveedor = facade + service + connector + config +
  mapper + record, y cambian juntos (`f77c8a1`, `d0b786a`, `c0f5987`). No tienen tablas.
- **El catálogo es estable y casi aislado**, salvo que `FlightHelper` lee `CityRepository` sin pasar por
  `CatalogService`.
- **Pricing depende del modelo de la búsqueda:** recibe `ItineraryDTO`, lo modifica en el lugar y descarta itinerarios.
- **"Aerolíneas" no existe como módulo:** el carrier es solo un código de 2 caracteres, sin entidad ni tabla.

## Decisión

Se divide el monolito en **cuatro bounded contexts**:

| # | Contexto | Une | Tipo | Datos |
|---|----------|-----|------|-------|
| BC1 | **Búsqueda de vuelos** | D1 + D6 | Core | `cluster_search` |
| BC2 | **Integración con proveedores** | D2 | Soporte (ACL) | sin tablas |
| BC3 | **Pricing** | D3 + D4 | Core | `commercial_policy`, `commercial_policy_carrier` |
| BC4 | **Catálogo geográfico** | D5 | Genérico | `continent`, `country`, `city` |

Y se fijan estas reglas para las clases siguientes:

1. **Cada tabla tiene un solo dueño** (ver [entidades compartidas](entidades-compartidas.md#3-entidades-sin-dueño)).
   Ningún contexto lee por SQL las tablas de otro.
2. **BC1 es el dueño del itinerario** (`ItineraryDTO`). BC2 lo produce traduciendo; BC3 deja de conocerlo.
3. **BC3 publica un contrato de cotización propio** (Open Host Service): recibe carrier, fechas del vuelo y precios
   por tipo de pasajero, y devuelve el markup o "sin política". BC1 aplica el resultado y decide qué descartar.
4. **BC4 publica una API de consulta por código** que usan BC1 (validación) y BC2 (zona horaria).
5. **El kernel compartido se desarma:** cada concepto pasa a su dueño o queda como lenguaje publicado (`PassengerType`,
   `CabinClass`, código de carrier, `ErrorDTO`). `ExceptionStatus` se reparte por contexto sin cambiar los códigos que
   ve el cliente.
6. **El comportamiento no cambia.** Las reglas ocultas (R01-R20), incluidas las que parecen errores (markup `/10`,
   prioridad más alta gana, vigencia por fecha de vuelo), se mantienen hasta que negocio decida otra cosa.

## Alternativas consideradas

| Alternativa | Por qué se descartó |
|-------------|---------------------|
| **Seis contextos, uno por módulo de la clase 2** | D3 y D4 comparten tablas e invariantes: separarlos obliga a compartir la base o replicarla. D6 no tiene lenguaje propio: es infraestructura de la búsqueda |
| **Separar la administración de pricing del cálculo** | Lo que cambia es la audiencia (admin con `X-Api-Key` frente a la búsqueda pública), no el modelo. Se resuelve con dos APIs dentro de BC3 |
| **"Aerolíneas" como contexto** | No hay entidad, tabla, servicio ni endpoint. El carrier es un código compartido. Si negocio pide un maestro de aerolíneas, se revisa este ADR |
| **Proveedores dentro de Búsqueda** | Cambian por razones distintas (un proveedor nuevo, un cambio de formato) y ya tienen un borde claro (`ProviderFacade`). Mantenerlos aparte protege a BC1 del lenguaje de cada proveedor |
| **Tres contextos: Búsqueda (con proveedores y catálogo) + Pricing + Catálogo de solo lectura dentro de Búsqueda** | Mezcla un subdominio genérico y estable con el core. El catálogo tiene su propio ritmo de cambio (migraciones de ciudades) y otros consumidores (BC2) |
| **Kernel compartido como contexto** | No es un dominio: es el acoplamiento que hay que desarmar |

## Consecuencias

**Positivas**

- Cada contexto tiene un lenguaje coherente y un dueño claro de sus datos.
- BC4 queda como el candidato más fácil de extraer primero: bajo acoplamiento por API, sin escrituras en runtime,
  un solo servicio. (La decisión del orden de extracción es de la clase 6.)
- BC3 casi no cambió en el git log (`PricingService` tiene un solo commit): con un contrato propio, es estable.
- BC2 permite sumar un proveedor sin tocar el modelo de la búsqueda.

**Negativas y costos**

- Hay que crear un **modelo de cotización** en BC3 y cambiar `PricingService` para que no mute `ItineraryDTO`. Es el
  cambio de mayor riesgo, porque la regla "sin política no se vende" (R02) hoy se ejecuta ahí.
- `FlightHelper` hace 2 consultas a ciudades por segmento y 2 por tramo. Pasarlas por una API remota multiplica la
  latencia de la búsqueda: hará falta caché o una consulta en lote.
- Repartir `ExceptionStatus` toca el archivo más cambiado del repositorio.
- `cluster_search` guarda itinerarios como JSON: un cambio en la forma de `ItineraryDTO` puede romper búsquedas
  guardadas durante el TTL.
- El **alta de un proveedor** sigue cruzando tres contextos (BC2 + ciudades en BC4 + carriers en BC3). La división no
  lo resuelve; necesita un dueño de proceso.

**Lo que queda fuera de este ADR**

- No se cambia código: este ADR solo fija los límites. El monolito modular se arma en la clase 4 y la red de seguridad
  en la clase 5.
- El orden de extracción y la estrategia de datos se deciden en las clases 6 y M2 · C2.

## Preguntas abiertas

Preguntas del informe que este ADR no cierra. Cualquiera de ellas puede obligar a revisarlo.

| # | Pregunta | Impacto si la respuesta cambia |
|---|----------|--------------------------------|
| 1 | ¿Cotizar es solo parte de buscar o lo van a usar otros (checkout, reprecio)? | Si otros lo usan, el contrato de BC3 gana prioridad y versión pública |
| 2 | ¿Hace falta un maestro de aerolíneas? | Aparece un contexto nuevo o crece BC4 |
| 3 | ¿La "ciudad" es una ciudad o un aeropuerto? | Cambia el lenguaje de BC4 y la regla de zona horaria |
| 4 | ¿La zona horaria debería venir del proveedor? | Si sí, BC2 deja de depender de BC4 |
| 5 | ¿Quién es dueño del alta de un proveedor? | Define el proceso que hoy cruza BC2, BC3 y BC4 |
| 6 | ¿La búsqueda guardada es un concepto de negocio o un caché para paginar? | Si es de negocio, podría ser un contexto propio |
| 7 | ¿Las reglas R01 (`/10`), R03 (prioridad) y R04 (vigencia) son correctas? | Cambian el contrato de cotización de BC3 |
