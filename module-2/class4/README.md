# TWA Flights

Repositorio del curso "Modernización de Aplicaciones Legacy con IA": el monolito de búsqueda de vuelos y los
servicios que se van extrayendo de él.

```
.
├── docs/                   material del curso y entregables de cada clase
├── twa-flights-monolith/   el monolito (Spring Boot 3.2, Java 21, MySQL, Flyway)
├── twa-flights-catalog/    servicio de catálogo de ciudades extraído del monolito (M2 · C1)
├── microcks/               contratos de los proveedores simulados
├── docker/                 inicialización de MySQL (usuario de solo lectura del catálogo)
├── scripts/                pruebas de humo y chequeo de dependencias entre módulos
├── docker-compose.yml      todo el stack: MySQL, Microcks, monolito y catálogo
└── pom.xml                 agrupa los proyectos: mvn desde la raíz compila y testea todos
```

| Componente           | Puerto | Documentación |
|----------------------|--------|---------------|
| twa-flights-monolith | 8080   | [README](twa-flights-monolith/README.md) |
| twa-flights-catalog  | 8081   | [README](twa-flights-catalog/README.md) |
| MySQL (`flights`)    | 3320   | |
| Microcks             | 8585   | |

## Comandos

```bash
./scripts/init-env.sh           # crea .env con una contraseña al azar para el catálogo (make lo corre solo)
make start                      # compila y levanta todo el stack con Docker
./scripts/smoke-test.sh         # pruebas de humo contra el stack levantado
mvn -B test                     # tests de todos los proyectos (necesita Docker)
make start-infra                # sólo MySQL y Microcks, para correr los proyectos desde el IDE
make run-monolith               # el monolito con start-infra levantado
make run-catalog                # el catálogo con start-infra levantado
```

La guía del curso está en [docs/README.md](docs/README.md).
