-- Rollback of V1.13__create_city_replica.sql. Run by hand (see README.md of this folder).
-- Before running it: the consumer is off (CATALOG_EVENTS_ENABLED=false). No data is lost: the replica is derived from
-- the events of the catalog. The queue keeps the events that arrive meanwhile; to rebuild the replica from the first
-- city, also set to 0 the row 'ciudad-actualizada' of event_relay_checkpoint in the database catalog.
DROP TABLE IF EXISTS processed_event;
DROP TABLE IF EXISTS city_replica;
DELETE FROM flyway_schema_history WHERE version = '1.13';
