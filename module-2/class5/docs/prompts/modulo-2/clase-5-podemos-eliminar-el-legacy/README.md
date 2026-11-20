# Módulo 2 · Clase 5 · ¿Podemos eliminar el Legacy?

Validamos y cerramos la migración con confianza. **Primero evidencia, después borrado, y todo reversible con git.** Acá el rol de validar es el más escéptico: busca razones para NO apagar el camino viejo.

| Paso | Archivo | Rol | Alcance | Resultado |
|------|---------|-----|---------|-----------|
| 1 | [1-investigar.md](1-investigar.md) | Ingeniero de confiabilidad | Solo lectura | Evidencia por señal, consumidores pendientes y riesgos abiertos |
| 2 | [2-ejecutar.md](2-ejecutar.md) | Ingeniero que ejecuta el cierre | Una rama, un paso por vez | Flag y código local eliminados, tablas archivadas y documentación actualizada |
| 3 | [3-validar.md](3-validar.md) | Revisor escéptico | Conversación nueva, antes de mergear | Checklist de cierre con estado por condición, veredicto final y pendientes |

## Flujo

1. Ejecutá el prompt 1 con exportaciones de los dashboards: lo que no esté medido queda "sin evidencia".
2. Ejecutá el prompt 2 archivando primero (tag en git y backup de la base) y eliminando después, un pull request por paso.
3. Abrí una conversación nueva y ejecutá el prompt 3 antes de mergear: no se aprueba si falta evidencia.

Con esto se cierra el curso: el monolito se modernizó sin reescribirlo desde cero, con cada paso controlado y reversible.
