# Revisión escéptica: `twa-flights-catalog` contra el módulo `catalog`

> Objetivo: probar que el servicio **no** es idéntico al módulo. Rama `extract/catalogo-service`.
> Sin correcciones: lo que no se pudo verificar queda como "no verificado".

| Ronda | Fecha | Commit revisado | Veredicto |
|---|---|---|---|
| 1 | 2026-11-16 | `6c19198` | No se puede activar: 2 hallazgos altos (filas 5 y 6) |
| 2 | 2026-11-16 | `fb2ad40` | Todavía no: los 2 altos cerrados, divergencia nueva con `A/B` y `%` (fila 4) |
| 3 | 2026-11-16 | `63b18e6` (agrega `a7d276b`, `725c0f8`, `ea77407`, `1f41fff`, `63b18e6`) | **Sí, con condiciones**: queda una divergencia baja (fila 4b) y la configuración de activación |

## Cómo se probó (ronda 3)

- `./scripts/init-env.sh` y stack completo con `docker compose up -d --build`.
- Datos de las 86 ciudades del servicio contra el JOIN `city`/`country`/`continent`.
- **20 búsquedas** en 4 configuraciones del monolito (recreado con un override de compose):
  - **L0**: `LOCAL`, sin lote ni caché. Es el comportamiento legacy y la base de comparación.
  - **L1**: `LOCAL` con lote y caché.
  - **R0**: `REMOTE`, sin lote ni caché (los valores por defecto).
  - **R1**: `REMOTE` con lote y caché (la configuración objetivo).

  Los casos agregados en esta ronda prueban la collation (`ＢＵＥ` en letras de ancho completo, `MİA`, `MIß`, la
  ligadura `ﬀA`) y caracteres especiales en el query (`A&B`, `B+E`, `B#E`, `BU `).
- Duración de cada tramo recalculada con las zonas de la base, en las 4 configuraciones.
- Llamadas HTTP por búsqueda (métrica `http.server.requests`, uri `/cities`) y aciertos de caché (`cache.gets`).
- Fallas en R1: catálogo parado con la caché caliente, después del TTL, y `docker pause` a mitad de la búsqueda con la
  caché fría. Conteo de `cluster_search` antes y después.
- Permisos de `catalog_reader` (`SELECT` y `UPDATE` sobre varias tablas) y arranque del servicio sin `DB_PASSWORD`.
- Carga: 20 búsquedas ida y vuelta concurrentes en R1.
- `python3 microcks/generate-contracts.py` y `git diff` de `microcks/`.
- `mvn -B test` (el monolito corre la suite dos veces: LOCAL y REMOTE con lote y caché) y `./scripts/smoke-test.sh`.
- No se cambió código. Al final se apagó el stack (`docker compose down`).

## Qué cambió desde la ronda 2

- **Lote** (`a7d276b`): `CatalogApi.getCities` y `GET /cities?codes=`. El validador pide todas las ciudades en una
  llamada y cada proveedor pide las de sus itinerarios en otra. `CatalogClient` usa siempre el query, nunca el path
  ([CatalogClient.java:39](../../../twa-flights-monolith/src/main/java/com/twa/flights/catalog/client/CatalogClient.java)).
  Cada ciudad que devuelve MySQL se asigna al código pedido con un `Collator` de Java
  ([CityCodes.java:21-31](../../../twa-flights-monolith/src/main/java/com/twa/flights/catalog/helper/CityCodes.java),
  [CityService.java:104](../../../twa-flights-catalog/src/main/java/com/twa/catalog/service/CityService.java)).
  Se activa con `catalog.batch.enabled`, **apagado por defecto**.
- **Caché** (`725c0f8`): Caffeine delante del catálogo, local o remoto. TTL de 60 s, sin cachear las ciudades
  inexistentes ni las fallas ([CachedCatalogApi.java:45,59,73](../../../twa-flights-monolith/src/main/java/com/twa/flights/catalog/client/CachedCatalogApi.java)).
  Se activa con `catalog.cache.enabled`, **apagado por defecto**.
