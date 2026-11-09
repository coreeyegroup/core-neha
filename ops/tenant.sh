#!/usr/bin/env bash
# Software vendor's console for the hospital chains (tenants) on this installation.
# Each chain is completely separate: its own licence, organisation, staff, logins and patients.
# Licences can be changed only here, on the server; no web user can change a licence.
#
#   ops/tenant.sh list
#   ops/tenant.sh licence <code> --reason "..." [--no LIC-123] [--licensee "Legal name"] [--plan CLINIC|STANDARD|ENTERPRISE|DEMO]
#                                 [--from 2026-11-01] [--to 2027-10-31|open] [--max-centres 25|unlimited]
#                                 [--modules EYE_CARE,BILLING,INVENTORY,PHARMACY,OPTICAL,OPTICAL_LAB,LABORATORY,RADIOLOGY|ALL]
#   ops/tenant.sh suspend  <code> --reason "..."     (nobody in that chain can do anything until activated)
#   ops/tenant.sh activate <code> --reason "..."
#
# A new chain is created with ops/bootstrap.sh (its first two administrators); then set its licence here.
set -euo pipefail
. "$(dirname "$(readlink -f "$0")")/lib.sh"
cn_load_env

usage() { sed -n '2,13p' "$0" | sed 's/^# \{0,1\}//'; exit 2; }
[ $# -ge 1 ] || usage
cmd="$1"; shift
envs=()
case "$cmd" in
  list) command=tenant-list ;;
  licence|suspend|activate)
    [ $# -ge 1 ] || usage
    envs+=(-e "CORE_NEHA_TENANT_CODE=$1"); shift
    command="tenant-$cmd"
    while [ $# -gt 0 ]; do
      [ $# -ge 2 ] || usage
      case "$1" in
        --reason) envs+=(-e "CORE_NEHA_REASON=$2") ;;
        --no) envs+=(-e "CORE_NEHA_LICENCE_NO=$2") ;;
        --licensee) envs+=(-e "CORE_NEHA_LICENSEE=$2") ;;
        --plan) envs+=(-e "CORE_NEHA_LICENCE_PLAN=$2") ;;
        --from) envs+=(-e "CORE_NEHA_LICENCE_VALID_FROM=$2") ;;
        --to) envs+=(-e "CORE_NEHA_LICENCE_VALID_TO=$2") ;;
        --max-centres) envs+=(-e "CORE_NEHA_LICENCE_MAX_CENTRES=$2") ;;
        --modules) envs+=(-e "CORE_NEHA_LICENCE_MODULES=$2") ;;
        *) usage ;;
      esac
      shift 2
    done ;;
  *) usage ;;
esac

"$CN_REPO/ops/cn" run --rm --no-deps -e CORE_NEHA_COMMAND="$command" -e SPRING_MAIN_WEB_APPLICATION_TYPE=none \
  -e LOGGING_LEVEL_ROOT=WARN "${envs[@]}" backend
