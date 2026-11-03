#!/usr/bin/env bash
# Logical backup of both databases plus role definitions (no passwords).
# Output: ~/core-neha/backups/<UTC timestamp>/ (dir 700, files 600), 14-day retention.
set -euo pipefail
. "$(dirname "$(readlink -f "$0")")/lib.sh"
umask 077
ts="$(date -u +%Y%m%dT%H%M%SZ)"; dir="$CN_BACKUPS/$ts"
mkdir -p "$dir"; chmod 700 "$CN_BACKUPS" "$dir"
cn="$CN_REPO/ops/cn"
for db in core_neha keycloak; do
  "$cn" exec -T postgres pg_dump -U postgres --format=custom --compress=6 --dbname="$db" > "$dir/$db.dump"
  cn_log "dumped $db ($(du -h "$dir/$db.dump" | cut -f1))"
done
"$cn" exec -T postgres pg_dumpall -U postgres --roles-only --no-role-passwords > "$dir/roles.sql"
"$cn" exec -T postgres psql -U postgres -At -c "select version()" > "$dir/server-version.txt"
( cd "$dir" && sha256sum ./*.dump roles.sql server-version.txt > SHA256SUMS )
chmod 600 "$dir"/*
find "$CN_BACKUPS" -mindepth 1 -maxdepth 1 -type d -name '20*Z' -mtime +14 -print -exec rm -rf {} + | sed 's/^/removed old backup: /'
cn_log "backup complete: $dir"
