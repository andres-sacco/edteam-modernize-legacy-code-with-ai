# Prompts del curso

Prompts para ejecutar sobre este proyecto, organizados por módulo, clase y rol. Cada clase tiene un `README.md` con
el flujo y un archivo por rol, numerado en el orden de ejecución.

```
prompts/
├── modulo-1/
│   ├── clase-2-conoce-tu-monolito/          mapa del monolito en /docs
│   ├── clase-3-que-partes-podemos-separar/  bounded contexts, context map y ADR en /docs
│   ├── clase-4-prepara-el-monolito/         refactor a monolito modular (modifica código)
│   ├── clase-5-crea-una-red-de-seguridad/   tests de caracterización en src/test
│   └── clase-6-planifica-la-migracion/      plan de extracción del catálogo en /docs
└── modulo-2/
    ├── clase-1-extrae-tu-primer-microservicio/            servicio del catálogo (crea código)
    ├── clase-2-separa-los-datos/                          migraciones por etapa, outbox y rollback
    ├── clase-3-conecta-los-servicios/                     cliente REST, contrato OpenAPI y evento
    ├── clase-4-activa-progresivamente-el-nuevo-servicio/  métricas, umbrales, alertas y feature flag
    └── clase-5-podemos-eliminar-el-legacy/                evidencia y cierre del camino viejo
```

Cada carpeta de clase contiene:

| Archivo | Rol |
|---------|-----|
| `README.md` | Flujo de la clase y qué produce cada paso |
| `1-investigar.md` | Solo lectura: investiga y propone |
| `2-ejecutar.md` | Ejecuta lo aprobado, con el alcance de escritura de la clase |
| `3-validar.md` | Revisor escéptico, en una conversación nueva |

Cada prompt sigue la misma estructura: **Rol, Contexto, Tarea, Restricciones y Formato**, más un tip de uso. El bloque
de texto de cada archivo está listo para copiar y adaptar.
