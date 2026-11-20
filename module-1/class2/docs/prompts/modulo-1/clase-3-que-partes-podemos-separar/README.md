# Módulo 1 · Clase 3 · ¿Qué partes podemos separar?

Con el mapa de la clase anterior, ahora decidimos dónde cortar. Tres prompts, tres roles: **la IA propone, el equipo decide**. Cada prompt termina con una revisión humana.

| Paso | Archivo | Rol | Alcance | Resultado |
|------|---------|-----|---------|-----------|
| 1 | [1-investigar.md](1-investigar.md) | Arquitecto de dominio (DDD) | Solo lectura | Bounded contexts candidatos con evidencia, cohesión y acoplamiento |
| 2 | [2-ejecutar.md](2-ejecutar.md) | Arquitecto que documenta | Escribe solo en `/docs` | Context map (Mermaid), glosario por contexto, entidades compartidas con dueño y ADR |
| 3 | [3-validar.md](3-validar.md) | Revisor escéptico | Solo lectura, conversación nueva | Tabla límite / hallazgo / evidencia / severidad / recomendación |

## Flujo

1. Ejecutá el prompt 1 y revisá la propuesta con alguien del negocio: un límite sin sentido de negocio es un límite equivocado.
2. Ejecutá el prompt 2 con la propuesta corregida. Solo genera documentación en `/docs`, con un commit por archivo.
3. Abrí una conversación nueva y ejecutá el prompt 3, que intenta refutar cada límite recorriendo una búsqueda y una cotización.

En la próxima clase se ordena el monolito para que esos límites existan dentro del código.