- **Credenciales** (`ea77407`): contraseña aleatoria en `.env`, que está en `.gitignore`. El servicio no arranca sin
  `DB_PASSWORD`. El contenedor `catalog-db-grants` da `SELECT` solo sobre `continent`, `country` y `city`
  ([grant-catalog-reader.sh:9,23](../../../docker/mysql/grants/grant-catalog-reader.sh)).
- **Contrato** (`1f41fff`): `microcks/catalog-openapi.json`, generado desde las migraciones semilla. Lo verifican los
  tests del servicio y del cliente.
- **Suite en los dos modos** (`63b18e6`): segunda ejecución de surefire (`catalog-remote`) con `REMOTE`, lote y caché.

## Resultados (ronda 3)

| # | Chequeo | Evidencia | Estado | Severidad | Ronda 2 → 3 |
|---|---|---|---|---|---|
| 1 | El servicio devuelve los mismos datos que las tablas | Las 86 ciudades coinciden con el JOIN: 0 diferencias | ✅ Verificado | — | Igual |
| 2 | Búsqueda en las 4 configuraciones (casos válidos) | Los 6 casos válidos dan 200 en L0, L1, R0 y R1. Duraciones recalculadas: L0 186/186, L1 159/159, R0 154/154 y R1 164/164 tramos bien. Los cuerpos no se comparan byte a byte porque Microcks genera ofertas al azar | ✅ Verificado (igualdad byte a byte: **no verificada**) | — | Igual |
| 3 | Tests | `mvn -B test`: el monolito pasa **216/216 en LOCAL y 216/216 en REMOTE** (1 skip legítimo: `CatalogApiRouterTest.localByDefault` no aplica en REMOTE). El servicio pasa 20/20. `smoke-test.sh` ahora prueba el servicio y el mock de Microcks, y pasa | ✅ Verificado | — | Mejoró |
| 4 | Códigos raros que el servicio rechazaba (`A/B`, `%`, `..`, `B;x`) | **Corregido.** Ahora dan 400 40014 en las 4 configuraciones, igual que el legacy. El código viaja como query y no como path. Lo cubre [CatalogClientTest.java:120](../../../twa-flights-monolith/src/test/java/com/twa/flights/catalog/client/CatalogClientTest.java). También dan 40014 en las 4: `A&B`, `B+E`, `B#E`, `BU `, `MIß` y `ﬀA` | ✅ Verificado | — | Era Media |
| 4b | **Collation: letras de ancho completo** | **Divergencia nueva.** `from=ＢＵＥ` da **200 en L0** (legacy: MySQL `utf8mb4_0900_ai_ci` lo iguala a `BUE`) y **400 40014 en L1, R0 y R1**. MySQL sí devuelve `BUE`, pero el `Collator` de Java (`Locale.ROOT`, `PRIMARY`, sin descomposición) dice que `ＢＵＥ` ≠ `BUE`, así que se descarta ([CityCodes.java:31](../../../twa-flights-monolith/src/main/java/com/twa/flights/catalog/helper/CityCodes.java), [CityService.java:104](../../../twa-flights-catalog/src/main/java/com/twa/catalog/service/CityService.java)). El servicio tampoco es consistente consigo mismo: `/cities/ＢＵＥ` da 200 y `/cities?codes=ＢＵＥ` da `[]`. Afecta también a R0, porque el cliente usa el lote aunque `batch.enabled=false`. Los IME de japonés y chino suelen escribir letras latinas de ancho completo. Ningún test lo cubre. `MİA` y `BUÉ` sí coinciden en las 4 configuraciones | ❌ Verificado | Baja | Nuevo |
| 4c | El `Collator` iguala `BUE ` con `BUE`, MySQL no (`NO PAD`) | Visto con un programa de Java. Hoy no tiene efecto: el `Collator` solo filtra lo que ya devolvió MySQL, y el validador rechaza los códigos de 4 caracteres | ⚠️ Latente (por código) | Info | Nuevo |
| 5 | Catálogo caído o colgado durante la validación | En R1, después del TTL y con el catálogo parado: 503 50301 en 0,03 s | ✅ Verificado | — | Igual |
| 6 | El catálogo cae a mitad de una búsqueda | En R1 con la caché fría: 2 pausas dieron 503 50301, con fallas en los hilos `pool-2-thread-*`. Las otras 2 dieron 200 porque la búsqueda (~0,2 s con el lote) terminó antes de la pausa. `cluster_search` pasó de 43 a 45 filas: solo se guardaron los dos 200 | ✅ Verificado | — | Igual |
| 6c | **Caída con la caché caliente** | Con el catálogo parado, 6 búsquedas BUE↔MIA y una SCL→LIM dieron **200**: salieron de la caché. Pasados 65 s (TTL) dio 503. Durante una caída de menos de 60 s la búsqueda no se entera. Es lo que pide ADR-002 D5, pero es un comportamiento distinto al de la ronda 2: las alertas tienen que mirar el servicio de catálogo, no solo los 503 de la búsqueda | ✅ Verificado | Info | Nuevo |
| 7 | Cantidad de llamadas (N+1) | **Corregido en R1.** De 148 llamadas por búsqueda ida y vuelta a 4 como máximo con la caché fría (1 de la validación + 1 por proveedor) y 0 o 1 con la caché caliente. 20 búsquedas concurrentes: 20 × 200, **promedio 0,10 s y máximo 0,12 s** (ronda 2: 0,75 s y 1,15 s), 0 errores del catálogo. Con los valores por defecto (R0) no hay lote: es una llamada por código distinto | ✅ Verificado | — | Era Media |
| 8 | Timeouts | Sin cambios: 500 ms de conexión y 500 ms de lectura. Con el lote, una búsqueda espera como mucho un timeout por proveedor | ✅ Verificado | Baja | Igual |
| 9 | Base del servicio caída → 503, nunca 404 | Lo cubren los tests ([CityControllerErrorsTest.java](../../../twa-flights-catalog/src/test/java/com/twa/catalog/controller/CityControllerErrorsTest.java)). No se probó en vivo: la base es la misma del monolito | ❓ No verificado en vivo | — | Igual |
| 10 | Credenciales fijas | **Corregido.** `DB_PASSWORD` sin valor por defecto ([application.yml:27](../../../twa-flights-catalog/src/main/resources/application.yml)). Sin la variable, el servicio no arranca y lo dice (probado con `java -jar`). Compose exige `CATALOG_DB_PASSWORD` de `.env`. Queda el `root`/`muppet` del legacy, ahora también en el contenedor `catalog-db-grants` ([docker-compose.yml:30](../../../docker-compose.yml)) | ✅ Verificado | Baja (legacy) | Era Media |
| 11 | Mínimo privilegio | **Corregido.** `SHOW GRANTS`: `SELECT` solo sobre `city`, `continent` y `country`. Con `catalog_reader`: `city` → 86; `commercial_policy`, `cluster_search` y `UPDATE city` → `ERROR 1142`. El script no revoca el `GRANT ... ON flights.*` de la ronda 2: en una base que ya existía, ese permiso sigue (no aplica a compose, donde la base no persiste) | ✅ Verificado | Info | Era Media |
| 12 | Entidades expuestas | No hay: el controller devuelve `CityDTO` | ✅ Verificado | — | Igual |
| 13 | Información de más | Sin cambios: `health` con `show-details: always`, `metrics` público y la página de Jetty con stack trace (aunque el cliente ya no llama al path con códigos raros) | ⚠️ Verificado | Baja | Igual |
| 14 | Tests solo del camino feliz | **Mejoró mucho.** La suite de caracterización corre en REMOTE en cada `mvn test`. El contrato de Microcks lo verifican las dos puntas, y regenerarlo no cambia nada. Hay tests de lote, caché y códigos raros. Queda: (a) la ejecución REMOTE prueba solo lote + caché, no R0 (`REMOTE` con los valores por defecto); (b) la ejecución REMOTE usa WireMock con el contrato, no el servicio con MySQL, así que no detecta la fila 4b; (c) ningún test cubre la caída con la caché caliente (fila 6c) | ⚠️ Verificado | Baja | Era Media |
| 15 | Ciudad sin país | En REMOTE el cliente sigue tratando la ciudad incompleta como catálogo caído. Ahora un solo `city` incompleto en el lote hace fallar todo el lote: la búsqueda da 503 ([CatalogClient.java:104-109](../../../twa-flights-monolith/src/main/java/com/twa/flights/catalog/client/CatalogClient.java)). En LOCAL se calcula igual. Hoy hay 0 filas así | ⚠️ Latente (por código) | Baja | Igual |
| 16 | `city.code` sin `UNIQUE` | Con un duplicado, `findByCode` (LOCAL sin lote) da 503 y el lote (`findFirst`) elige uno sin avisar: L0 y L1/R1 se comportarían distinto. Hoy los 86 códigos son distintos | ⚠️ Latente (por código) | Baja | Cambió: antes coincidían |
| 17 | Esquema de los tests del servicio | Sigue siendo una copia de las migraciones. Mitigado: el contrato generado desde las migraciones hace fallar los tests si los datos cambian sin regenerarlo | ⚠️ Riesgo | Baja | Mejoró |

