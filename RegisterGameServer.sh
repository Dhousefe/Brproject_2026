#!/usr/bin/env bash
# Registro de GameServer no Login (hexid) — macOS / Linux
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

# shellcheck source=cache/brproject-java.inc.sh
source "$ROOT/cache/brproject-java.inc.sh"
# shellcheck source=cache/brproject-classpath.inc.sh
source "$ROOT/cache/brproject-classpath.inc.sh" "$ROOT/libs"

CFG="$ROOT/login/config/console.cfg"
if [[ ! -f "$CFG" ]]; then
  # fallback se console.cfg não existir
  exec "$JAVA_CMD" -cp "$BRPROJECT_CP" ext.mods.gsregistering.GameServerRegister
fi

exec "$JAVA_CMD" -Djava.util.logging.config.file="$CFG" -cp "$BRPROJECT_CP" ext.mods.gsregistering.GameServerRegister
