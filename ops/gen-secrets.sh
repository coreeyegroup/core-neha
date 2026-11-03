#!/usr/bin/env bash
# Generates missing secrets into ~/core-neha/secrets (dir 700, files 600).
# Existing secrets are never replaced. Values are never printed.
set -euo pipefail
. "$(dirname "$(readlink -f "$0")")/lib.sh"
umask 077
mkdir -p "$CN_SECRETS"; chmod 700 "$CN_SECRETS"
for name in $CN_SECRET_NAMES; do
  f="$CN_SECRETS/$name"
  if [ -s "$f" ]; then
    cn_log "kept      $name"
  else
    python3 -c 'import secrets, sys; sys.stdout.write(secrets.token_urlsafe(36))' > "$f"
    chmod 600 "$f"
    cn_log "generated $name"
  fi
done
[ "$(id -u)" = 1000 ] || cn_log "WARNING: uid $(id -u) is not 1000; the Keycloak container (uid 1000) may not read the secret files"
