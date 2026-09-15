#!/bin/bash

# Define o comando Java
JAVA_CMD="java"

# --- Configuração das Variáveis ---
L2_EMAIL=${L2_EMAIL:-"brprojeto@l2jbrasil.com"}
PASSWORD=${PASSWORD:-"12345678"}
KEY=$(uuidgen | tr -d '-')

# --- Flags JVM Recomendadas do JvmOptimizer para JDK 25+ ---
# Detecta versão do Java
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | sed '/^1\./s///' | cut -d'.' -f1)

# Flags base para ambos os servidores
BASE_JVM_FLAGS="-XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:G1HeapRegionSize=16m -XX:+G1UseAdaptiveIHOP -XX:G1ReservePercent=20 -XX:InitiatingHeapOccupancyPercent=45 -XX:+UseStringDeduplication -XX:+UseCompressedOops -XX:+UseCompressedClassPointers -XX:+TieredCompilation -XX:TieredStopAtLevel=4 -XX:G1PeriodicGCInterval=30000 -XX:+G1PeriodicGCInvokesConcurrent -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=30"

# Flags específicas para JDK 25+
if [ "$JAVA_VERSION" -ge 25 ] 2>/dev/null; then
    BASE_JVM_FLAGS="$BASE_JVM_FLAGS -XX:+UseCompactObjectHeaders"
    # AppCDS: 1o boot cria cache/brproject_cds.jsa ao desligar; 2o boot carrega classes pre-compiladas
    mkdir -p cache
    BASE_JVM_FLAGS="$BASE_JVM_FLAGS -XX:+AutoCreateSharedArchive -XX:SharedArchiveFile=cache/brproject_cds.jsa"
    # Linux: Transparent Huge Pages
    if [[ "$OSTYPE" == "linux-gnu"* ]]; then
        BASE_JVM_FLAGS="$BASE_JVM_FLAGS -XX:+UseTransparentHugePages"
    fi
fi

# Flags específicas para LoginServer (mais leve, sem JFR)
LOGIN_JAVA_OPTS="${LOGIN_JAVA_OPTS:-$BASE_JVM_FLAGS}"

# Flags específicas para GameServer (pode incluir JFR se necessário)
# Para habilitar JFR no GameServer, adicione: -XX:+FlightRecorder -XX:StartFlightRecording=duration=60s,filename=./logs/recording.jfr
GAME_JAVA_OPTS="${GAME_JAVA_OPTS:-"-Xms1g -Xmx2g $BASE_JVM_FLAGS"}"

# Classpath ordenado (Kotlin 2.3.0-Beta2 + coroutines 1.9.0; evita SpillingKt / AppCDS mismatch)
build_runtime_classpath() {
  local libs="$1"
  local cp="$libs/server.jar"
  local j base
  for j in $(ls "$libs"/*.jar 2>/dev/null | sort); do
    base=$(basename "$j")
    case "$base" in
      server.jar|*.encrypted|kotlin-stdlib-2.0.0.jar|kotlin-reflect-2.0.0.jar|kotlinx-coroutines-core-jvm-1.8.1.jar) ;;
      *) cp="$cp:$j" ;;
    esac
  done
  echo "$cp"
}

echo "=== Iniciando LoginServer (MODO DEBUG) ==="
echo "Flags JVM: $LOGIN_JAVA_OPTS"
(
  cd login || exit
  # SEM REDIREÇÃO DE LOG - O erro Java vai aparecer no log do container
  $JAVA_CMD $LOGIN_JAVA_OPTS -Dext.mods.Config.dataPath=../game/data -cp "$(build_runtime_classpath ../libs)" ext.mods.loginserver.LoginServer
) &
LOGIN_PID=$!

echo "=== Iniciando GameServer (MODO DEBUG) ==="
echo "Flags JVM: $GAME_JAVA_OPTS"
(
  cd game || exit
  # SEM REDIREÇÃO DE LOG - O erro Java vai aparecer no log do container
  $JAVA_CMD $GAME_JAVA_OPTS -cp "$(build_runtime_classpath ../libs)" ext.mods.gameserver.GameServer
) &
GAME_PID=$!

echo "LoginServer PID: $LOGIN_PID"
echo "GameServer PID: $GAME_PID"

# --- Gerenciador de Processos ---
shutdown() {
  echo "Desligando... enviando SIGTERM para $LOGIN_PID e $GAME_PID"
  # Adicionamos '|| true' para ignorar o erro "No such process"
  kill -TERM "$LOGIN_PID" || true
  kill -TERM "$GAME_PID" || true
  wait "$LOGIN_PID"
  wait "$GAME_PID"
  echo "Servidores parados."
  exit 0
}

trap shutdown SIGTERM SIGINT

wait -n $LOGIN_PID $GAME_PID

echo "Um dos servidores (Login ou Game) parou inesperadamente. Desligando o container..."
shutdown