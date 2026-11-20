# Prompt 1: investigar

**Rol:** Solo lectura. Asignar dueños y encontrar cruces.

## Prompt

```text
Rol: Actúa como arquitecto de datos especializado en microservicios.

Contexto: Esquema de la base compartida, tablas por módulo, consultas entre módulos y el Catálogo de ciudades ya extraído.

Tarea: Asignar un dueño a cada tabla y listar las foreign keys, joins y accesos que cruzan límites.

Restricciones: Solo lectura. Cita la consulta o la clase de cada cruce. Marca como hipótesis lo que no puedas comprobar.

Formato: Tabla de ownership y lista de cruces con riesgo y alternativa (API, evento o copia de lectura).
```

## Tip

Adjuntá el esquema (DDL), las consultas reales y los logs de queries lentas si existen. Cada cruce debe tener una alternativa: API, evento o copia de lectura.

## Preparar lo que se adjunta

Con la base levantada (`make start-infra` o `docker compose up -d`):

```bash
# Esquema (DDL) de la base compartida
docker exec flights-db mysqldump --no-data -uroot -pmuppet flights > analisis-ddl.sql

# Consultas reales: activá el log de SQL de Hibernate, ejecutá búsquedas y guardá el log
#   spring.jpa.show-sql=true  (o la variable SPRING_JPA_SHOW_SQL=true en docker-compose.yml)
docker logs twa-flights-monolith > analisis-consultas.log 2>&1
```
