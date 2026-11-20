-- Rollback of V1.11__create_catalog_outbox.sql. Run by hand (see README.md of this folder), after U1.12 and before
-- U1.10. U1.12 goes first: U1.9 drops the update triggers by their names of V1.9, so the triggers of V1.12 would stay
-- and every update of city and country would fail.
-- Before running it: the publisher of the catalog service is off (CATALOG_COPY_ENABLED=false). The outbox only has
-- events derived from continent, country and city, that do not change: no data of the catalog is lost. The copy in
-- the database catalog stays as it was (see
-- twa-flights-catalog/src/main/resources/db/rollback/U1.0__create_catalog_copy_tables.sql to drop it too).
DROP TRIGGER IF EXISTS trg_city_outbox_delete;
DROP TRIGGER IF EXISTS trg_city_outbox_update;
DROP TRIGGER IF EXISTS trg_city_outbox_insert;
DROP TRIGGER IF EXISTS trg_country_outbox_delete;
DROP TRIGGER IF EXISTS trg_country_outbox_update;
DROP TRIGGER IF EXISTS trg_country_outbox_insert;
DROP TRIGGER IF EXISTS trg_continent_outbox_delete;
DROP TRIGGER IF EXISTS trg_continent_outbox_update;
DROP TRIGGER IF EXISTS trg_continent_outbox_insert;

-- MySQL keeps the privileges of a table after dropping it (docker/mysql/grants/grant-catalog-reader.sh gives it)
REVOKE IF EXISTS SELECT ON catalog_outbox FROM 'catalog_reader'@'%' IGNORE UNKNOWN USER;

DROP TABLE IF EXISTS catalog_outbox;

DELETE FROM flyway_schema_history WHERE version = '1.11';
