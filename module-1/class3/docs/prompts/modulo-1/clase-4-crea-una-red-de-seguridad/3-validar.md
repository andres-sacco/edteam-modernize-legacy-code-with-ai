# Prompt 3: validar

**Rol:** Revisor escéptico. Intenta romper la red.

## Prompt

```text
Rol: Actúa como un revisor escéptico: tu trabajo es demostrar que la red tiene agujeros.

Contexto: La suite generada, el código de producción y el reporte de cobertura de ramas.

Tarea: Verificar que los tests pasan y fallan ante bugs a propósito; detectar asserts triviales, mocks excesivos y casos inventados.

Restricciones: Evidencia con archivo y línea. No modifiques tests ni código. Lo que no puedas verificar queda "no verificado".

Formato: Tabla: test o módulo, hallazgo, evidencia y severidad, más un veredicto: ¿la red permite migrar?
```

## Tip

Ejecutalo en una conversación nueva. Complementalo con cobertura de ramas (JaCoCo) y con mutation testing (PIT), que introduce bugs automáticamente y mide cuántos detectan los tests.

```bash
# Los tests tienen que pasar
mvn -B test

# Solo cambiaron archivos de src/test (<commit-base>: commit anterior a los generados por el prompt 2)
git diff --stat <commit-base>..HEAD -- . ':(exclude)src/test'   # debe salir vacío

# Cobertura de ramas, sin agregar plugins al pom
mvn -B org.jacoco:jacoco-maven-plugin:prepare-agent test org.jacoco:jacoco-maven-plugin:report
```

El reporte queda en `target/site/jacoco/index.html`.

Para mutation testing con PIT hay que agregar al `pom.xml` el plugin `org.pitest:pitest-maven` con la dependencia
`org.pitest:pitest-junit5-plugin` (sin ella PIT no encuentra los tests JUnit 5) y ejecutar
`mvn -B test-compile org.pitest:pitest-maven:mutationCoverage`. El reporte queda en `target/pit-reports/`.
