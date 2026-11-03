#!/usr/bin/env bash
# Restores a backup into a throwaway PostgreSQL container on an isolated,
# internal-only network and checks the result. Never touches the live database.
#   ops/restore-test.sh                (latest backup)
#   ops/restore-test.sh <backup-dir>
set -euo pipefail
. "$(dirname "$(readlink -f "$0")")/lib.sh"
dir="${1:-$(ls -1d "$CN_BACKUPS"/20*Z 2>/dev/null | tail -1)}"
[ -n "$dir" ] && [ -d "$dir" ] || { echo "no backup found" >&2; exit 1; }
( cd "$dir" && sha256sum --quiet -c SHA256SUMS ) && cn_log "checksums OK for $dir"
name="core-neha-restore-test-$$"
cleanup() { docker rm -f "$name" >/dev/null 2>&1 || true; docker network rm "$name-net" >/dev/null 2>&1 || true; }
trap cleanup EXIT
docker network create --internal "$name-net" >/dev/null
docker run -d --name "$name" --network "$name-net" --memory 768m \
  -e POSTGRES_PASSWORD="$(python3 -c 'import secrets; print(secrets.token_urlsafe(24))')" \
  postgres:18.6-alpine3.24 >/dev/null
for _ in $(seq 1 60); do docker exec "$name" pg_isready -U postgres -q 2>/dev/null && break; sleep 1; done
grep -Ev '^(CREATE|ALTER) ROLE postgres( |;)' "$dir/roles.sql" | docker exec -i "$name" psql -U postgres -q -v ON_ERROR_STOP=1 >/dev/null
for pair in core_neha:core_neha_owner keycloak:keycloak; do
  db="${pair%%:*}"; owner="${pair##*:}"
  docker exec "$name" createdb -U postgres -O "$owner" "$db"
  docker exec -i "$name" pg_restore -U postgres --exit-on-error --dbname="$db" < "$dir/$db.dump"
  tables="$(docker exec "$name" psql -U postgres -d "$db" -At -c "select count(*) from information_schema.tables where table_schema not in ('pg_catalog','information_schema')")"
  cn_log "restored $db: $tables tables"
done
realms="$(docker exec "$name" psql -U postgres -d keycloak -At -c "select string_agg(name, ',' order by name) from realm")"
cn_log "keycloak realms in restored copy: $realms"
case ",$realms," in *,core-neha,*) cn_log "RESTORE TEST PASSED";; *) echo "RESTORE TEST FAILED: realm core-neha missing" >&2; exit 1;; esac
