#!/usr/bin/env bash
# Own database of the catalog service (M2 C2) and its user, owner of only that database. MySQL runs this script only
# the first time that the container creates the database. The tables are created by the Flyway of the service
# (twa-flights-catalog/src/main/resources/db/copy) when it starts with CATALOG_COPY_ENABLED=true.
# The collation is the same as flights: it decides which codes are equal (informe M2 C2, note 2).
set -e

CATALOG_COPY_DB_NAME="${CATALOG_COPY_DB_NAME:-catalog}"
CATALOG_COPY_DB_USER="${CATALOG_COPY_DB_USER:-catalog_writer}"
: "${CATALOG_COPY_DB_PASSWORD:?The password of the user of the database catalog is required (run ./scripts/init-env.sh)}"

mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
CREATE DATABASE IF NOT EXISTS \`${CATALOG_COPY_DB_NAME}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS '${CATALOG_COPY_DB_USER}'@'%' IDENTIFIED BY '${CATALOG_COPY_DB_PASSWORD}';
GRANT ALL PRIVILEGES ON \`${CATALOG_COPY_DB_NAME}\`.* TO '${CATALOG_COPY_DB_USER}'@'%';
SQL
