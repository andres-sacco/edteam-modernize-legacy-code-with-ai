# Prompt 3: validar

**Rol:** Revisor escéptico. Verifica que nada cambió.

## Prompt

```text
Rol: Actúa como un revisor escéptico: tu trabajo es probar que el refactor no cambió el comportamiento.

Contexto: El diff de la rama, el plan aprobado y los resultados de los tests antes y después.

Tarea: Verificar que solo se movió código, que no quedan imports cruzados y que las interfaces públicas son mínimas y se respetan.

Restricciones: Evidencia con archivo y línea. No corrijas nada. Lo que no puedas verificar se marca como "no verificado".

Formato: Tabla: chequeo, evidencia y estado (verificado, no verificado, incorrecto), más un veredicto sobre el merge.
```

## Tip

Ejecutalo en una conversación nueva. Complementalo con una regla de ArchUnit o Spring Modulith que falle si alguien vuelve a importar una clase interna de otro módulo.

```bash
# Diff de la rama contra la rama base
git diff --stat main...HEAD
git diff main...HEAD > analisis-diff-refactor.txt

# Dependencias entre paquetes después del refactor
mvn -q -B clean package -DskipTests
jdeps -verbose:package -filter:none -e 'com\.twa\.flights.*' target/classes > analisis-jdeps-despues.txt
```
