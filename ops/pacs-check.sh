#!/usr/bin/env bash
# Image archive and viewer diagnosis. Read only; prints no secrets and no patient names.
# Checks: the Orthanc container, its plugins, the viewer files, the stored studies, the orders linked
# to studies, the gateway's answers for /api/pacs/ and recent errors.
set -uo pipefail
. "$(dirname "$(readlink -f "$0")")/lib.sh"
cn_load_env
cn="$CN_REPO/ops/cn"
gw() { "$cn" exec -T gateway wget -q -S -O - "$@" 2>&1; }

echo "== version"; git -C "$CN_REPO" log --oneline -1
echo "== containers"; "$cn" ps --format 'table {{.Service}}\t{{.Status}}' 2>&1
echo "== orthanc log (last 15 lines)"; "$cn" logs --no-color --tail 15 orthanc 2>&1 | cut -c1-220

echo "== orthanc seen from the gateway"
echo "-- system:"; gw http://orthanc:8042/system | grep -E '"(Version|Name|DicomAet|DicomPort)"' | head -5
echo "-- plugins:"; gw http://orthanc:8042/plugins | tr -d '\n' | head -c 300; echo
echo "-- statistics:"; gw http://orthanc:8042/statistics | grep -E '"Count(Studies|Instances)"'
echo "-- OHIF files (/ohif/):"; gw http://orthanc:8042/ohif/ | grep -oE 'HTTP/1.1 [0-9]+ [A-Za-z ]+|<title>[^<]*' | head -2
echo "-- OHIF settings:"; gw http://orthanc:8042/ohif/app-config.js | grep -oE '(routerBasename|qidoRoot|wadoRoot|wadoUriRoot|name)[^,]{0,90}' | head -6
echo "-- DICOMweb studies (accession numbers only):"
gw "http://orthanc:8042/dicom-web/studies?limit=20&includefield=00080050" | grep -o '"00080050"[^]]*]' | grep -o 'VEC[A-Z0-9]*' | sort -u | head -20

echo "== imaging orders in the database"
"$cn" exec -T postgres psql -U postgres -d core_neha -At -F ' | ' -c "
  select o.accession_no, ip.code, o.status, case when o.study_instance_uid is null then 'no study' else 'study linked' end, coalesce(o.image_count, 0)
  from diagnostics.diagnostic_order o join diagnostics.imaging_procedure ip on ip.id = o.procedure_id order by o.ordered_at" 2>&1

echo "== the gateway's answers for /api/pacs/ (last 25, study numbers shortened)"
"$cn" logs --no-color --since 2h gateway 2>&1 | grep '"path":"[^"]*/api/pacs/' | tail -25 \
  | sed -E 's/[0-9]+(\.[0-9]+){4,}/{uid}/g' \
  | sed -E 's/.*"path":"([^"]*)","status":([0-9]+).*"upstream_status":"([^"]*)".*/  \2 (archive \3) \1/' | cut -c1-200
echo "== backend errors about imaging (last 2 hours)"
"$cn" logs --no-color --since 2h backend 2>&1 | grep -iE 'imaging|pacs|orthanc|dicom' | grep -iE 'error|warn|exception' | tail -8 | cut -c1-300
echo "== done"
