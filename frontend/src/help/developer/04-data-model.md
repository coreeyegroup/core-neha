# Data model
> Schemas, tables and relationships, the conventions every table follows, and how migrations are written.

## Conventions

- **One schema per module**: `org`, `workforce`, `iam`, `audit`, `session`, `patient`, `eyecare`, `billing`.
- **Primary keys** `UUID DEFAULT uuidv7()` (time-ordered). Audit uses a bigint identity.
- **`tenant_id`** on every tenant-owned row, with the RLS policy `tenant_isolation`.
- **`version INT`** for optimistic locking: updates use `WHERE id = :id AND version = :expectedVersion` and bump it; zero rows updated becomes a 409 `…changed`.
- **Status as TEXT with CHECK**, never deleted rows: RETIRED, CANCELLED, REVOKED, DISABLED keep history.
- **Effective dating** with `valid_from` / `valid_to` for enablement, establishments, postings, assignments.
- **Business rules as CHECK constraints** where they can be: independent approval, surgery readiness, payment reference, paid ≤ net.
- Amounts are `NUMERIC(12,2)` rupees; business dates are IST dates (`BusinessTime.today()`).

## Organisation and people (V3)

```
org.tenant ─┬─ org.legal_entity
            ├─ org.hierarchy_level ── org.org_node (parent_id, path '/a/b/c/')
            ├─ org.department_definition (library)
            └─ org.centre (node_id, legal_entity_id)
                   └─ org.centre_department (department_id, local_name, valid_from/to)

workforce.position_definition (library)
workforce.establishment (position, centre, centre_department, headcount, dates)
workforce.person ── workforce.posting (establishment, fte, dates)
```

## Access (V3)

```
iam.user_account (person_id unique, status, access_version)
iam.login_identity (Keycloak subject → user; no RLS)
iam.permission (catalogue = Permission enum)
iam.role ── iam.role_version (DRAFT | PENDING_APPROVAL | PUBLISHED | SUPERSEDED | REJECTED)
               └─ iam.role_version_grant (permission_code)
iam.role_assignment (kind ACCESS|AUTHORITY, user, role, scope_type, scope_id, posting, dates,
                     status, requested_by, decided_by, via_bootstrap)
iam.responsibility_type ── iam.responsibility_assignment (person, scope, dates)
audit.audit_event (append-only for the runtime role)
session.spring_session, session.spring_session_attributes (V2)
```

## Patients, eye care, billing (V4)

```
patient.uhid_counter (per tenant: prefix 'NH', last_value)
patient.patient (uhid unique per tenant, demographics, registered_centre_id, abha unique)

eyecare.daily_counter (centre, date, kind 'TOKEN')
eyecare.appointment (patient, centre, date, slot, visit_type, source, consultant_person_id,
                     status BOOKED|CHECKED_IN|CANCELLED|NO_SHOW, visit_id)
eyecare.visit (patient, centre, appointment, visit_date, token_no, stage, timestamps per stage,
               consultant_person_id, no_charge_reason)
eyecare.optometry_record (visit_id PK: VA, refraction, IOP per eye)
eyecare.consultation (visit_id PK: findings, diagnosis + ICD-10, medicines, surgery advice)
eyecare.surgery_case (visit_id, procedure, eye, status, counselling, schedule,
                      pre-op checklist, operation)

billing.tariff_item (centre, code unique per centre, category, price, gst_rate, status)
billing.counter (centre, financial year) → bill_no CENTRE/2026-27/000001
billing.bill (patient, centre, visit_ref, surgery_ref, totals, discount fields, status OPEN|PAID|CANCELLED)
billing.bill_line (tariff_item, description, quantity, unit_price, gst, amount)
billing.payment (bill, business_date, mode, amount, reference, received_by)
billing.day_close (centre, business_date, system totals by mode, counted cash, variance,
                   SUBMITTED|APPROVED|REJECTED)
```

`visit_ref` and `surgery_ref` deliberately have no foreign key: billing does not depend on eye care.

## Writing a migration

1. Add `backend/src/main/resources/db/migration/V<n>__short_description.sql`. Never edit a migration that has run anywhere; add a new one.
2. Create new schemas with `GRANT USAGE` and `ALTER DEFAULT PRIVILEGES … TO core_neha_app` (see V1 and V4).
3. Every new tenant table: `tenant_id`, then add it to the RLS `DO $$ … $$` loop.
4. New permissions: `INSERT INTO iam.permission` **and** the `Permission` enum.
5. Migrations run as `core_neha_owner` when the backend starts. They are forward-only; rollback means restoring the backup taken before the update.
6. `MigrationPrivilegesTest`, `RowLevelSecurityTest` and `PermissionCatalogueTest` must stay green.
