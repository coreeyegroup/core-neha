#!/usr/bin/env bash
# Health, exposure and resource report. Read-only.
set -uo pipefail
. "$(dirname "$(readlink -f "$0")")/lib.sh"
cn_load_env
P="$CORE_NEHA_HTTP_PORT"; B="$CORE_NEHA_BASE_PATH"; A="$CORE_NEHA_KC_ADMIN_PORT"
code() { curl -s -o /dev/null -w '%{http_code}' --max-time 10 "$1"; }
expect() { local got; got="$(code "$2")"; if [ "$got" = "$3" ]; then printf '  ok    %-52s %s\n' "$1" "$got"; else printf '  FAIL  %-52s got %s, expected %s\n' "$1" "$got" "$3"; fi; }
echo "== containers"; "$CN_REPO/ops/cn" ps --format 'table {{.Service}}\t{{.Status}}\t{{.Ports}}'
echo "== published ports (must all be 127.0.0.1)"
docker ps --filter label=com.docker.compose.project=core-neha --format '{{.Names}} {{.Ports}}' | tr ',' '\n' | grep -- '->' | sed 's/^ *//' | while read -r line; do
  case "$line" in *0.0.0.0:*|*"[::]:"*|*":::"*) echo "  FAIL  public binding: $line";; *) echo "  ok    $line";; esac
done
echo "== routes through the gateway (127.0.0.1:$P)"
expect "gateway health"                         "http://127.0.0.1:$P/__gateway_health" 200
expect "$B redirects to $B/"                    "http://127.0.0.1:$P$B" 308
expect "public website page"                    "http://127.0.0.1:$P$B/" 200
expect "staff application page"                 "http://127.0.0.1:$P$B/staff/" 200
expect "account console closed"                 "http://127.0.0.1:$P$B/auth/realms/core-neha/account" 403
expect "API refuses requests without login"     "http://127.0.0.1:$P$B/api/v1/me" 401
expect "OIDC discovery, realm core-neha"        "http://127.0.0.1:$P$B/auth/realms/core-neha/.well-known/openid-configuration" 200
expect "admin console blocked publicly"         "http://127.0.0.1:$P$B/auth/admin/" 403
expect "master realm blocked publicly"          "http://127.0.0.1:$P$B/auth/realms/master/" 403
expect "admin console on localhost-only port"   "http://localhost:$A$B/auth/admin/" 302
issuer="$(curl -s --max-time 10 "http://127.0.0.1:$P$B/auth/realms/core-neha/.well-known/openid-configuration" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("issuer"))' 2>/dev/null)"
if [ "$issuer" = "$CORE_NEHA_PUBLIC_BASE_URL/auth/realms/core-neha" ]; then echo "  ok    issuer $issuer"; else echo "  FAIL  issuer '$issuer'"; fi
echo "== resources"; docker stats --no-stream --format 'table {{.Name}}\t{{.MemUsage}}\t{{.CPUPerc}}' $(docker ps -q --filter label=com.docker.compose.project=core-neha) 2>/dev/null
echo "== existing services (must stay active)"
for s in caddy apache2 postgresql@14-main corei-kafka corei-relay docker; do printf '  %-20s %s\n' "$s" "$(systemctl is-active "$s" 2>/dev/null)"; done
