#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export APP_VERSION="${1:?Usage: scripts/rollback.sh previous-commit-sha}"
docker image inspect "shop-admin-backend:$APP_VERSION" >/dev/null
docker image inspect "shop-admin-frontend:$APP_VERSION" >/dev/null
docker compose up -d --no-build --wait --wait-timeout 240 backend frontend
