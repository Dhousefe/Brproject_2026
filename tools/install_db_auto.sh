#!/usr/bin/env bash
# =============================================================================
# BrProject — Instalador e Migrador Autonomo de Banco de Dados (Linux / macOS)
# Suporta SQLite, MariaDB, MySQL e PostgreSQL sem regressao.
# =============================================================================

set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# shellcheck source=cache/brproject-java.inc.sh
source "$ROOT/cache/brproject-java.inc.sh"

# Le configuracoes de server.properties como fallback padrao
CONFIG_FILE="$ROOT/game/config/server.properties"
DEFAULT_URL=""
DEFAULT_USER="root"
DEFAULT_PASS="root"

if [[ -f "$CONFIG_FILE" ]]; then
  DEFAULT_URL="$(grep -E '^\s*sql\.url\s*=' "$CONFIG_FILE" | head -n 1 | cut -d '=' -f 2- | tr -d '\r' | sed 's/^[ \t]*//')" || true
  DEFAULT_USER="$(grep -E '^\s*sql\.login\s*=' "$CONFIG_FILE" | head -n 1 | cut -d '=' -f 2- | tr -d '\r' | sed 's/^[ \t]*//')" || true
  DEFAULT_PASS="$(grep -E '^\s*sql\.password\s*=' "$CONFIG_FILE" | head -n 1 | cut -d '=' -f 2- | tr -d '\r' | sed 's/^[ \t]*//')" || true
fi

if [[ -z "$DEFAULT_URL" ]]; then
  DEFAULT_URL="jdbc:sqlite:$ROOT/data/brproject.sqlite"
fi

DB_URL="${1:-$DEFAULT_URL}"
DB_USER="${2:-$DEFAULT_USER}"
DB_PASSWORD="${3:-$DEFAULT_PASS}"

echo "=== BrProject Database Installer & Migrator ==="
echo "Java: $JAVA_CMD (major=${JAVA_MAJOR:-?})"
echo "URL:  $DB_URL"
echo "User: $DB_USER"
echo

if [[ -f "$ROOT/gradlew" && ! -x "$ROOT/gradlew" ]]; then
  chmod +x "$ROOT/gradlew" 2>/dev/null || true
fi

"$ROOT/gradlew" :db-migrate:run \
  -PdbUrl="$DB_URL" \
  -PdbUser="$DB_USER" \
  -PdbPassword="$DB_PASSWORD" \
  --quiet --console=plain

# Registra o marker de ambiente preparado na pasta flag/
mkdir -p "$ROOT/flag"
cat <<EOF > "$ROOT/flag/PrepararTeste.done"
url=$DB_URL
user=$DB_USER
status=INSTALLED_AUTONOMOUS
timestamp=$(date +%s)
EOF

echo
echo "Instalacao concluida com sucesso!"
echo "Marker gravado: flag/PrepararTeste.done"
