#!/usr/bin/env bash
# Read-only checks before starting the stack.
set -uo pipefail
. "$(dirname "$(readlink -f "$0")")/lib.sh"
cn_load_env
ok=1
check() { if eval "$2"; then printf '  ok    %s\n' "$1"; else printf '  FAIL  %s\n' "$1"; ok=0; fi; }
echo "== core-neha preflight"
check "docker available"                   "docker info >/dev/null 2>&1"
check "compose available"                  "docker compose version >/dev/null 2>&1"
check "config valid"                       "$CN_REPO/ops/init-config.sh >/dev/null"
check "secrets dir mode 700"               "[ \"\$(stat -c %a '$CN_SECRETS')\" = 700 ]"
for n in $CN_SECRET_NAMES; do check "secret $n present, mode 600" "[ -s '$CN_SECRETS/$n' ] && [ \"\$(stat -c %a '$CN_SECRETS/$n')\" = 600 ]"; done
check "port $CORE_NEHA_HTTP_PORT free or ours"     "! ss -tln | grep -q ':$CORE_NEHA_HTTP_PORT ' || docker ps --format '{{.Ports}}' | grep -q ':$CORE_NEHA_HTTP_PORT->'"
check "port $CORE_NEHA_KC_ADMIN_PORT free or ours" "! ss -tln | grep -q ':$CORE_NEHA_KC_ADMIN_PORT ' || docker ps --format '{{.Ports}}' | grep -q ':$CORE_NEHA_KC_ADMIN_PORT->'"
check "subnets 172.29.84-85.0/24 unused by host" "! ip -4 route | grep -Eq '172\.29\.8[45]\.' || docker network ls --format '{{.Name}}' | grep -q '^core-neha_'"
check "compose file renders"               "$CN_REPO/ops/cn config -q"
[ "$ok" = 1 ] && echo "== preflight passed" || { echo "== preflight FAILED"; exit 1; }
