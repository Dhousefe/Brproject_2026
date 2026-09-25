#!/bin/sh
set -eu

: "${DB_URL:?DB_URL is required}"
: "${DB_USER:?DB_USER is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"
: "${GAME_SERVER_HEXID:?GAME_SERVER_HEXID is required}"
: "${GAME_SERVER_HOST:?GAME_SERVER_HOST is required}"

cd /opt/brproject

java -cp "/opt/brproject/app.jar:/opt/brproject/libs/*" br.project.db.MigrateMain
