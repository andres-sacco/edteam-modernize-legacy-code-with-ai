# Módulo 1 · Clase 2 · Conoce tu monolito con IA

Tres prompts, tres roles: investigar, ejecutar y validar por separado. Separar los roles evita que la IA se confirme
a sí misma: **entre cada prompt, una persona revisa**.

| Paso | Archivo | Rol | Alcance | Resultado |
|------|---------|-----|---------|-----------|
| 1 | [1-investigar.md](1-investigar.md) | Arquitecto que investiga | Solo lectura | Informe: dominios, dependencias, datos, reglas ocultas, antipatrones y preguntas para el negocio |
| 2 | [2-ejecutar.md](2-ejecutar.md) | Arquitecto que documenta | Escribe solo en `/docs` | Mapa de módulos, glosario, catálogo de APIs y entidades, reglas ocultas (Markdown + Mermaid) |
| 3 | [3-validar.md](3-validar.md) | Revisor escéptico | Solo lectura, conversación nueva | Tabla afirmación / evidencia / estado y veredicto final |

## Flujo

1. Ejecutá el prompt 1 y revisá el informe a mano: corregí lo que esté mal y respondé las preguntas abiertas.
2. Ejecutá el prompt 2 con el informe corregido. Solo genera documentación en `/docs`, con un commit por archivo.
3. Abrí una conversación nueva y ejecutá el prompt 3. Revisá su tabla y corregí lo marcado como incorrecto o no
   verificado antes de dar el mapa por bueno.

El mapa que queda en `/docs` es la base de la próxima clase, donde se decide dónde cortar el monolito.
