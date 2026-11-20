# Documentación del curso

Esta carpeta reúne el material del curso "Modernización de Aplicaciones Legacy con IA" y lo que se produce en cada
clase. La documentación funcional del monolito (cómo levantarlo, endpoints, parámetros, proveedores) está en el
[README del monolito](../twa-flights-monolith/README.md).

## Estructura

```
docs/
├── README.md        este índice
├── prompts/         prompts de cada clase (material del curso, no se modifica)
└── modulo-N/
    └── clase-N-.../ entregables de esa clase (se crean a medida que avanza el curso)
```

- **`prompts/`** tiene, por clase, un `README.md` con el flujo y tres prompts: `1-investigar.md`, `2-ejecutar.md` y
  `3-validar.md`. Ver [prompts/README.md](prompts/README.md).
- **Los entregables** de cada clase van en una carpeta con el mismo nombre que la de sus prompts. Por ejemplo, el mapa
  del monolito de la clase 2 va en `docs/modulo-1/clase-2-conoce-tu-monolito/`. Así cada clase parte de lo que dejó
  la anterior y es fácil comparar con la solución.
- **Los archivos de trabajo** que generan los comandos de los prompts (`analisis-*`, `ejemplos/`, `copia-flights.sql`,
  `backup-*.sql`) quedan en la raíz del proyecto y git los ignora: son material para adjuntar, no se commitean.
- **Los informes del paso 1** (por ejemplo `informe-investigacion.md`), una vez revisados y corregidos, se guardan en
  la carpeta de la clase, porque son la base del paso 2.

## Clases

| Clase | Prompts | Entregable | Dónde queda |
|-------|---------|------------|-------------|
| M1 · C2 · Conoce tu monolito | [prompts](prompts/modulo-1/clase-2-conoce-tu-monolito/README.md) | Mapa de módulos, glosario, catálogo de APIs y entidades, reglas ocultas | `docs/modulo-1/clase-2-conoce-tu-monolito/` |
| M1 · C3 · ¿Qué partes podemos separar? | [prompts](prompts/modulo-1/clase-3-que-partes-podemos-separar/README.md) | Context map, glosario por contexto, entidades compartidas con dueño y ADR | `docs/modulo-1/clase-3-que-partes-podemos-separar/` |
| M1 · C4 · Crea una red de seguridad | [prompts](prompts/modulo-1/clase-4-crea-una-red-de-seguridad/README.md) | Comportamientos a proteger; tests de caracterización | Análisis en `docs/modulo-1/clase-4-crea-una-red-de-seguridad/`, tests en `twa-flights-monolith/src/test` |
| M1 · C5 · Prepara el monolito | [prompts](prompts/modulo-1/clase-5-prepara-el-monolito/README.md) | Plan de refactor; el resultado es código (monolito modular) | Plan en `docs/modulo-1/clase-5-prepara-el-monolito/`, código en `twa-flights-monolith/src/main` |
| M1 · C6 · Planifica la migración | [prompts](prompts/modulo-1/clase-6-planifica-la-migracion/README.md) | Roadmap de extracción del catálogo y ADR | `docs/modulo-1/clase-6-planifica-la-migracion/` |
| M2 · C1 · Extrae tu primer microservicio | [prompts](prompts/modulo-2/clase-1-extrae-tu-primer-microservicio/README.md) | Servicio del catálogo | Análisis en `docs/modulo-2/clase-1-extrae-tu-primer-microservicio/`, código en `twa-flights-catalog/` |
| M2 · C2 · Separa los datos | [prompts](prompts/modulo-2/clase-2-separa-los-datos/README.md) | Dueño de cada tabla; migraciones por etapa con rollback y outbox | Análisis en `docs/modulo-2/clase-2-separa-los-datos/`, scripts en `twa-flights-monolith/src/main/resources/db` |
| M2 · C3 · Conecta los servicios | [prompts](prompts/modulo-2/clase-3-conecta-los-servicios/README.md) | Decisión REST o evento por flujo; cliente, contrato y evento | `docs/modulo-2/clase-3-conecta-los-servicios/` y código |
| M2 · C4 · Activa progresivamente el nuevo servicio | [prompts](prompts/modulo-2/clase-4-activa-progresivamente-el-nuevo-servicio/README.md) | Métricas, umbrales, alertas, rollback y feature flag | `docs/modulo-2/clase-4-activa-progresivamente-el-nuevo-servicio/` y configuración |
| M2 · C5 · ¿Podemos eliminar el legacy? | [prompts](prompts/modulo-2/clase-5-podemos-eliminar-el-legacy/README.md) | Evidencia de cierre y checklist final | `docs/modulo-2/clase-5-podemos-eliminar-el-legacy/` |

## Cómo trabajar

1. **Antes de la primera clase**, dejá la línea base en verde: `make start` en una terminal y
   `./scripts/smoke-test.sh` en otra. Todas las pruebas deben dar `OK`. Si algo falla más adelante, sabés que lo
   rompió un cambio tuyo y no el entorno.
2. **Abrí Claude Code en la raíz del proyecto**, para que tome `CLAUDE.md`, los permisos, el hook y la skill
   `verify-app` de `.claude/`.
3. **Seguí el orden de cada clase:** investigar (solo lectura), revisar y corregir el resultado, ejecutar lo aprobado
   y validar en una conversación nueva.
4. **Revisá antes de commitear.** Claude no commitea ni pushea salvo que se lo pidas.
