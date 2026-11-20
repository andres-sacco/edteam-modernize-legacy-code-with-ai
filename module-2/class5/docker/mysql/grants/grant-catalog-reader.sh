#!/usr/bin/env bash
# Gives the user of the catalog service SELECT over the tables of the catalog, and only those (least privilege). It
# waits until the Flyway migrations of the monolith create the tables: with docker compose the monolith runs in the
# stack, with "make start-infra" it runs from the IDE. It runs as the container catalog-db-grants and ends.
# It also gives SELECT over catalog_outbox (V1.11), that the publisher of the service reads to keep the copy of the
# catalog in its own database. A version of the monolith without V1.11 (rollback) does not have the table: after
# waiting for it a while, the script ends without that grant.
set -e

: "${MYSQL_HOST:?}" "${MYSQL_ROOT_PASSWORD:?}" "${MYSQL_DATABASE:?}"
CATALOG_DB_USER="${CATALOG_DB_USER:-catalog_reader}"
TABLES="continent country city"
OUTBOX_TABLE="catalog_outbox"
OUTBOX_WAIT_SECONDS="${OUTBOX_WAIT_SECONDS:-60}"

export MYSQL_PWD="${MYSQL_ROOT_PASSWORD}"
mysql_root() {
  mysql -h "${MYSQL_HOST}" -uroot "$@"
}
table_exists() {
  [ "$(mysql_root -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '${MYSQL_DATABASE}'
      AND table_name = '$1'" 2>/dev/null)" = "1" ]
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

# Flyway creates the outbox some seconds after the tables of the catalog, in the same run
waited=0
until table_exists "${OUTBOX_TABLE}" || [ "${waited}" -ge "${OUTBOX_WAIT_SECONDS}" ]; do
  echo "Waiting for ${OUTBOX_TABLE} (V1.11 of the monolith)..."
  sleep 3
  waited=$((waited + 3))
done
if table_exists "${OUTBOX_TABLE}"; then
  mysql_root -e "GRANT SELECT ON \`${MYSQL_DATABASE}\`.\`${OUTBOX_TABLE}\` TO '${CATALOG_DB_USER}'@'%'"
  echo "SELECT granted to ${CATALOG_DB_USER} over ${OUTBOX_TABLE}"
else
  echo "WARNING: ${OUTBOX_TABLE} does not exist, the publisher of the copy of the catalog cannot read it"
fi
# Reads of the cities from the own database of the service (M2 C5, CATALOG_READS_DATABASE=catalog). Its tables are
# created later by the Flyway of the service, the grant over the database already covers them
CATALOG_READS_DB_NAME="${CATALOG_READS_DB_NAME:-catalog}"
mysql_root -e "GRANT SELECT ON \`${CATALOG_READS_DB_NAME}\`.* TO '${CATALOG_DB_USER}'@'%'"
echo "SELECT granted to ${CATALOG_DB_USER} over the database ${CATALOG_READS_DB_NAME}"

mysql_root -e "SHOW GRANTS FOR '${CATALOG_DB_USER}'@'%'"
