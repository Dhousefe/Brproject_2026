#!/usr/bin/env bash
# macOS Finder launcher: starts BrProject launcher/interface silently and closes Terminal.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"
mkdir -p "$ROOT/game/log"
nohup "$ROOT/StartBrproject.sh" > "$ROOT/game/log/launcher.log" 2>&1 &
disown
osascript -e 'tell application "Terminal" to close (every window whose name contains ".command")' 2>/dev/null || true
exit 0
