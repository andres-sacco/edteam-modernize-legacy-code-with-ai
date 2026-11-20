-- Rollback of V1.12__catalog_code_triggers_without_binary.sql. Run by hand (see README.md of this folder), before
-- U1.11. It does not lose data: it only changes triggers, with the same logic.
-- It creates again the triggers of V1.9 (with BINARY: MySQL shows the warning 1287) before dropping the new ones, so
-- there is no moment without sync.
DELIMITER //

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

DROP TRIGGER trg_country_continent_code_update_v2;
DROP TRIGGER trg_city_country_code_update_v2;

DELETE FROM flyway_schema_history WHERE version = '1.12';
