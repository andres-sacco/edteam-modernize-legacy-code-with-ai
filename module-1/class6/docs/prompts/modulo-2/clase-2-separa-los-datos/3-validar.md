# Prompt 3: validar

**Rol:** Revisor escéptico. Verifica los datos.

## Prompt

```text
Rol: Actúa como un revisor escéptico de migraciones de datos.

Contexto: Los scripts, el esquema antes y después, y una copia de datos de prueba.

Tarea: Verificar conteos y checksums, que cada tabla tenga un solo dueño, que no queden FKs cruzadas y que el rollback funcione.

Restricciones: Evidencia con consulta y resultado. No ejecutes nada en producción. Lo no verificable queda "no verificado".

Formato: Tabla: chequeo, evidencia y estado, más un veredicto: ¿se puede migrar?
```

## Tip

Ejecutalo en una conversación nueva. Pedí evidencia como consultas y resultados: conteos, checksums y una prueba del rollback.

```bash
# Conteos y checksums, antes y después de cada etapa
docker exec flights-db mysql -uroot -pmuppet flights -e "SELECT table_name, table_rows FROM information_schema.tables WHERE table_schema='flights';"
docker exec flights-db mysql -uroot -pmuppet flights -e "CHECKSUM TABLE city, country, continent, commercial_policy, commercial_policy_carrier;"

# Foreign keys que quedan
docker exec flights-db mysql -uroot -pmuppet -e "SELECT table_name, column_name, referenced_table_name FROM information_schema.key_column_usage WHERE table_schema='flights' AND referenced_table_name IS NOT NULL;"
```

`table_rows` es aproximado en InnoDB: para conteos exactos usá `SELECT COUNT(*)` por tabla.
