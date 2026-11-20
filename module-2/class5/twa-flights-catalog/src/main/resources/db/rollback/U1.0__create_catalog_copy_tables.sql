-- Rollback of db/copy/V1.0__create_catalog_copy_tables.sql, run by hand over the database catalog (not flights).
-- Before running it: the publisher is off (CATALOG_COPY_ENABLED=false) and the service reads flights (it is the only
-- mode today). No data is lost: the copy only has rows of flights, and turning the publisher on again with an empty
-- database builds it again from the first event of the outbox.
DROP TABLE IF EXISTS city;
DROP TABLE IF EXISTS country;
DROP TABLE IF EXISTS continent;
DROP TABLE IF EXISTS outbox_checkpoint;
DROP TABLE IF EXISTS flyway_schema_history;
