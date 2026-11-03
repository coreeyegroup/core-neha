#!/usr/bin/env bash
# Adds or removes the core-neha route in the existing Caddyfile, safely:
# backup -> edit only between markers -> caddy validate -> reload -> verify.
# Restores the backup automatically if validation or verification fails.
#   ops/caddy-route.sh add | remove | show
set -euo pipefail
. "$(dirname "$(readlink -f "$0")")/lib.sh"
cn_load_env
CF=/etc/caddy/Caddyfile
action="${1:-show}"
host="$(python3 -c 'import sys, urllib.parse; print(urllib.parse.urlparse(sys.argv[1]).hostname)' "$CORE_NEHA_PUBLIC_BASE_URL")"
pub() { curl -s -o /dev/null -w '%{http_code}' --max-time 15 "$1"; }

if [ "$action" = show ]; then grep -n -A6 '# CORE NEHA BEGIN' "$CF" || echo "no core-neha route in $CF"; exit 0; fi

tmp="$(mktemp)"; trap 'rm -f "$tmp"' EXIT
python3 - "$CF" "$tmp" "$action" "$host" "$CORE_NEHA_BASE_PATH" "$CORE_NEHA_HTTP_PORT" "$CN_REPO/deploy/caddy/core-neha.caddy" <<'PY'
import re, sys
src, dst, action, host, base, port, snippet_file = sys.argv[1:]
lines = open(src).read().splitlines(keepends=True)
begin = [i for i, l in enumerate(lines) if "# CORE NEHA BEGIN" in l]
end = [i for i, l in enumerate(lines) if "# CORE NEHA END" in l]
if action == "remove":
    if not begin: sys.exit("no core-neha route present; nothing to remove")
    out = lines[:begin[0]] + lines[end[0] + 1:]
elif action == "add":
    if begin: sys.exit("core-neha route already present; nothing to add")
    site = next((i for i, l in enumerate(lines)
                 if not l.lstrip().startswith("#") and re.search(r"(^|[\s,])" + re.escape(host) + r"(:443)?\s*(,[^{]*)?\{\s*$", l)), None)
    if site is None: sys.exit("site block for %s not found" % host)
    depth, catch_all, close = 0, None, None
    for i in range(site, len(lines)):
        s = lines[i].split("#", 1)[0]
        if i > site and depth == 1 and re.match(r"^\s*handle\s*\{\s*$", s): catch_all = i
        depth += s.count("{") - s.count("}")
        if depth == 0: close = i; break
    insert_at = catch_all if catch_all is not None else close
    snippet = open(snippet_file).read().replace("__BASE_PATH__", base).replace("__HTTP_PORT__", port)
    if not snippet.endswith("\n"): snippet += "\n"
    out = lines[:insert_at] + [snippet] + lines[insert_at:]
else:
    sys.exit("usage: add | remove | show")
open(dst, "w").write("".join(out))
PY

if [ "$action" = add ]; then
  curl -fsS --max-time 5 "http://127.0.0.1:$CORE_NEHA_HTTP_PORT/__gateway_health" >/dev/null \
    || { echo "gateway is not healthy on 127.0.0.1:$CORE_NEHA_HTTP_PORT; not adding the route" >&2; exit 1; }
fi
before_root="$(pub "https://$host/")"
bak="$CF.before-core-neha-$action.$(date +%Y%m%d%H%M%S)"
sudo cp -a "$CF" "$bak"; cn_log "backup: $bak"
echo "== change"; diff -u "$CF" "$tmp" || true
sudo install -m 644 -o root -g root "$tmp" "$CF"
restore() { echo "restoring $bak" >&2; sudo cp -a "$bak" "$CF"; sudo systemctl reload caddy; exit 1; }
sudo caddy validate --config "$CF" --adapter caddyfile >/dev/null 2>&1 || { echo "caddy validate failed" >&2; restore; }
sudo systemctl reload caddy
sleep 2
after_root="$(pub "https://$host/")"
[ "$after_root" = "$before_root" ] || { echo "https://$host/ changed from $before_root to $after_root" >&2; restore; }
cn_log "https://$host/ unchanged ($after_root)"
if [ "$action" = add ]; then
  got="$(pub "$CORE_NEHA_PUBLIC_BASE_URL/")"
  [ "$got" = 200 ] || { echo "$CORE_NEHA_PUBLIC_BASE_URL/ returned $got" >&2; restore; }
  cn_log "$CORE_NEHA_PUBLIC_BASE_URL/ -> $got"
  cn_log "OIDC issuer check: $(pub "$CORE_NEHA_PUBLIC_BASE_URL/auth/realms/core-neha/.well-known/openid-configuration")  admin blocked: $(pub "$CORE_NEHA_PUBLIC_BASE_URL/auth/admin/")"
fi
cn_log "caddy route $action complete"
