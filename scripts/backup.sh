#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p backups
chmod 700 backups
umask 077
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --single-transaction --no-tablespaces shop_admin' > "backups/shop-admin-$(date +%Y%m%d-%H%M%S).sql"
