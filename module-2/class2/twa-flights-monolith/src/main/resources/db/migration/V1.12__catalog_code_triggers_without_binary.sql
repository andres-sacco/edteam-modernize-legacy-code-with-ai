-- Fix of the update triggers of V1.9: they compare the codes with the operator BINARY, that MySQL 8.0 marks as
-- deprecated (warning 1287 when the trigger is created). The comparison is the same with the collation
-- utf8mb4_0900_bin: it distinguishes upper and lower case and accents, and it does not ignore the spaces at the end
-- (NO PAD), like BINARY. The logic of the triggers does not change.
-- V1.9 is not edited (a database that already applied it fails the validation of Flyway): the triggers are replaced here.
-- MySQL allows two triggers of the same table, time and event, so the new trigger is created before the old one is
-- dropped and there is no moment without sync. While the two exist they give the same row: the second one finds
-- the id and the code already in sync.
-- Rollback: db/rollback/U1.12__catalog_code_triggers_without_binary.sql
-- Precondition: V1.9 (columns by code and triggers).

DELIMITER //

CREATE TRIGGER trg_country_continent_code_update_v2 BEFORE UPDATE ON country FOR EACH ROW
BEGIN
    DECLARE parent_id bigint;
    DECLARE parent_code varchar(4);
    IF NOT (NEW.continent_id <=> OLD.continent_id
            AND NEW.continent_code COLLATE utf8mb4_0900_bin <=> OLD.continent_code COLLATE utf8mb4_0900_bin) THEN
        IF NEW.continent_id <=> OLD.continent_id THEN
            SET NEW.continent_id = NULL;
        ELSEIF NEW.continent_code COLLATE utf8mb4_0900_bin <=> OLD.continent_code COLLATE utf8mb4_0900_bin THEN
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

CREATE TRIGGER trg_city_country_code_update_v2 BEFORE UPDATE ON city FOR EACH ROW
BEGIN
    DECLARE parent_id bigint;
    DECLARE parent_code varchar(4);
    IF NOT (NEW.country_id <=> OLD.country_id
            AND NEW.country_code COLLATE utf8mb4_0900_bin <=> OLD.country_code COLLATE utf8mb4_0900_bin) THEN
        IF NEW.country_id <=> OLD.country_id THEN
            SET NEW.country_id = NULL;
        ELSEIF NEW.country_code COLLATE utf8mb4_0900_bin <=> OLD.country_code COLLATE utf8mb4_0900_bin THEN
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

DROP TRIGGER trg_country_continent_code_update;
DROP TRIGGER trg_city_country_code_update;
