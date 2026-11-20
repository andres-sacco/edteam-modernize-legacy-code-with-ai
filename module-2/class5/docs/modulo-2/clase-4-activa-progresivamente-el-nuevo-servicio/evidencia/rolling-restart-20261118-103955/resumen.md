# Reinicio escalonado del monolito (prerrequisito P1)

Generado por `scripts/rolling-restart.sh`: búsquedas constantes (2 clientes) a través de `flights-lb` mientras se
reinicia una instancia por vez, como un rolling deploy de una versión sin cambios.

| Resultado | Valor |
|---|---|
| Búsquedas | 883 |
| Por status | 200: 883 |
| Respuestas distintas de 200 (5xx, conexiones fallidas `000`) | 0 |
| Criterio de P1 (0 respuestas 5xx y 0 conexiones rechazadas) | **Cumple** |

| Segundo | Evento |
|---|---|
| 9.7 | docker restart twa-flights-monolith-2 |
| 16.5 | twa-flights-monolith-2 healthy |
| 31.5 | docker restart twa-flights-monolith |
| 42.1 | twa-flights-monolith healthy |

Archivos: `busquedas.tsv`, `eventos.tsv`, `monolito.log` (apagado y arranque de cada instancia) y `flights-lb.log` (lo que el balanceador no respondió con 200).
