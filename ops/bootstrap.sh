#!/usr/bin/env bash
# Creates a tenant and its two first administrators (ADR-0009). Reads
# ~/core-neha/config/bootstrap.env (see deploy/config/bootstrap.env.example).
# Refuses if the tenant exists. One-time passwords go to ~/core-neha/secrets/onboarding/.
set -euo pipefail
. "$(dirname "$(readlink -f "$0")")/lib.sh"
cfg="$CN_CONFIG_DIR/bootstrap.env"
[ -f "$cfg" ] || { echo "missing $cfg (copy deploy/config/bootstrap.env.example and fill it in)" >&2; exit 1; }
out="$CN_SECRETS/onboarding"; mkdir -p "$out"; chmod 700 "$out"
"$CN_REPO/ops/cn" run --rm --no-deps --env-file "$cfg" \
  -e CORE_NEHA_COMMAND=bootstrap -e SPRING_MAIN_WEB_APPLICATION_TYPE=none -e CORE_NEHA_OUTPUT_DIR=/out \
  -v "$out:/out" backend
echo "credentials file(s):"; ls -1 "$out" | sed 's/^/  /'
