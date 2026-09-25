#!/bin/bash
set -euo pipefail
cd /l2Brproject/login

envsubst '${DB_URL} ${DB_USER} ${DB_PASSWORD} ${GAME_SERVER_HOST}' \
  < config/loginserver.properties.template \
  > config/loginserver.properties

envsubst '${GAME_SERVER_HEXID}' \
  < config/hexid.txt.template \
  > config/hexid.txt

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

JAVA_OPTS="${JAVA_OPTS:--Xms256m -Xmx512m -XX:+UseG1GC}"
exec java ${JAVA_OPTS} -Dext.mods.Config.dataPath=../game/data -cp "$(build_cp)" ext.mods.loginserver.LoginServer
