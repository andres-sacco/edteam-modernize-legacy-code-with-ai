-- Rollback of db/copy/V1.1__create_event_relay_checkpoint.sql, run by hand over the database catalog (not flights).
-- Before running it: the relay is off (CATALOG_EVENTS_ENABLED=false). Turning it on again with the version 1.1 starts
-- from the first event of the outbox: the consumers receive every city again and discard what they already applied.
DROP TABLE IF EXISTS event_relay_checkpoint;
DELETE FROM flyway_schema_history WHERE version = '1.1';
