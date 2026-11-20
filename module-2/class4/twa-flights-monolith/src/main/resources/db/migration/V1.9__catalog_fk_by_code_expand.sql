-- Stage 2 of the separation of the data of the catalog (M2 C2): the references inside the catalog use the code instead
-- of the id (expand of expand/contract). The code is the key of integration (informe M2 C2, cross 4): the copy of the
-- catalog in its own database is loaded by code and does not depend on the ids of this database.
-- The columns by id stay: the current entities (City.country, Country.continent) and the version before this one
-- still read them. Two triggers keep the id and the code of each row in sync, so a writer that only knows one of them
-- (for example a seed migration like V1.5, that only sets country_id) leaves the row consistent:
--   - only the id: the code is the code of that parent;
--   - only the code: the id is the id of that parent and the code is written like the parent has it ('br' -> 'BR');
--   - the two: they must be of the same parent;
--   - an id or a code that does not exist, or the two of different parents: the write fails (SQLSTATE 45000).
-- The tables have few rows (7, 246 and 86): the FKs are validated with the copy algorithm in less than a second.
-- Rollback: db/rollback/U1.9__catalog_fk_by_code_expand.sql
-- Precondition: V1.8 (unique codes), the FKs by code reference those indexes.

-- 1. New columns, nullable like the columns by id
ALTER TABLE country ADD COLUMN continent_code varchar(4) NULL AFTER continent_id, ALGORITHM = INSTANT;
ALTER TABLE city ADD COLUMN country_code varchar(4) NULL AFTER country_id, ALGORITHM = INSTANT;

-- 2. Sync of the id and the code in every write. In an update, the column that did not change is calculated again
-- from the one that changed
DELIMITER //

CREATE TRIGGER trg_country_continent_code_insert BEFORE INSERT ON country FOR EACH ROW
BEGIN
    DECLARE parent_id bigint;
    DECLARE parent_code varchar(4);
    IF NEW.continent_id IS NOT NULL OR NEW.continent_code IS NOT NULL THEN
        SELECT id, code INTO parent_id, parent_code FROM continent
        WHERE id = COALESCE(NEW.continent_id, (SELECT k.id FROM continent k WHERE k.code = NEW.continent_code));
        IF parent_id IS NULL OR (NEW.continent_code IS NOT NULL AND NEW.continent_code <> parent_code) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'country: continent_id and continent_code do not match a continent';
        END IF;
        SET NEW.continent_id = parent_id, NEW.continent_code = parent_code;
    END IF;
END//

CREATE TRIGGER trg_country_continent_code_update BEFORE UPDATE ON country FOR EACH ROW
BEGIN
    DECLARE parent_id bigint;
    DECLARE parent_code varchar(4);
    IF NOT (NEW.continent_id <=> OLD.continent_id AND BINARY NEW.continent_code <=> BINARY OLD.continent_code) THEN
        IF NEW.continent_id <=> OLD.continent_id THEN
            SET NEW.continent_id = NULL;
        ELSEIF BINARY NEW.continent_code <=> BINARY OLD.continent_code THEN
            SET NEW.continent_code = NULL;
        END IF;
        IF NEW.continent_id IS NOT NULL OR NEW.continent_code IS NOT NULL THEN
            SELECT id, code INTO parent_id, parent_code FROM continent
            WHERE id = COALESCE(NEW.continent_id, (SELECT k.id FROM continent k WHERE k.code = NEW.continent_code));
            IF parent_id IS NULL OR (NEW.continent_code IS NOT NULL AND NEW.continent_code <> parent_code) THEN
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'country: continent_id and continent_code do not match a continent';
            END IF;
        END IF;
        SET NEW.continent_id = parent_id, NEW.continent_code = parent_code;
    END IF;
END//

CREATE TRIGGER trg_city_country_code_insert BEFORE INSERT ON city FOR EACH ROW
BEGIN
    DECLARE parent_id bigint;
    DECLARE parent_code varchar(4);
    IF NEW.country_id IS NOT NULL OR NEW.country_code IS NOT NULL THEN
        SELECT id, code INTO parent_id, parent_code FROM country
        WHERE id = COALESCE(NEW.country_id, (SELECT k.id FROM country k WHERE k.code = NEW.country_code));
        IF parent_id IS NULL OR (NEW.country_code IS NOT NULL AND NEW.country_code <> parent_code) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'city: country_id and country_code do not match a country';
        END IF;
        SET NEW.country_id = parent_id, NEW.country_code = parent_code;
    END IF;
END//

CREATE TRIGGER trg_city_country_code_update BEFORE UPDATE ON city FOR EACH ROW
BEGIN
    DECLARE parent_id bigint;
    DECLARE parent_code varchar(4);
    IF NOT (NEW.country_id <=> OLD.country_id AND BINARY NEW.country_code <=> BINARY OLD.country_code) THEN
        IF NEW.country_id <=> OLD.country_id THEN
            SET NEW.country_id = NULL;
        ELSEIF BINARY NEW.country_code <=> BINARY OLD.country_code THEN
            SET NEW.country_code = NULL;
        END IF;
        IF NEW.country_id IS NOT NULL OR NEW.country_code IS NOT NULL THEN
            SELECT id, code INTO parent_id, parent_code FROM country
            WHERE id = COALESCE(NEW.country_id, (SELECT k.id FROM country k WHERE k.code = NEW.country_code));
            IF parent_id IS NULL OR (NEW.country_code IS NOT NULL AND NEW.country_code <> parent_code) THEN
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'city: country_id and country_code do not match a country';
            END IF;
        END IF;
        SET NEW.country_id = parent_id, NEW.country_code = parent_code;
    END IF;
END//

DELIMITER ;

-- 3. Backfill of the rows that already exist (after the triggers: a row written meanwhile is already in sync). The
-- update only sets the code, so the trigger calculates the id again from that code: it is the same id
UPDATE country c JOIN continent k ON k.id = c.continent_id SET c.continent_code = k.code WHERE c.continent_code IS NULL;
UPDATE city c JOIN country k ON k.id = c.country_id SET c.country_code = k.code WHERE c.country_code IS NULL;

-- 4. FKs by code. They only accept codes that exist (the case and the accents are ignored, like in the unique index)
ALTER TABLE country ADD CONSTRAINT fk_country_continent_code FOREIGN KEY (continent_code) REFERENCES continent (code);
ALTER TABLE city ADD CONSTRAINT fk_city_country_code FOREIGN KEY (country_code) REFERENCES country (code);
