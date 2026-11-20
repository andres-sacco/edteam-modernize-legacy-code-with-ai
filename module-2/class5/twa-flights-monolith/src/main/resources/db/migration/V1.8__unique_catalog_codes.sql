-- Stage 1 of the separation of the data of the catalog (M2 C2): the code is the key of integration between services,
-- so it must be unique before any reference uses it (finding 6 of the extraction plan, decision 3 of ADR-002).
-- The collation utf8mb4_0900_ai_ci ignores case and accents: 'EZE' and 'eze' are the same code, like CityCodes.matches.
-- Online DDL (INPLACE, LOCK=NONE): the readers of the tables are not blocked. If there is a duplicated code the
-- statement fails and the table does not change.
-- Rollback: db/rollback/U1.8__unique_catalog_codes.sql
ALTER TABLE continent ADD CONSTRAINT uk_continent_code UNIQUE (code), ALGORITHM = INPLACE, LOCK = NONE;
ALTER TABLE country ADD CONSTRAINT uk_country_code UNIQUE (code), ALGORITHM = INPLACE, LOCK = NONE;
ALTER TABLE city ADD CONSTRAINT uk_city_code UNIQUE (code), ALGORITHM = INPLACE, LOCK = NONE;
