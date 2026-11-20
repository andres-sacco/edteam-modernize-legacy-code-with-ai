-- Stage 5 of the separation of the data of the catalog (M2 C2): the own database of the catalog service (catalog),
-- with its own Flyway and its own history (finding 9 of the informe). For now it is a copy of reading: the primary is
-- still flights, and CatalogOutboxPublisher applies here the events of flights.catalog_outbox (ADR-002 D2).
-- Same schema as flights after V1.10 of the monolith: the same ids (D2), the codes unique and the references by code.
-- The columns by id stay without FK, so the current entities of the service can read this database when the reads
-- change (stage 7 of the extraction plan). Same collation as flights: it decides which codes are equal (informe,
-- note 2). Without seed data: the whole copy comes from the outbox (V1.11 of the monolith enqueues every row).
-- Rollback: db/rollback/U1.0__create_catalog_copy_tables.sql
CREATE TABLE continent (
    id bigint NOT NULL,
    code varchar(4) NOT NULL,
    name varchar(50) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_continent_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE country (
    id bigint NOT NULL,
    code varchar(4) NOT NULL,
    name varchar(50) NOT NULL,
    continent_id bigint NULL,
    continent_code varchar(4) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_country_code (code),
    KEY idx_country_continent_id (continent_id),
    CONSTRAINT fk_country_continent_code FOREIGN KEY (continent_code) REFERENCES continent (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE city (
    id bigint NOT NULL,
    code varchar(4) NOT NULL,
    name varchar(50) NOT NULL,
    time_zone varchar(50) NOT NULL,
    country_id bigint NULL,
    country_code varchar(4) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_city_code (code),
    KEY idx_city_country_id (country_id),
    CONSTRAINT fk_city_country_code FOREIGN KEY (country_code) REFERENCES country (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Last event of flights.catalog_outbox applied in this database. It is updated in the same transaction as the change
-- that the event makes, so an event is applied once even if the publisher stops in the middle. The lock of this row
-- also makes the instances of the service publish one at a time
CREATE TABLE outbox_checkpoint (
    id tinyint NOT NULL,
    last_event_id bigint NOT NULL,
    updated_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT ck_outbox_checkpoint_single_row CHECK (id = 1)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO outbox_checkpoint (id, last_event_id) VALUES (1, 0);
