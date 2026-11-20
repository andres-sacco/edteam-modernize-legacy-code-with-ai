#!/usr/bin/env bash
# User of the catalog service (twa-flights-catalog). MySQL runs this script only the first time that the container
# creates the database. The user is created without privileges: the tables continent, country and city do not exist
# yet (the Flyway migrations of the monolith create them later) and MySQL 8 rejects table grants over tables that do
# not exist. The container catalog-db-grants gives SELECT over those three tables when they exist
# (docker/mysql/grants/grant-catalog-reader.sh).
# It is not a Flyway migration on purpose: it is a user of the infrastructure, not a change of the schema or the data.
set -e

CATALOG_DB_USER="${CATALOG_DB_USER:-catalog_reader}"
: "${CATALOG_DB_PASSWORD:?The password of the user of the catalog is required (run ./scripts/init-env.sh)}"

mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
CREATE USER IF NOT EXISTS '${CATALOG_DB_USER}'@'%' IDENTIFIED BY '${CATALOG_DB_PASSWORD}';
SQL
