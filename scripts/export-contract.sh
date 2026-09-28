#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
curl --fail --silent http://127.0.0.1:8080/v3/api-docs -o docs/openapi.json
cd frontend
npm run generate:api
npm run typecheck
