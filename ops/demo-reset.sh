#!/usr/bin/env bash
# Empties the DEMONSTRATION system and loads it again (20 centres + Vijaya Eye Clinic and its full staff).
# Use after a change to how demonstration data is built (for example employee-number logins).
# Refuses outside CORE_NEHA_ENVIRONMENT=demo, asks you to type RESET DEMO, backs up first.
# Removes all data in both databases (core_neha and keycloak), including every login.
set -euo pipefail
. "$(dirname "$(readlink -f "$0")")/lib.sh"
cn_load_env
[ "$CORE_NEHA_ENVIRONMENT" = demo ] || { echo "refused: CORE_NEHA_ENVIRONMENT is '$CORE_NEHA_ENVIRONMENT', not demo" >&2; exit 1; }
cn="$CN_REPO/ops/cn"
echo "This deletes ALL data and logins of the demonstration system and loads fresh synthetic data."
printf 'Type RESET DEMO to continue: '
read -r answer
[ "$answer" = "RESET DEMO" ] || { echo "cancelled"; exit 1; }

"$CN_REPO/ops/backup.sh"
cn_log "stopping application containers"
"$cn" stop gateway backend keycloak
"$cn" exec -T postgres psql -v ON_ERROR_STOP=1 -U postgres -d postgres <<'SQL'
DROP DATABASE IF EXISTS core_neha WITH (FORCE);
DROP DATABASE IF EXISTS keycloak WITH (FORCE);
CREATE DATABASE core_neha OWNER core_neha_owner ENCODING 'UTF8' TEMPLATE template0;
CREATE DATABASE keycloak  OWNER keycloak        ENCODING 'UTF8' TEMPLATE template0;
REVOKE ALL ON DATABASE core_neha FROM PUBLIC;
REVOKE ALL ON DATABASE keycloak  FROM PUBLIC;
GRANT CONNECT ON DATABASE core_neha TO core_neha_app;
SQL
"$cn" exec -T postgres psql -v ON_ERROR_STOP=1 -U postgres -d core_neha <<'SQL'
REVOKE ALL ON SCHEMA public FROM PUBLIC;
ALTER SCHEMA public OWNER TO core_neha_owner;
SQL
cn_log "databases recreated; starting (Keycloak imports the realm, the backend runs the migrations)"
"$cn" up -d
for _ in $(seq 1 60); do
  state="$("$cn" ps --format '{{.Service}} {{.Health}}' | awk '$1=="backend"{print $2}')"
  [ "$state" = healthy ] && break
  sleep 5
done
[ "$state" = healthy ] || { echo "backend did not become healthy; see: ops/cn logs --since 10m backend" >&2; exit 1; }
"$cn" restart gateway
"$CN_REPO/ops/demo-seed.sh"
"$CN_REPO/ops/clinic-seed.sh"
"$CN_REPO/ops/clinic-staff.sh"
"$CN_REPO/ops/clinic-supply.sh"
cn_log "demonstration system reloaded; new one-time passwords are in $CN_SECRETS/onboarding/"
