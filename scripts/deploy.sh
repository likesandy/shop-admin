#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${APP_VERSION:?Set APP_VERSION to the immutable commit SHA}"
docker compose build --pull
# Do not automatically reverse database migrations; schema changes must be backward compatible.
docker compose up -d --wait --wait-timeout 240
curl --fail --silent "http://127.0.0.1:${HTTP_PORT:-8088}/" >/dev/null
