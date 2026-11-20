-- Stage 4 of the separation of the data of the catalog (M2 C2): outbox of the changes of continent, country and city.
-- Today this database is the primary of the catalog (only the migrations of Flyway write it). Every change is
-- written in catalog_outbox by a trigger, in the same transaction as the change: if the change fails there is no
-- event, and there is no change without its event. The publisher of the catalog service
-- (com.twa.catalog.outbox.CatalogOutboxPublisher) applies the events in order in the copy of the catalog in its own
-- database (ADR-002 D2, "copy of reading and after that own database").
-- The events have the whole row (the id and the code) and the publisher applies them by id, so applying an event
-- twice gives the same result. A change of id is sent as a DELETE of the old id followed by an UPSERT.
-- The old columns by id go in the payload too: the copy has the same schema and the same ids as this database.
-- Rollback: db/rollback/U1.11__create_catalog_outbox.sql
-- Precondition: V1.9 (the BEFORE triggers fill the code before the AFTER triggers of this migration read it).
CREATE TABLE catalog_outbox (
    id bigint NOT NULL AUTO_INCREMENT,
    aggregate_type varchar(20) NOT NULL,
    aggregate_id bigint NOT NULL,
    operation varchar(10) NOT NULL,
    payload json NOT NULL,
    created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_catalog_outbox_aggregate (aggregate_type, aggregate_id),
    CONSTRAINT ck_catalog_outbox_aggregate_type CHECK (aggregate_type IN ('continent', 'country', 'city')),
    CONSTRAINT ck_catalog_outbox_operation CHECK (operation IN ('UPSERT', 'DELETE'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- continent
CREATE TRIGGER trg_continent_outbox_insert AFTER INSERT ON continent FOR EACH ROW
INSERT INTO catalog_outbox (aggregate_type, aggregate_id, operation, payload)
VALUES ('continent', NEW.id, 'UPSERT', JSON_OBJECT('id', NEW.id, 'code', NEW.code, 'name', NEW.name));

CREATE TRIGGER trg_continent_outbox_update AFTER UPDATE ON continent FOR EACH ROW
INSERT INTO catalog_outbox (aggregate_type, aggregate_id, operation, payload)
SELECT e.aggregate_type, e.aggregate_id, e.operation, e.payload FROM (
    SELECT 1 AS seq, 'continent' AS aggregate_type, OLD.id AS aggregate_id, 'DELETE' AS operation,
           JSON_OBJECT('id', OLD.id, 'code', OLD.code) AS payload FROM DUAL WHERE OLD.id <> NEW.id
    UNION ALL
    SELECT 2, 'continent', NEW.id, 'UPSERT', JSON_OBJECT('id', NEW.id, 'code', NEW.code, 'name', NEW.name)
) e ORDER BY e.seq;

CREATE TRIGGER trg_continent_outbox_delete AFTER DELETE ON continent FOR EACH ROW
INSERT INTO catalog_outbox (aggregate_type, aggregate_id, operation, payload)
VALUES ('continent', OLD.id, 'DELETE', JSON_OBJECT('id', OLD.id, 'code', OLD.code));

-- country
CREATE TRIGGER trg_country_outbox_insert AFTER INSERT ON country FOR EACH ROW
INSERT INTO catalog_outbox (aggregate_type, aggregate_id, operation, payload)
VALUES ('country', NEW.id, 'UPSERT', JSON_OBJECT('id', NEW.id, 'code', NEW.code, 'name', NEW.name,
                                                 'continentId', NEW.continent_id, 'continentCode', NEW.continent_code));

CREATE TRIGGER trg_country_outbox_update AFTER UPDATE ON country FOR EACH ROW
INSERT INTO catalog_outbox (aggregate_type, aggregate_id, operation, payload)
SELECT e.aggregate_type, e.aggregate_id, e.operation, e.payload FROM (
    SELECT 1 AS seq, 'country' AS aggregate_type, OLD.id AS aggregate_id, 'DELETE' AS operation,
           JSON_OBJECT('id', OLD.id, 'code', OLD.code) AS payload FROM DUAL WHERE OLD.id <> NEW.id
    UNION ALL
    SELECT 2, 'country', NEW.id, 'UPSERT', JSON_OBJECT('id', NEW.id, 'code', NEW.code, 'name', NEW.name,
                                                      'continentId', NEW.continent_id,
                                                      'continentCode', NEW.continent_code)
) e ORDER BY e.seq;

CREATE TRIGGER trg_country_outbox_delete AFTER DELETE ON country FOR EACH ROW
INSERT INTO catalog_outbox (aggregate_type, aggregate_id, operation, payload)
VALUES ('country', OLD.id, 'DELETE', JSON_OBJECT('id', OLD.id, 'code', OLD.code));

-- city
CREATE TRIGGER trg_city_outbox_insert AFTER INSERT ON city FOR EACH ROW
INSERT INTO catalog_outbox (aggregate_type, aggregate_id, operation, payload)
VALUES ('city', NEW.id, 'UPSERT', JSON_OBJECT('id', NEW.id, 'code', NEW.code, 'name', NEW.name,
                                              'timeZone', NEW.time_zone, 'countryId', NEW.country_id,
                                              'countryCode', NEW.country_code));

CREATE TRIGGER trg_city_outbox_update AFTER UPDATE ON city FOR EACH ROW
INSERT INTO catalog_outbox (aggregate_type, aggregate_id, operation, payload)
SELECT e.aggregate_type, e.aggregate_id, e.operation, e.payload FROM (
    SELECT 1 AS seq, 'city' AS aggregate_type, OLD.id AS aggregate_id, 'DELETE' AS operation,
           JSON_OBJECT('id', OLD.id, 'code', OLD.code) AS payload FROM DUAL WHERE OLD.id <> NEW.id
    UNION ALL
    SELECT 2, 'city', NEW.id, 'UPSERT', JSON_OBJECT('id', NEW.id, 'code', NEW.code, 'name', NEW.name,
                                                   'timeZone', NEW.time_zone, 'countryId', NEW.country_id,
                                                   'countryCode', NEW.country_code)
) e ORDER BY e.seq;

CREATE TRIGGER trg_city_outbox_delete AFTER DELETE ON city FOR EACH ROW
INSERT INTO catalog_outbox (aggregate_type, aggregate_id, operation, payload)
VALUES ('city', OLD.id, 'DELETE', JSON_OBJECT('id', OLD.id, 'code', OLD.code));

-- Initial load: one UPSERT for every row that already exists, the parents before the children. The publisher builds
-- the whole copy from the outbox, without a second copy of the seed data in the catalog service
INSERT INTO catalog_outbox (aggregate_type, aggregate_id, operation, payload)
SELECT 'continent', id, 'UPSERT', JSON_OBJECT('id', id, 'code', code, 'name', name) FROM continent ORDER BY id;

INSERT INTO catalog_outbox (aggregate_type, aggregate_id, operation, payload)
SELECT 'country', id, 'UPSERT', JSON_OBJECT('id', id, 'code', code, 'name', name, 'continentId', continent_id,
                                            'continentCode', continent_code)
FROM country ORDER BY id;

INSERT INTO catalog_outbox (aggregate_type, aggregate_id, operation, payload)
SELECT 'city', id, 'UPSERT', JSON_OBJECT('id', id, 'code', code, 'name', name, 'timeZone', time_zone,
                                         'countryId', country_id, 'countryCode', country_code)
FROM city ORDER BY id;
