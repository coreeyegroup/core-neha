#!/usr/bin/env bash
# Loads the synthetic demonstration tenant (20 centres) once. Only when
# CORE_NEHA_ENVIRONMENT=demo. One-time login passwords are written to
# ~/core-neha/secrets/onboarding/ (owner-only); nothing is printed.
set -euo pipefail
. "$(dirname "$(readlink -f "$0")")/lib.sh"
cn_load_env
[ "$CORE_NEHA_ENVIRONMENT" = demo ] || { echo "refused: CORE_NEHA_ENVIRONMENT is '$CORE_NEHA_ENVIRONMENT', not demo" >&2; exit 1; }
out="$CN_SECRETS/onboarding"; mkdir -p "$out"; chmod 700 "$out"
"$CN_REPO/ops/cn" run --rm --no-deps \
  -e CORE_NEHA_COMMAND=demo-seed -e SPRING_MAIN_WEB_APPLICATION_TYPE=none -e CORE_NEHA_OUTPUT_DIR=/out \
  -v "$out:/out" backend
echo "credentials file(s):"; ls -1 "$out" | sed 's/^/  /'
