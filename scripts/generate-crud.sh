#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ $# -lt 2 || $# -gt 3 ]]; then
  echo "Usage: DB_URL=... DB_USER=... DB_PASSWORD=... bash scripts/generate-crud.sh <table> <resource> [output-directory]" >&2
  exit 2
fi
: "${DB_URL:?Set DB_URL}"
: "${DB_USER:?Set DB_USER}"
: "${DB_PASSWORD:?Set DB_PASSWORD}"
table="$1"
resource="$2"
output="${3:-generated/$table}"
mvn -B -q -f backend/pom.xml -pl admin-generator -am package -DskipTests
mvn -B -q -f backend/admin-generator/pom.xml exec:java \
  -Dexec.mainClass=com.acme.admin.generator.CrudGenerator \
  -Dexec.args="$table $resource $output"
echo "Generated scaffold: $output"
