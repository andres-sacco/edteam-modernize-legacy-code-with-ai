#!/usr/bin/env bash
# Creates the file .env of docker compose (ignored by git) with a random password for the read-only user of the catalog
# service. It does nothing if the password already exists, so it is safe to run it before every "docker compose up".
# Usage: ./scripts/init-env.sh
set -e
cd "$(dirname "$0")/.."

if [ -f .env ] && grep -q '^CATALOG_DB_PASSWORD=' .env; then
  exit 0
fi

password=$(openssl rand -hex 16 2>/dev/null || head -c 16 /dev/urandom | od -An -tx1 | tr -d ' \n')
echo "CATALOG_DB_PASSWORD=${password}" >> .env
chmod 600 .env
echo "Created .env with a random CATALOG_DB_PASSWORD"
