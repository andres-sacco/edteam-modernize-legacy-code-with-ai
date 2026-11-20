-- Rollout of the catalog service (M2 C4): the flag that decides which percentage of the calls to the catalog go to the
-- remote path (CatalogClient) instead of the local one (CatalogService). The monolith reads it every
-- catalog.rollout.refresh (5 s), so a change, and the rollback, does not need a deploy or a restart.
-- remote_percentage NULL: the property catalog.mode decides (LOCAL = 0 %, REMOTE = 100 %), the behavior before this
-- migration. A value from 0 to 100 has priority over the property.
-- Rollback: db/rollback/U1.14__create_catalog_rollout.sql
CREATE TABLE catalog_rollout (
    id tinyint NOT NULL,
    remote_percentage tinyint NULL,
    updated_by varchar(50) NOT NULL,
    reason varchar(255) NULL,
    updated_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT chk_catalog_rollout_percentage CHECK (remote_percentage BETWEEN 0 AND 100)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO catalog_rollout (id, remote_percentage, updated_by, reason)
VALUES (1, NULL, 'flyway', 'The property catalog.mode decides');

-- Who changed the flag, when and why (prerequisite P2 of the extraction plan), including the automatic rollbacks
CREATE TABLE catalog_rollout_history (
    id bigint NOT NULL AUTO_INCREMENT,
    remote_percentage tinyint NULL,
    updated_by varchar(50) NOT NULL,
    reason varchar(255) NULL,
    updated_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
