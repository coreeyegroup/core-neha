#!/bin/bash
# Reads Compose secrets into the Keycloak process environment and starts the
# optimised server. --import-realm creates the core-neha realm on first start
# only; an existing realm is never overwritten.
set -euo pipefail
read_secret() {
  local v=""
  IFS= read -r v < "/run/secrets/$1" || true
  [[ -n "$v" ]] || { echo "core-neha: secret $1 is missing or empty" >&2; exit 1; }
  printf '%s' "$v"
}
KC_DB_PASSWORD="$(read_secret keycloak_db_password)"
KC_BOOTSTRAP_ADMIN_PASSWORD="$(read_secret keycloak_bootstrap_admin_password)"
CORE_NEHA_OIDC_STAFF_CLIENT_SECRET="$(read_secret oidc_staff_client_secret)"
CORE_NEHA_OIDC_PROVISIONER_CLIENT_SECRET="$(read_secret oidc_provisioner_client_secret)"
export KC_DB_PASSWORD KC_BOOTSTRAP_ADMIN_PASSWORD CORE_NEHA_OIDC_STAFF_CLIENT_SECRET CORE_NEHA_OIDC_PROVISIONER_CLIENT_SECRET
exec /opt/keycloak/bin/kc.sh start --optimized --import-realm
