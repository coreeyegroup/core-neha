#!/bin/sh
# Reads Compose secrets (as root, before the image drops to the postgres user)
# and hands them to the official entrypoint and init scripts as environment
# variables of the server process only. Nothing is written to disk or logged.
set -eu
read_secret() {
  v=""
  IFS= read -r v < "/run/secrets/$1" || true
  [ -n "$v" ] || { echo "core-neha: secret $1 is missing or empty" >&2; exit 1; }
  printf '%s' "$v"
}
POSTGRES_PASSWORD="$(read_secret pg_superuser_password)"
CN_OWNER_PASSWORD="$(read_secret core_neha_owner_password)"
CN_APP_PASSWORD="$(read_secret core_neha_app_password)"
KC_DB_PASSWORD="$(read_secret keycloak_db_password)"
export POSTGRES_PASSWORD CN_OWNER_PASSWORD CN_APP_PASSWORD KC_DB_PASSWORD
exec docker-entrypoint.sh "$@"
