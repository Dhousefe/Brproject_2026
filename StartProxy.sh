#!/usr/bin/env bash
# Brproject — Netty Reverse Proxy (macOS / Linux)
# Inicializa o proxy de borda TCP / HTTP com suporte a Epoll nativo e Fail2Ban.

set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

# shellcheck source=cache/brproject-java.inc.sh
source "$ROOT/cache/brproject-java.inc.sh"

echo "=== Brproject Netty Reverse Proxy ==="
echo "Java: $JAVA_CMD (major=${JAVA_MAJOR:-?})"
echo "CWD:  $(pwd)"
echo

# Garante permissao de execucao no gradlew se existir
if [[ -f "$ROOT/gradlew" && ! -x "$ROOT/gradlew" ]]; then
  chmod +x "$ROOT/gradlew" 2>/dev/null || true
fi

# Executa via Gradle wrapper com passagem de argumentos
exec "$ROOT/gradlew" :proxy:run --quiet --console=plain "$@"
