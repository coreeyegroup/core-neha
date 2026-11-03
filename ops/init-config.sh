#!/usr/bin/env bash
# Creates the live config (from the example) and checks it. Never overwrites.
set -euo pipefail
. "$(dirname "$(readlink -f "$0")")/lib.sh"
umask 077
mkdir -p "$CN_CONFIG_DIR"
if [ -f "$CN_ENV" ]; then cn_log "kept existing $CN_ENV"; else cp "$CN_REPO/deploy/config/core-neha.env.example" "$CN_ENV"; chmod 600 "$CN_ENV"; cn_log "created $CN_ENV"; fi
cn_load_env
fail=0
case "$CORE_NEHA_PUBLIC_BASE_URL" in https://*) ;; *) echo "CORE_NEHA_PUBLIC_BASE_URL must start with https://"; fail=1;; esac
case "$CORE_NEHA_PUBLIC_BASE_URL" in *"$CORE_NEHA_BASE_PATH") ;; *) echo "CORE_NEHA_PUBLIC_BASE_URL must end with CORE_NEHA_BASE_PATH"; fail=1;; esac
case "$CORE_NEHA_BASE_PATH" in /*[!/]) ;; *) echo "CORE_NEHA_BASE_PATH must start with / and not end with /"; fail=1;; esac
case "$CORE_NEHA_PUBLIC_BASE_URL" in *[0-9].[0-9]*.[0-9]*.[0-9]*) echo "use a hostname, not an IP address (ADR-0004)"; fail=1;; esac
[ "$fail" = 0 ] && cn_log "config OK: $CORE_NEHA_PUBLIC_BASE_URL (ports 127.0.0.1:$CORE_NEHA_HTTP_PORT, admin 127.0.0.1:$CORE_NEHA_KC_ADMIN_PORT)"
exit "$fail"