Otras verificaciones de esta ronda: los `ERROR` del log del monolito son esperables. Hay descartes de itinerarios por
`The arrival of the flight is before the departure`, que vienen de los datos al azar de Microcks y del legacy, no del
catálogo. Los demás son las fallas provocadas en las pruebas y el chequeo de id inexistente del smoke test.

## No verificado

- El 503 del servicio con su base caída, en vivo (fila 9).
- Duplicados y ciudad sin país, en vivo (filas 15 y 16): se razonaron por código.
- Un descarte de `ProviderAlphaService` a las 01:06:45, durante la prueba de la fila 6c: Alpha no loguea la causa. Es
  muy probable que sea el mismo dato de Microcks, porque la búsqueda dio 200 y una falla del catálogo da 503.
- El consumo de conexiones con varias instancias contra el `max_connections` de MySQL (R13).
- El tiempo con red real entre hosts distintos.

## Algo raro que ya estaba antes (no es de esta rama)

Con `from=BUÉ` las respuestas devuelven `origin: "BUÉ"` tal cual, en todas las configuraciones.

## Veredicto (ronda 3): sí, con condiciones

Se cerraron todos los hallazgos medios de la ronda 2: los códigos raros, las 148 llamadas, las credenciales, los
permisos y la suite en un solo modo. En las 4 configuraciones la búsqueda da los mismos resultados que el legacy en
19 de 20 casos, y las fallas del catálogo dan 503 sin guardar nada.

Condiciones para activar:

1. **Activar los tres flags juntos:** `CATALOG_MODE=REMOTE`, `CATALOG_BATCH_ENABLED=true` y
   `CATALOG_CACHE_ENABLED=true`. Es la única combinación que prueba la suite en REMOTE (fila 14a). Hoy compose no tiene
   los dos flags nuevos y sus valores por defecto son `false`.
2. **Aceptar o corregir la fila 4b** (`ＢＵＥ`): es una búsqueda que hoy funciona y con REMOTE deja de funcionar. Es
   baja, pero es una diferencia visible para el usuario y no hay test que la cubra.
3. **Monitorear el servicio de catálogo directamente:** con la caché, una caída de menos de 60 s no se ve en los 503 de
   la búsqueda (fila 6c).
