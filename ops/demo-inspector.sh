#!/usr/bin/env bash
# Creates ONE developer login for the demonstration organisation: the role DEMO_INSPECTOR with
# every permission, given at whole-organisation scope (drafted by one administrator, approved by
# the other, audited). Licence, scope and two-person rules still apply. Refused outside demo.
# Run after ops/clinic-seed.sh; safe to run again (tops the role up with new permissions).
# One-time login passwords go to ~/core-neha/secrets/onboarding/ (owner-only); nothing is printed.
set -euo pipefail
. "$(dirname "$(readlink -f "$0")")/lib.sh"
cn_load_env
[ "$CORE_NEHA_ENVIRONMENT" = demo ] || { echo "refused: CORE_NEHA_ENVIRONMENT is '$CORE_NEHA_ENVIRONMENT', not demo" >&2; exit 1; }
out="$CN_SECRETS/onboarding"; mkdir -p "$out"; chmod 700 "$out"
"$CN_REPO/ops/cn" run --rm --no-deps \
  -e CORE_NEHA_COMMAND=demo-inspector -e SPRING_MAIN_WEB_APPLICATION_TYPE=none -e CORE_NEHA_OUTPUT_DIR=/out \
  -v "$out:/out" backend
echo "credentials file(s):"; ls -1 "$out" | sed 's/^/  /'
