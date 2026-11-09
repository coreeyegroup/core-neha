#!/usr/bin/env bash
# Adds the laboratory and imaging of Vijaya Eye Clinic: the test and imaging catalogue (LOINC-coded),
# and a day of orders worked by the staff (samples, results, validation, imaging with synthetic DICOM
# images stored in the image archive, reports). Run after ops/clinic-staff.sh; once.
# Demo only. Nothing secret is written or printed.
set -euo pipefail
. "$(dirname "$(readlink -f "$0")")/lib.sh"
cn_load_env
[ "$CORE_NEHA_ENVIRONMENT" = demo ] || { echo "refused: CORE_NEHA_ENVIRONMENT is '$CORE_NEHA_ENVIRONMENT', not demo" >&2; exit 1; }
"$CN_REPO/ops/cn" run --rm --no-deps \
  -e CORE_NEHA_COMMAND=clinic-diagnostics -e SPRING_MAIN_WEB_APPLICATION_TYPE=none backend
