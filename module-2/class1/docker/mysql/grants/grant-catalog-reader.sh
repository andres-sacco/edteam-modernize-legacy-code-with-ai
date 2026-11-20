#!/usr/bin/env bash
# Gives the user of the catalog service SELECT over the tables of the catalog, and only those (least privilege). It
# waits until the Flyway migrations of the monolith create the tables: with docker compose the monolith runs in the
# stack, with "make start-infra" it runs from the IDE. It runs as the container catalog-db-grants and ends.
set -e

: "${MYSQL_HOST:?}" "${MYSQL_ROOT_PASSWORD:?}" "${MYSQL_DATABASE:?}"
CATALOG_DB_USER="${CATALOG_DB_USER:-catalog_reader}"
TABLES="continent country city"

export MYSQL_PWD="${MYSQL_ROOT_PASSWORD}"
mysql_root() {
  mysql -h "${MYSQL_HOST}" -uroot "$@"
}

until [ "$(mysql_root -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '${MYSQL_DATABASE}'
    AND table_name IN ('continent', 'country', 'city')" 2>/dev/null)" = "3" ]; do
  echo "Waiting for the tables of the catalog (Flyway of the monolith)..."
  sleep 3
done

for table in $TABLES; do
  mysql_root -e "GRANT SELECT ON \`${MYSQL_DATABASE}\`.\`${table}\` TO '${CATALOG_DB_USER}'@'%'"
done
echo "SELECT granted to ${CATALOG_DB_USER} over ${TABLES}"
mysql_root -e "SHOW GRANTS FOR '${CATALOG_DB_USER}'@'%'"
