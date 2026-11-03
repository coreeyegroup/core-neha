# Shared paths for core-neha operations scripts (sourced, not executed).
CN_ROOT="${CN_ROOT:-$HOME/core-neha}"
CN_REPO="$CN_ROOT/repo"
CN_CONFIG_DIR="$CN_ROOT/config"
CN_ENV="$CN_CONFIG_DIR/core-neha.env"
CN_SECRETS="$CN_ROOT/secrets"
CN_BACKUPS="$CN_ROOT/backups"
CN_SECRET_NAMES="pg_superuser_password core_neha_owner_password core_neha_app_password keycloak_db_password keycloak_bootstrap_admin_password oidc_staff_client_secret oidc_provisioner_client_secret"

cn_load_env() {
  [ -f "$CN_ENV" ] || { echo "core-neha: missing $CN_ENV (run ops/init-config.sh)" >&2; exit 1; }
  set -a; . "$CN_ENV"; set +a
}
cn_log() { printf '%s %s\n' "$(date -u +%FT%TZ)" "$*"; }
