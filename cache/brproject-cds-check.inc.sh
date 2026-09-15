#!/usr/bin/env bash
# Invalida AppCDS se server.jar for mais novo ou o modo GC mudou.
# Uso: source cache/brproject-cds-check.inc.sh CDS_PATH SERVER_JAR [G1|ZGC]

_CDS="${1:-}"
_JAR="${2:-}"
_MODE="${3:-G1}"
_META="$(dirname "$_CDS")/brproject_cds.gc"

if [[ "$_MODE" == "ZGC" ]]; then
  if [[ -f "$_CDS" ]]; then
    rm -f "$_CDS"
    echo "[AppCDS] Snapshot removido — heap CDS incompatível com ZGC."
  fi
  printf '%s\n' "ZGC" >"$_META"
  return 0 2>/dev/null || exit 0
fi

if [[ -f "$_CDS" && -f "$_META" ]]; then
  _OLD="$(tr -d '\r\n' <"$_META" 2>/dev/null || true)"
  if [[ -n "$_OLD" && "$_OLD" != "$_MODE" ]]; then
    rm -f "$_CDS"
    echo "[AppCDS] Snapshot removido — modo GC alterado de $_OLD para $_MODE."
  fi
fi
printf '%s\n' "$_MODE" >"$_META"

if [[ -f "$_CDS" && -f "$_JAR" ]]; then
  # se jar for mais novo que o snapshot, invalida
  if [[ "$_JAR" -nt "$_CDS" ]]; then
    rm -f "$_CDS"
    echo "[AppCDS] Snapshot removido — server.jar foi atualizado."
  fi
fi

unset _CDS _JAR _MODE _META _OLD
