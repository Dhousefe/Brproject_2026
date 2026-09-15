#!/usr/bin/env bash
# Classpath absoluto e ordenado (macOS/Linux usa ':').
# Uso: source cache/brproject-classpath.inc.sh [DIR_LIBS]
# Exporta: BRPROJECT_CP

_LIBS="${1:-}"
if [[ -z "$_LIBS" ]]; then
  _LIBS="$(cd "$(dirname "${BASH_SOURCE[0]}")/../libs" && pwd)"
else
  _LIBS="$(cd "$_LIBS" && pwd)"
fi

if [[ ! -f "$_LIBS/server.jar" ]]; then
  echo "ERRO: server.jar não encontrado em $_LIBS" >&2
  echo "Rode: ./gradlew :app-dist:jar  (ou copie o fat jar para libs/server.jar)" >&2
  return 1 2>/dev/null || exit 1
fi

_CP="$_LIBS/server.jar"
# ordenação estável para AppCDS
while IFS= read -r j; do
  base="$(basename "$j")"
  case "$base" in
    server.jar|*.encrypted|kotlin-stdlib-2.0.0.jar|kotlin-reflect-2.0.0.jar|kotlinx-coroutines-core-jvm-1.8.1.jar)
      continue
      ;;
  esac
  _CP="$_CP:$j"
done < <(ls "$_LIBS"/*.jar 2>/dev/null | sort)

export BRPROJECT_CP="$_CP"
unset _LIBS _CP
