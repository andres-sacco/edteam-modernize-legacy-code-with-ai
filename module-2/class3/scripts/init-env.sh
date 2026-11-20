#!/usr/bin/env bash
# Creates the file .env of docker compose (ignored by git) with random passwords for the users of the catalog service:
# CATALOG_DB_PASSWORD (read-only user of flights) and CATALOG_COPY_DB_PASSWORD (owner of the database catalog, M2 C2),
# and for the user of RabbitMQ: RABBITMQ_PASSWORD (events of the catalog, M2 C3).
# It only adds the passwords that do not exist yet, so it is safe to run it before every "docker compose up".
# Usage: ./scripts/init-env.sh
set -e
cd "$(dirname "$0")/.."

random_password() {
  openssl rand -hex 16 2>/dev/null || head -c 16 /dev/urandom | od -An -tx1 | tr -d ' \n'
}

for variable in CATALOG_DB_PASSWORD CATALOG_COPY_DB_PASSWORD RABBITMQ_PASSWORD; do
  if [ -f .env ] && grep -q "^${variable}=" .env; then
    continue
  fi
  echo "${variable}=$(random_password)" >> .env
  chmod 600 .env
  echo "Added a random ${variable} to .env"
done
