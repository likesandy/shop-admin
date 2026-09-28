#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
for tool in java mvn docker python3; do
  command -v "$tool" >/dev/null || { echo "Missing required tool: $tool" >&2; exit 1; }
done
docker info >/dev/null 2>&1 || { echo 'Docker must be running for MySQL/Redis verification.' >&2; exit 1; }
python3 -B -m unittest discover -s scripts/tests -p 'test_*.py'
# No caller-supplied Maven flags: this entry point always runs a clean, full test suite.
RUN_MYSQL_TESTS=true mvn -B -f backend/pom.xml clean verify
python3 scripts/check-test-reports.py
python3 scripts/check-coverage.py
printf '%s\n' 'Backend gate passed: real dependencies, migrations, API contract, tests and coverage.'
