# Prompt 2: ejecutar

**Rol:** Una rama, un paso por vez. Mueve código y crea interfaces.

## Prompt

```text
Rol: Actúa como el desarrollador que aplica el plan de refactor aprobado por el equipo.

Contexto: El plan ya revisado, el repositorio en una rama nueva y la suite de tests actual.

Tarea: Ejecutar solo el paso indicado: mover las clases, crear la interfaz pública del módulo y reemplazar los imports directos.

Restricciones: Un paso por vez. Sin cambios de comportamiento ni de lógica. Compila y corre los tests antes de cada commit.

Formato: Un commit por paso con mensaje claro y un resumen de archivos movidos y tests ejecutados.
```

## Tip

Ejecutalo una vez por cada paso del plan, nunca el plan completo. Si un paso falla, se descarta el commit y se vuelve al anterior.

## Antes de ejecutarlo

```bash
git switch -c refactor/monolito-modular
```

- Indicá en el prompt **qué número de paso** del plan aprobado tiene que ejecutar.
- En este punto el proyecto todavía no tiene tests (la red se construye en la clase 5). Como verificación de cada
  paso usá la compilación y las pruebas de humo contra el stack levantado (skill `verify-app`):

```bash
mvn -q -B clean package -DskipTests
docker compose up -d --build
./scripts/smoke-test.sh
docker compose down
```
