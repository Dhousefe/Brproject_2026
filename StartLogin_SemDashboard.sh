#!/usr/bin/env bash
# Brproject — Login Server (sem dashboard) — macOS / Linux
# Equivalente a StartLogin_SemDashboard.bat

set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

# shellcheck source=cache/brproject-java.inc.sh
source "$ROOT/cache/brproject-java.inc.sh"
# shellcheck source=cache/brproject-g1-reclaim.inc.sh
source "$ROOT/cache/brproject-g1-reclaim.inc.sh"
# shellcheck source=cache/brproject-classpath.inc.sh
source "$ROOT/cache/brproject-classpath.inc.sh" "$ROOT/libs"

JVM_FLAGS=(
  -Xms256m -Xmx256m
  -Dext.mods.Config.dataPath=../game/data
  -XX:+UseG1GC
  -XX:MaxGCPauseMillis=200
  -XX:G1HeapRegionSize=8m
  -XX:+UseStringDeduplication
  -XX:+UseCompressedOops
  -XX:+TieredCompilation
  -XX:TieredStopAtLevel=4
)

# JDK 25+: compact object headers
if [[ "${JAVA_MAJOR:-0}" -ge 25 ]]; then
  JVM_FLAGS+=(-XX:+UseCompactObjectHeaders)
fi

mkdir -p "$ROOT/login/cache"
# limpa CDS de sessão anterior (login leve; bat faz o mesmo)
rm -f "$ROOT/login/cache/brproject_cds.jsa" "$ROOT/login/cache/brproject_cds.gc" 2>/dev/null || true

cd "$ROOT/login"

echo "=== Brproject LoginServer ==="
echo "Java: $JAVA_CMD (major=${JAVA_MAJOR:-?})"
echo "CWD:  $(pwd)"
echo

# shellcheck disable=SC2206
FLAGS=( $G1_RECLAIM_FLAGS )
exec "$JAVA_CMD" "${JVM_FLAGS[@]}" "${FLAGS[@]}" -cp "$BRPROJECT_CP" ext.mods.loginserver.LoginServer
