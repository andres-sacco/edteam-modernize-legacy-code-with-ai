---
name: verify-app
description: Compila el monolito, levanta el stack completo con Docker (MySQL, Microcks y la app), corre las pruebas de humo y revisa los logs. Usar después de cualquier cambio de código, configuración, migraciones o contratos de Microcks, antes de dar la tarea por terminada, o cuando el usuario pide "verificá", "probalo", "levantá la app".
---

# Verificar la aplicación de punta a punta

Ejecutá los pasos en orden desde la raíz del proyecto y reportá el resultado real de cada uno (no lo des por bueno
sin ver la salida).

1. **Compilar:** `mvn -q -B clean package -DskipTests`. Si falla, corregí el error de compilación antes de seguir.
2. **Levantar el stack:** `docker compose up -d --build`. Si cambiaste `microcks/generate-contracts.py`, regenerá
   antes los contratos con `python3 microcks/generate-contracts.py`.
3. **Esperar a que esté listo** (puede tardar hasta un par de minutos la primera vez):
   `for i in $(seq 1 80); do curl -s localhost:8080/api/flights/actuator/health | grep -q UP && break; sleep 3; done`
   Si no llega a `UP`, mirá `docker logs twa-flights-monolith` y `docker logs microcks-importer`.
4. **Pruebas de humo:** `./scripts/smoke-test.sh`. Todas deben dar `OK`.
5. **Probar lo que cambió:** si el cambio toca un endpoint, filtro o regla, hacé además requests con `curl` que lo
   ejerciten directamente y verificá la respuesta, no sólo el status.
6. **Revisar logs:** `docker logs twa-flights-monolith 2>&1 | grep -E 'ERROR|WARN' | grep -v 'HHH\|open-in-view'`.
   Algunos errores son esperables por los datos aleatorios de los proveedores (por ejemplo
   `Occur an error calculating the flight duration`); cualquier otro hay que explicarlo.
7. **Bajar el stack:** `docker compose down` (sin `-v`).

Al terminar, resumí qué se verificó, qué falló y cómo se resolvió.
