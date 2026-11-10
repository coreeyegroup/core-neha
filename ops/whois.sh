#!/usr/bin/env bash
# Who is a login id, and what can it do? Read only; prints no passwords or secrets.
#   ops/whois.sh e10001
# Shows the person, their postings (position, centre, department), their role assignments
# (role, scope, dates, status) and how many permissions each published role gives.
set -uo pipefail
. "$(dirname "$(readlink -f "$0")")/lib.sh"
cn_load_env
login="$(printf '%s' "${1:?usage: ops/whois.sh <login id, e.g. e10001>}" | tr 'A-Z' 'a-z')"
case "$login" in *[!a-z0-9._@-]*) echo "refused: unexpected characters in the login id" >&2; exit 1 ;; esac
# SQL goes in on standard input so that psql substitutes :'login' as a quoted literal (it does not with -c).
q() { printf '%s;\n' "$1" | "$CN_REPO/ops/cn" exec -T postgres psql -U postgres -d core_neha -At -F ' | ' -v login="$login" 2>&1; }

echo "== account (login | employee no | name | organisation | account status | MFA required)"
q "select u.username, p.employee_no, p.given_name || ' ' || p.family_name, t.name, u.status, u.mfa_required
   from iam.user_account u join workforce.person p on p.id = u.person_id join org.tenant t on t.id = u.tenant_id
   where lower(u.username) = :'login'"
echo "== postings (position | category | centre | department | from | to)"
q "select pos.title, pos.category, c.name, coalesce(d.local_name, ld.name, 'centre management'), po.valid_from, coalesce(po.valid_to::text, 'open')
   from iam.user_account u join workforce.posting po on po.person_id = u.person_id
   join workforce.establishment e on e.id = po.establishment_id join workforce.position_definition pos on pos.id = e.position_id
   join org.centre c on c.id = e.centre_id
   left join org.centre_department d on d.id = e.centre_department_id left join org.department_definition ld on ld.id = d.department_id
   where lower(u.username) = :'login'"
echo "== assignments (kind | role | scope | status | from | to | via bootstrap)"
q "select a.kind, coalesce(r.code, 'any role (authority)'), a.scope_type, a.status, a.valid_from, coalesce(a.valid_to::text, 'open'), a.via_bootstrap
   from iam.user_account u join iam.role_assignment a on a.user_id = u.id left join iam.role r on r.id = a.role_id
   where lower(u.username) = :'login' order by a.kind, r.code"
echo "== permissions from active roles (role | permissions in its published version | of all permissions)"
q "select r.code, count(g.permission_code), (select count(*) from iam.permission)
   from iam.user_account u join iam.role_assignment a on a.user_id = u.id and a.kind = 'ACCESS' and a.status = 'ACTIVE'
   join iam.role r on r.id = a.role_id join iam.role_version v on v.role_id = r.id and v.status = 'PUBLISHED'
   join iam.role_version_grant g on g.role_version_id = v.id
   where lower(u.username) = :'login' group by r.code order by r.code"
