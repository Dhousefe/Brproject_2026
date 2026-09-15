#!/bin/bash
set -euo pipefail
cd /l2Brproject/game

build_cp() {
  local libs="../libs"
  local cp="${libs}/server.jar"
  local j base
  for j in $(ls "${libs}"/*.jar 2>/dev/null | sort); do
    base=$(basename "$j")
    case "$base" in
      server.jar|*.encrypted|kotlin-stdlib-2.0.0.jar|kotlin-reflect-2.0.0.jar|kotlinx-coroutines-core-jvm-1.8.1.jar) ;;
      *) cp="${cp}:$j" ;;
    esac
  done
  echo "$cp"
}

JAVA_OPTS="${JAVA_OPTS:--Xms1g -Xmx2g -XX:+UseG1GC}"
# Phase 6: no license args
exec java ${JAVA_OPTS} -cp "$(build_cp)" ext.mods.gameserver.GameServer
