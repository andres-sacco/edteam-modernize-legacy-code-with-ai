# Prompt 3: validar

**Rol:** Revisor escéptico. Intenta que falle.

## Prompt

```text
Rol: Actúa como un revisor escéptico: tu trabajo es probar que el servicio no es idéntico al módulo.

Contexto: El servicio, el módulo original, los tests de caracterización y el diff de la rama.

Tarea: Comparar respuestas de ambos, probar timeouts y caídas, y buscar credenciales fijas, entidades expuestas y tests solo del camino feliz.

Restricciones: Evidencia con archivo y línea. No corrijas nada. Lo que no puedas verificar queda "no verificado".

Formato: Tabla: chequeo, evidencia, estado y severidad, más un veredicto: ¿se puede activar?
```

## Tip

Ejecutalo en una conversación nueva. Complementalo levantando el servicio solo, sin el monolito, y apagándolo para ver cómo reacciona Búsqueda.

```bash
git diff --stat main...HEAD
git diff main...HEAD > analisis-diff-extraccion.txt

# Con el stack levantado: apagar el servicio del catálogo y ver cómo reacciona la búsqueda
docker compose stop <servicio-catalogo>
./scripts/smoke-test.sh
docker compose start <servicio-catalogo>
```
