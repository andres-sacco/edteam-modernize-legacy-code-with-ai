-- Rollback of V1.9__catalog_fk_by_code_expand.sql. Run by hand (see README.md of this folder), after U1.12, U1.11 and U1.10.
-- It does not lose data: the columns by code are a copy of the columns by id, that this stage never stops writing.
-- Precondition: the FKs by id exist (V1.10 not applied, or U1.10 already run).
ALTER TABLE city DROP FOREIGN KEY fk_city_country_code;
ALTER TABLE country DROP FOREIGN KEY fk_country_continent_code;

DROP TRIGGER IF EXISTS trg_city_country_code_update;
DROP TRIGGER IF EXISTS trg_city_country_code_insert;
DROP TRIGGER IF EXISTS trg_country_continent_code_update;
DROP TRIGGER IF EXISTS trg_country_continent_code_insert;

-- Dropping the FK leaves its index: it goes with the column
ALTER TABLE city DROP COLUMN country_code;
ALTER TABLE country DROP COLUMN continent_code;

DELETE FROM flyway_schema_history WHERE version = '1.9';
