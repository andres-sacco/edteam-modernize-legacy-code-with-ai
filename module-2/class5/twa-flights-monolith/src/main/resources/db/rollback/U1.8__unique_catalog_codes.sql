-- Rollback of V1.8__unique_catalog_codes.sql. Flyway Community does not run undo migrations: this script is run by
-- hand (see README.md of this folder) and only after the rollbacks of the later stages (U1.11, U1.10, U1.9).
-- It does not lose data: it only drops indexes.
-- Precondition: the FKs by code of V1.9 do not exist (they need these unique indexes).
ALTER TABLE city DROP INDEX uk_city_code, ALGORITHM = INPLACE, LOCK = NONE;
ALTER TABLE country DROP INDEX uk_country_code, ALGORITHM = INPLACE, LOCK = NONE;
ALTER TABLE continent DROP INDEX uk_continent_code, ALGORITHM = INPLACE, LOCK = NONE;

-- Flyway applies the migration again if the version of the application that has it is started
DELETE FROM flyway_schema_history WHERE version = '1.8';
