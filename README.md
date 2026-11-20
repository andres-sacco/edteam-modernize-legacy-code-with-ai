# Modernización de Aplicaciones Legacy con IA — EDteam

Código del curso **"Modernización de Aplicaciones Legacy con IA"** de EDteam. A lo largo del curso se toma una
aplicación legacy, **TWA Flights**, y se la analiza, modulariza, cubre con tests y migra a microservicios con ayuda de
Claude Code.

Este repositorio reúne en carpetas el punto de partida y el resultado de cada clase. Así podés abrir cualquier clase,
ver cómo quedó el proyecto al terminarla y compararla con la anterior sin cambiar de rama.

## La aplicación: TWA Flights

TWA Flights es un **monolito de búsqueda de vuelos** hecho con **Java 21, Spring Boot 3.2, Maven, MySQL y Flyway**.
Reúne en un solo despliegue la lógica que antes estaba repartida en varias APIs (`api-clusters`, `api-catalog`,
`api-pricing`, `api-itineraries-search` y los proveedores).

- **Búsqueda de clusters:** `GET /api/flights/clusters/itineraries` devuelve itinerarios con precio final, con
  filtros, orden y paginación. `GET /api/flights/clusters/{id}/itineraries/{itineraryId}` devuelve el detalle.
- **Proveedores Alpha, Beta y Gamma:** APIs externas simuladas con [Microcks](https://microcks.io), cada una con su
  propio formato. El monolito las consulta en paralelo y traduce las respuestas a un modelo interno.
- **Pricing:** calcula el markup de cada aerolínea según políticas comerciales, que se administran en
  `/api/flights/pricing/commercial-policies/**` con el header `X-Api-Key`.
- **Catálogo:** continentes, países y ciudades que se usan para validar y enriquecer la búsqueda.
- **Una base única `flights`** con el catálogo, el pricing y las búsquedas guardadas.
- **Swagger** en `/api/flights/documentation` y **Actuator** en `/api/flights/actuator/health`.

La documentación funcional completa (cómo levantarla, endpoints, parámetros, proveedores) está en el
[README del código base](base-code/README.md).

## Distribución del repositorio

```
./
├── base-code/          el monolito original, punto de partida del curso
├── module-1/           Módulo 1: entender y preparar el monolito
│   ├── class2/         resultado de la clase 2
│   ├── class3/
│   ├── class4/
│   ├── class5/
│   └── class6/
└── module-2/           Módulo 2: migrar a microservicios
    ├── class1/
    ├── class2/
    ├── class3/
    ├── class4/
    └── class5/
```

**Cada carpeta es un proyecto completo e independiente.** Tiene su propio `README.md`, `CLAUDE.md`, `pom.xml`,
`Makefile`, `docker-compose.yml` y la configuración de Claude Code en `.claude/`. No hace falta ninguna otra carpeta
para compilarla ni levantarla.

**Las clases son acumulativas.** Cada carpeta contiene todo lo de la clase anterior más lo que se agregó en esa clase.
Por ejemplo, `module-2/class3/` incluye los entregables de todo el Módulo 1 y de las clases 1 y 2 del Módulo 2.

La **clase 1 del Módulo 1** es introductoria y no cambia el código: su punto de partida es `base-code/`.

### Qué hay dentro de cada carpeta

- `docs/prompts/`: los prompts de cada clase. Cada clase tiene un `README.md` con el flujo y tres prompts:
  `1-investigar.md`, `2-ejecutar.md` y `3-validar.md`.
- `docs/modulo-N/clase-N-.../`: los entregables de cada clase (análisis, informes, ADRs, planes y checklists). Aparecen
  a medida que avanza el curso.
- El código: en el Módulo 1 el monolito está en la raíz de la carpeta (`src/`). Desde el Módulo 2 el proyecto pasa a
  tener un `pom.xml` agregador con dos proyectos: `twa-flights-monolith/` y `twa-flights-catalog/`.

## Clases

| Carpeta | Clase | Qué se agrega |
|---------|-------|---------------|
| [`base-code/`](base-code) | Punto de partida | El monolito legacy, los prompts del curso y la skill `verify-app` |
| [`module-1/class2/`](module-1/class2) | M1 · C2 · Conoce tu monolito | Mapa de módulos, glosario, catálogo de APIs y entidades, reglas ocultas |
| [`module-1/class3/`](module-1/class3) | M1 · C3 · ¿Qué partes podemos separar? | Context map, glosario por contexto, entidades compartidas con dueño y ADR |
| [`module-1/class4/`](module-1/class4) | M1 · C4 · Crea una red de seguridad | Comportamientos a proteger y tests de caracterización |
| [`module-1/class5/`](module-1/class5) | M1 · C5 · Prepara el monolito | Plan de refactor y el monolito reorganizado como monolito modular |
| [`module-1/class6/`](module-1/class6) | M1 · C6 · Planifica la migración | Roadmap de extracción del catálogo y ADR |
| [`module-2/class1/`](module-2/class1) | M2 · C1 · Extrae tu primer microservicio | El servicio del catálogo (`twa-flights-catalog`, puerto 8081) |
| [`module-2/class2/`](module-2/class2) | M2 · C2 · Separa los datos | Dueño de cada tabla, migraciones por etapa con rollback y outbox |
| [`module-2/class3/`](module-2/class3) | M2 · C3 · Conecta los servicios | REST o evento por flujo; cliente, contrato y evento (RabbitMQ) |
| [`module-2/class4/`](module-2/class4) | M2 · C4 · Activa progresivamente el nuevo servicio | Métricas, umbrales, alertas, rollback y feature flag (Prometheus, Alertmanager y Grafana) |
| [`module-2/class5/`](module-2/class5) | M2 · C5 · ¿Podemos eliminar el legacy? | Evidencia de cierre, simulacro de rollback y checklist final |

Los entregables de cada clase están en `docs/modulo-N/clase-N-.../` dentro de su carpeta. Por ejemplo, el mapa del
monolito de la clase 2 está en
[`module-1/class2/docs/modulo-1/clase-2-conoce-tu-monolito/`](module-1/class2/docs/modulo-1/clase-2-conoce-tu-monolito).

## Cómo usarlo

### Requisitos

- Java 21+
- Maven 3.9+
- Docker
- Python 3 (para regenerar los contratos de Microcks)
- [Claude Code](https://docs.claude.com/en/docs/claude-code), para seguir los prompts

### Levantar una clase

Entrá en la carpeta de la clase y seguí su `README.md`. Por ejemplo, para el código base:

```bash
cd base-code
```

```bash
make start
```

Desde el Módulo 2, antes del primer `docker compose` hay que crear el `.env` con las contraseñas locales:

```bash
cd module-2/class5
```

```bash
./scripts/init-env.sh
```

```bash
docker compose up -d --build
```

Para comprobar que todo funciona, corré las pruebas de humo con el stack levantado:

```bash
./scripts/smoke-test.sh
```

Todas las carpetas usan los mismos puertos (8080, 3320, 8585…), así que **levantá una sola clase a la vez**. Bajá el
stack con `docker compose down` antes de pasar a otra.

### Seguir el curso con Claude Code

Abrí Claude Code **dentro de la carpeta de la clase** (no en la raíz de este repositorio). Así toma el `CLAUDE.md`, los
permisos, el hook y la skill `verify-app` de esa carpeta.

Para hacer una clase vos mismo, partí de la carpeta de la clase anterior y seguí los prompts de
`docs/prompts/modulo-N/clase-N-.../`. Después compará tu resultado con la carpeta de la clase.

### Comparar dos clases

Para ver qué cambió en una clase, compará su carpeta con la de la clase anterior:

```bash
diff -ru module-1/class4 module-1/class5
```

```bash
git diff --no-index --stat module-2/class3 module-2/class4
```

## Licencia

[MIT](LICENSE)
