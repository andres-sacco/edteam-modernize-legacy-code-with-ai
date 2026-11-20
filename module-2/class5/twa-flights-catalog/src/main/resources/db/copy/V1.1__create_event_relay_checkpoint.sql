-- Events of the catalog in RabbitMQ (M2 C3): last event of flights.catalog_outbox that CityEventRelay published as
-- CiudadActualizada (with the confirm of the broker). It is independent of outbox_checkpoint: the copy of the catalog
-- and the broker advance each one at its own pace, and one of them down does not stop the other.
-- The lock of the row makes the instances of the service publish one at a time, in the order of the outbox.
-- Rollback: db/rollback/U1.1__create_event_relay_checkpoint.sql
CREATE TABLE event_relay_checkpoint (
    relay varchar(50) NOT NULL,
    last_event_id bigint NOT NULL,
    updated_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (relay)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- From the first event: the initial load of V1.11 of the monolith (an UPSERT for each city) is the snapshot that
-- fills the replica of the consumers
INSERT INTO event_relay_checkpoint (relay, last_event_id) VALUES ('ciudad-actualizada', 0);
