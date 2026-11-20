-- Rollback of V1.10__catalog_fk_by_code_contract.sql. Run by hand (see README.md of this folder), after U1.11.
-- It creates again the FKs by id of V1.0 with the same names (the entities and Hibernate expect them).
-- It does not lose data. If a row has an id that does not exist (impossible while the triggers of V1.9 are active) the
-- statement fails and the table does not change: the query below shows those rows before trying.
SELECT 'city' AS tbl, c.id, c.country_id FROM city c LEFT JOIN country k ON k.id = c.country_id
WHERE c.country_id IS NOT NULL AND k.id IS NULL
UNION ALL
SELECT 'country', c.id, c.continent_id FROM country c LEFT JOIN continent k ON k.id = c.continent_id
WHERE c.continent_id IS NOT NULL AND k.id IS NULL;

ALTER TABLE country ADD CONSTRAINT FKpymfsgrl32dy3gtl9r7rykkjg FOREIGN KEY (continent_id) REFERENCES continent (id);
ALTER TABLE city ADD CONSTRAINT FKrpd7j1p7yxr784adkx4pyepba FOREIGN KEY (country_id) REFERENCES country (id);

DELETE FROM flyway_schema_history WHERE version = '1.10';
