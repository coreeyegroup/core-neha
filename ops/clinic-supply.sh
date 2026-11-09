#!/usr/bin/env bash
# Adds the pharmacy, optical shop, optical lab and stores of Vijaya Eye Clinic: stores, the item
# master (eye medicines, frames, lenses, lens blanks), opening stock from suppliers, and a working day
# (prescriptions, dispensing, spectacle orders through the lab). Run after ops/clinic-staff.sh; once.
# Demo only. Nothing secret is written or printed.
set -euo pipefail
. "$(dirname "$(readlink -f "$0")")/lib.sh"
cn_load_env
[ "$CORE_NEHA_ENVIRONMENT" = demo ] || { echo "refused: CORE_NEHA_ENVIRONMENT is '$CORE_NEHA_ENVIRONMENT', not demo" >&2; exit 1; }
"$CN_REPO/ops/cn" run --rm --no-deps \
  -e CORE_NEHA_COMMAND=clinic-supply -e SPRING_MAIN_WEB_APPLICATION_TYPE=none backend
