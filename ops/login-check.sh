#!/usr/bin/env bash
# Sign-in diagnosis. Read-only. Prints no passwords, secrets or file contents.
# Checks each step: the containers, the sign-in start, the Keycloak login page, the accounts
# in both databases, the newest credentials files, and recent errors in the logs.
set -uo pipefail
. "$(dirname "$(readlink -f "$0")")/lib.sh"
cn_load_env
cn="$CN_REPO/ops/cn"
U="$CORE_NEHA_PUBLIC_BASE_URL"
jar="$(mktemp)"; trap 'rm -f "$jar"' EXIT
sql() { "$cn" exec -T postgres psql -U postgres -At -F ' | ' -d "$1" -c "$2" 2>&1; }

echo "== version"; git -C "$CN_REPO" log --oneline -1
echo "== containers"; "$cn" ps --format 'table {{.Service}}\t{{.Status}}'

echo "== sign-in through the public address ($U)"
start="$(curl -s -o /dev/null -c "$jar" -w '%{http_code} %{redirect_url}' --max-time 15 "$U/api/oauth2/authorization/keycloak")"
echo "  start sign-in      -> ${start%%\?*}"
loc="${start#* }"
if [ -n "$loc" ]; then
  page="$(curl -s -b "$jar" -c "$jar" --max-time 15 -w '\n%{http_code}' "$loc")"
  status="${page##*$'\n'}"
  if grep -q 'name="username"' <<<"$page"; then form="login form shown"; else form="NO login form"; fi
  echo "  Keycloak login page -> $status, $form"
  grep -o '<title>[^<]*' <<<"$page" | head -1 | sed 's/<title>/  page title: /'
  grep -oE 'kc-feedback-text[^>]*>[^<]+|alert-error[^<]*<[^<]*' <<<"$page" | head -2 | sed 's/^/  message: /'
fi
echo "  staff app page     -> $(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "$U/staff/")"
echo "  website page       -> $(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "$U/")"

echo "== application accounts (database core_neha)"
sql core_neha "select code, name from org.tenant order by created_at"
sql core_neha "select 'accounts: ' || count(*) || ', active: ' || count(*) filter (where status='ACTIVE') || ', linked to a login: ' || (select count(*) from iam.login_identity) from iam.user_account"
sql core_neha "select u.username, u.status, coalesce(p.employee_no,''), p.given_name || ' ' || p.family_name, case when li.subject is null then 'NOT LINKED' else 'linked' end from iam.user_account u join workforce.person p on p.id = u.person_id left join iam.login_identity li on li.user_id = u.id order by u.username limit 8"

echo "== sign-in accounts (database keycloak, realm core-neha)"
sql keycloak "select 'users: ' || count(*) || ', enabled: ' || count(*) filter (where ue.enabled) from user_entity ue join realm r on r.id = ue.realm_id where r.name = 'core-neha' and ue.service_account_client_link is null"
sql keycloak "select ue.username, ue.enabled, coalesce(string_agg(ra.required_action, ','), '') from user_entity ue join realm r on r.id = ue.realm_id left join user_required_action ra on ra.user_id = ue.id where r.name = 'core-neha' and ue.service_account_client_link is null group by ue.username, ue.enabled order by ue.username limit 8"

echo "== newest credentials files (names only)"
ls -lt "$CN_SECRETS/onboarding" 2>/dev/null | sed -n '2,5p' | awk '{print "  " $6, $7, $8, $9}'

echo "== recent errors (last 60 minutes)"
"$cn" logs --since 60m --no-color backend 2>&1 | grep -E '"level":"(ERROR|WARN)"|ERROR|Exception' | tail -8 | cut -c1-300 | sed 's/^/  backend: /'
"$cn" logs --since 60m --no-color keycloak 2>&1 | grep -E 'LOGIN_ERROR|ERROR|"level":"WARN"' | tail -8 | cut -c1-300 | sed 's/^/  keycloak: /'
echo "== done"
