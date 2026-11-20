-- Rollback of V1.14__create_catalog_rollout.sql. Run by hand (see README.md of this folder).
-- Before running it: the version of the monolith without the flag is deployed (it uses catalog.mode again). The
-- history of the changes of the flag is lost: export it first if it is needed.
DROP TABLE IF EXISTS catalog_rollout_history;
DROP TABLE IF EXISTS catalog_rollout;
DELETE FROM flyway_schema_history WHERE version = '1.14';
