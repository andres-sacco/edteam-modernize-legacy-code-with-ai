# TWA Flights - Monolith

Aplicación monolítica de búsqueda de vuelos (Spring Boot 3.2, Java 21, MySQL, Flyway). Es el sistema **legacy** del
curso "Modernización de Aplicaciones Legacy con IA": se va a analizar, modularizar, cubrir con tests y migrar a
microservicios. El README de `twa-flights-monolith/` tiene la documentación funcional completa (endpoints, parámetros,
proveedores).

## Estructura del repositorio

- `twa-flights-monolith/`: el monolito (proyecto Maven, `src/`, `Dockerfile`). Las rutas de este archivo que empiezan
  con `src/` son relativas a esta carpeta.
- `twa-flights-catalog/`: el servicio de catálogo extraído en M2 · C1 (puerto 8081, `/api/catalog`).
- Raíz: `pom.xml` agregador (`mvn` desde la raíz compila y testea los dos), `docker-compose*.yml`, `Makefile`,
  `scripts/`, `microcks/`, `docker/` (init de MySQL) y `docs/`.
- Los prompts de `docs/prompts/` usan rutas como `src/main/java` o `target/classes`: esos comandos se corren dentro de
  `twa-flights-monolith/`.

## Reglas de trabajo

- **Es legacy a propósito.** No "arreglar", refactorizar ni modernizar código que no forma parte del pedido, aunque
  parezca un bug o un mal diseño: el comportamiento actual es el que se caracteriza y se migra. Si encontrás algo raro,
  mencionalo en la respuesta en lugar de cambiarlo.
- **Respetar el estilo existente:** paquetes por capa técnica (`controller`, `service`, `repository`, `helper`...),
  POJOs con getters/setters, inyección por constructor con `@Autowired`, `LOGGER` de SLF4J, `Lists`/`Maps` de Guava,
  documentación de Swagger en las interfaces de `controller/documentation`. Los records sólo se usan en `dto/provider`.
- **No leer `docs/NOTAS-INSTRUCTOR.md`**: es material del instructor y su contenido es lo que hay que descubrir.
- **Migraciones de Flyway:** nunca editar ni renombrar una existente; los cambios de esquema o datos van en una nueva
  `twa-flights-monolith/src/main/resources/db/migration/V1.<n>__descripcion.sql` (siguiente número libre). Un hook bloquea la edición.
- **Contratos de Microcks** (`microcks/provider-*-openapi.json` y `microcks/catalog-openapi.json`): son generados. Se
  cambia `microcks/generate-contracts.py` y se regeneran con `python3 microcks/generate-contracts.py`. Un hook bloquea
  la edición directa. El del catálogo sale de las migraciones semilla del monolito y es el contrato que verifican los
  tests del servicio y del cliente del monolito: si cambia un dato del catálogo, hay que regenerarlo.
- **Documentación del curso:** `docs/prompts/` son los prompts de cada clase y no se modifican. Los entregables de cada
  clase van en `docs/` fuera de `docs/prompts/` (ver `docs/README.md`).
- No agregar tests salvo que se pida (la red de seguridad es parte del curso). No commitear ni pushear salvo pedido.

## Comandos

```bash
mvn -q -B clean package -DskipTests          # compilar y empaquetar el monolito y el catálogo
mvn -B -pl twa-flights-monolith test         # tests de un solo proyecto (el monolito corre la suite en los dos
                                             # modos del catálogo; -DskipCatalogRemoteTests saltea el REMOTE)
make start-infra                             # MySQL (3320) + Microcks (8585) para correr desde el IDE
make run-monolith                            # la app en http://localhost:8080/api/flights
make run-catalog                             # el catálogo en http://localhost:8081/api/catalog
./scripts/init-env.sh                        # crea .env con la contraseña del catálogo (una vez, antes de compose)
docker compose up -d --build                 # todo el stack: MySQL + Microcks + importador + 2 instancias de la app
                                             # detrás de flights-lb (8080) + catálogo
make start-observability                     # el stack + Prometheus (9090), Alertmanager (9093) y Grafana (3000)
make test-observability                      # tests de las alertas del rollout (promtool)
./scripts/smoke-test.sh                      # pruebas de humo contra el stack levantado
docker compose down                          # bajar el stack (la base no persiste entre corridas)
python3 microcks/generate-contracts.py       # regenerar los contratos de los proveedores
```

Un cambio no está terminado hasta que compila y pasa `./scripts/smoke-test.sh` con el stack levantado (skill
`verify-app`). Si el cambio agrega o modifica un endpoint, probalo además con `curl` y mirá
`docker logs twa-flights-monolith` y `docker logs twa-flights-monolith-2` buscando `ERROR`.

Para matar la app corrida con `java -jar`, guardá el PID al lanzarla; `pkill -f <jar>` también mata la shell que
ejecuta el comando.

## Mapa rápido

- **API pública:** `GET /api/flights/clusters/itineraries` (búsqueda, filtros, orden, paginación) y
  `GET /api/flights/clusters/{id}/itineraries/{itineraryId}`.
- **Administración de pricing:** `/api/flights/pricing/commercial-policies/**`, requiere header `X-Api-Key`
  (`PRICING_API_KEY`, local `twa-pricing-local-key`).
- **Swagger:** `/api/flights/documentation` (grupos `default` y `pricing`). **Actuator:** `/api/flights/actuator/health`.
- **Flujo de búsqueda:** `ClustersController` → `AvailabilityRequestValidator` → `ClustersService` →
  `ItinerariesSearchService` (proveedores en paralelo vía `facade/*`) → `PricingService` → `ClustersRepositoryImpl`
  (búsqueda guardada en `cluster_search` con TTL) → filtros y orden (`helper/ItineraryFilterHelper`,
  `helper/ItinerarySortHelper`).
- **Proveedores Alpha/Beta/Gamma:** APIs externas simuladas en Microcks, cada una con su formato. `connector/*Connector`
  hace el HTTP y `mapper/provider/*ItineraryMapper` traduce al `ItineraryDTO` interno.
- **Base única `flights`:** catálogo (`continent`, `country`, `city`), pricing (`commercial_policy`,
  `commercial_policy_carrier`) y búsquedas (`cluster_search`).
