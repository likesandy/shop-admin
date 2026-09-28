#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${1:?Usage: scripts/restore.sh backup.sql}"
# Intentionally explicit: restoring replaces target tables. Stop application writes first.
docker compose stop frontend backend
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot shop_admin' < "$1"
docker compose up -d --wait backend frontend
