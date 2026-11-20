-- Events of the catalog (M2 C3, flows F1 and F2 of the decision table): local replica of the cities that the search
-- builds from the events CiudadActualizada of the catalog service, and the events already processed. The search still
-- reads the tables of the catalog: the replica is filled now and the reads change in the next class, with a flag.
-- city_replica has the city with its country and continent as the event brings them (denormalized), by the id of the
-- city in the catalog. A DELETE leaves the row with deleted = 1 (tombstone): an older UPSERT that arrives later can not
-- create the city again, because its sequence is lower than last_sequence.
-- Rollback: db/rollback/U1.13__create_city_replica.sql
CREATE TABLE city_replica (
    city_id bigint NOT NULL,
    code varchar(4) NOT NULL,
    name varchar(50) NULL,
    time_zone varchar(50) NULL,
    country_code varchar(4) NULL,
    country_name varchar(50) NULL,
    continent_code varchar(4) NULL,
    continent_name varchar(50) NULL,
    deleted tinyint(1) NOT NULL DEFAULT 0,
    last_sequence bigint NOT NULL,
    updated_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (city_id),
    KEY idx_city_replica_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- The eventId of every event processed (applied or discarded as old), written in the same transaction as the change
-- of the replica: an event that arrives twice is applied once
CREATE TABLE processed_event (
    event_id varchar(36) NOT NULL,
    event_type varchar(50) NOT NULL,
    sequence bigint NOT NULL,
    processed_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (event_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
