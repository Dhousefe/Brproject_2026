#!/usr/bin/env bash
# Run Flyway migrations via MigrateMain (Gradle 9-safe; no flyway plugin).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "${ROOT}"

DB_URL="${DB_URL:-jdbc:mariadb://localhost:3306/l2jdb?useUnicode=true&characterEncoding=UTF-8}"
DB_USER="${DB_USER:-brproject}"
DB_PASSWORD="${DB_PASSWORD:-brproject}"

echo "Migrating database..."
echo "  url=${DB_URL}"
echo "  user=${DB_USER}"

./gradlew :db-migrate:run \
  -PdbUrl="${DB_URL}" \
  -PdbUser="${DB_USER}" \
  -PdbPassword="${DB_PASSWORD}" \
  --no-daemon

echo "Flyway migrate finished."
