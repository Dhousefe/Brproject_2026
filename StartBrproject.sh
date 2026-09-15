#!/usr/bin/env bash
# Brproject — LicenseInit / Launcher (macOS / Linux)
# Equivalente a start.vbs e StartBrproject.bat
#
# Exit code 2 → reinicia o launcher (mesmo comportamento do start.vbs).

set -uo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

# shellcheck source=cache/brproject-java.inc.sh
source "$ROOT/cache/brproject-java.inc.sh"
# shellcheck source=cache/brproject-classpath.inc.sh
source "$ROOT/cache/brproject-classpath.inc.sh" "$ROOT/libs"

# Auth do launcher: DevAuth com token fixo para auto-login silencioso.
# O AuthService aceita este token quando BRPROJECT_DEV_AUTH=1.
export BRPROJECT_DEV_AUTH="${BRPROJECT_DEV_AUTH:-1}"
export BRPROJECT_DEV_TOKEN="${BRPROJECT_DEV_TOKEN:-brproject-local-dev-2026}"

JVM_FLAGS=(
  -Xms256m
  -Xmx512m
  -Dsun.java2d.opengl=false
  -Dsun.java2d.d3d=false
  -Dsun.java2d.pmoffscreen=false
  -Dbrproject.safe.graphics=true
  -Dbrproject.devAuth=true
)

if [[ "${JAVA_MAJOR:-0}" -ge 25 ]]; then
  JVM_FLAGS+=(-XX:+UseCompactObjectHeaders)
fi

echo "=== Brproject LicenseInit / Launcher ==="
echo "Java: $JAVA_CMD (major=${JAVA_MAJOR:-?})"
echo "Dev auth: BRPROJECT_DEV_AUTH=$BRPROJECT_DEV_AUTH / -Dbrproject.devAuth=true"
echo "Login:    brprojeto@l2jbrasil.com / 12345678"
echo "CWD:      $(pwd)"
echo

while true; do
  "$JAVA_CMD" "${JVM_FLAGS[@]}" -cp "$BRPROJECT_CP" ext.mods.security.LicenseInit
  code=$?

  if [[ $code -eq 2 ]]; then
    echo
    echo "Launcher pediu reinício (exit code 2)..."
    sleep 1
    continue
  fi

  if [[ $code -ne 0 ]]; then
    echo
    echo "LicenseInit encerrou com erro (código $code)."
    return "$code" 2>/dev/null || exit "$code"
  fi

  exit 0
done
