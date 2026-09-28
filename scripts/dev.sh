#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mvn -q -f backend/pom.xml package -DskipTests
(cd backend && java -jar admin-starter/target/admin-starter-1.0.0.jar --spring.profiles.active=local) &
backend_pid=$!
trap 'kill "$backend_pid" 2>/dev/null || true' EXIT INT TERM
cd frontend
npm ci
npm run dev
