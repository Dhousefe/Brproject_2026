#!/usr/bin/env bash
# Sync brproject-data config examples into runtime tree (never overwrites live secrets).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DATA="${ROOT}/brproject-data"
GAME_CFG="${ROOT}/game/config"
LOGIN_CFG="${ROOT}/login/config"

echo "brproject-data VERSION=$(cat "${DATA}/VERSION" 2>/dev/null || echo unknown)"

mkdir -p "${GAME_CFG}" "${LOGIN_CFG}"

copy_example() {
  local src="$1"
  local dest="$2"
  if [[ -f "${src}" ]]; then
    cp -f "${src}" "${dest}"
    echo "  synced $(basename "${dest}")"
  fi
}

echo "Syncing config examples..."
if [[ -d "${DATA}/config-examples/game" ]]; then
  for f in "${DATA}/config-examples/game"/*; do
    [[ -f "$f" ]] || continue
    copy_example "$f" "${GAME_CFG}/$(basename "$f")"
  done
fi
if [[ -d "${DATA}/config-examples/login" ]]; then
  for f in "${DATA}/config-examples/login"/*; do
    [[ -f "$f" ]] || continue
    copy_example "$f" "${LOGIN_CFG}/$(basename "$f")"
  done
fi

# Seed live properties from examples only if missing
seed_if_missing() {
  local example="$1"
  local live="$2"
  if [[ -f "${example}" && ! -f "${live}" ]]; then
    cp "${example}" "${live}"
    echo "  seeded missing $(basename "${live}") from example"
  fi
}

seed_if_missing "${GAME_CFG}/server.properties.example" "${GAME_CFG}/server.properties"
seed_if_missing "${LOGIN_CFG}/loginserver.properties.example" "${LOGIN_CFG}/loginserver.properties"

echo "Done. Live secrets were not overwritten if already present."
