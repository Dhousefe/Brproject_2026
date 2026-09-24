#!/usr/bin/env bash
# Brproject — Game Server (sem dashboard) — macOS / Linux
# Equivalente a StartGame_SemDashboard.bat
#
# Memória padrão no Mac: 2g (sobrescreva com BRPROJECT_GAME_XMX=3g se quiser).

set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

# shellcheck source=cache/brproject-java.inc.sh
source "$ROOT/cache/brproject-java.inc.sh"
# shellcheck source=cache/brproject-g1-reclaim.inc.sh
source "$ROOT/cache/brproject-g1-reclaim.inc.sh"
# shellcheck source=cache/brproject-classpath.inc.sh
source "$ROOT/cache/brproject-classpath.inc.sh" "$ROOT/libs"

XMS="${BRPROJECT_GAME_XMS:-2g}"
XMX="${BRPROJECT_GAME_XMX:-2g}"

JVM_FLAGS=(
  -Xms"$XMS" -Xmx"$XMX"
  -XX:+UseG1GC
  -XX:MaxGCPauseMillis=200
  -XX:G1HeapRegionSize=16m
  -XX:+UseStringDeduplication
  -XX:+UseCompressedOops
  -XX:+TieredCompilation
  -XX:TieredStopAtLevel=4
)

if [[ "${JAVA_MAJOR:-0}" -ge 25 ]]; then
  JVM_FLAGS+=(-XX:+UseCompactObjectHeaders)
  JVM_FLAGS+=(-XX:+AutoCreateSharedArchive -XX:SharedArchiveFile=cache/brproject_cds.jsa -Xlog:cds=error)
fi

mkdir -p "$ROOT/game/cache"
cd "$ROOT/game"

# AppCDS check (só se usar snapshot no game/cache)
if [[ "${JAVA_MAJOR:-0}" -ge 25 ]]; then
  # shellcheck source=cache/brproject-cds-check.inc.sh
  source "$ROOT/cache/brproject-cds-check.inc.sh" "cache/brproject_cds.jsa" "$ROOT/libs/server.jar" "G1"
fi

echo "=== Lineage2 NewEra GameServer ==="
echo "Java: $JAVA_CMD (major=${JAVA_MAJOR:-?})"
echo "Heap: -Xms$XMS -Xmx$XMX"
echo "CWD:  $(pwd)"
echo

# shellcheck disable=SC2206
FLAGS=( $G1_RECLAIM_FLAGS )
exec "$JAVA_CMD" "${JVM_FLAGS[@]}" "${FLAGS[@]}" -cp "$BRPROJECT_CP" ext.mods.gameserver.GameServer
