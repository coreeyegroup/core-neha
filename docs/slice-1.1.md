# Slice 1.1 – Organisation and access foundation

Status: **implemented; acceptance on core-node pending** (see "Acceptance evidence").

## Feature IDs covered

| Area | IDs | State |
|---|---|---|
| Tenant, legal entities, configurable hierarchy | ADM-01, ADM-12 | Delivered |
| Department library, centre enablement, retirement with impact check | ADM-14, ADM-18 (departments), ADM-17 (preview, partial) | Delivered (retirement blocked while work is open) |
| Positions, establishments, people, dated multi-centre postings | HR-01, HR-11, HR-12, HR-13 | Delivered |
| Roles, role versions with independent approval, custom roles, cloning, impact preview | IAM-12, IAM-13, IAM-14, IAM-18 | Delivered |
| Scoped assignments, delegated assignment authority, independent approval, revocation | IAM-01, IAM-02 | Delivered |
| Responsibilities separate from access | IAM-15 | Delivered |
| Effective-access preview and explanation | IAM-21 | Delivered |
| Login, logout, MFA (TOTP), session timeouts, revocation | IAM-03, IAM-04 | Delivered (Keycloak + server sessions) |
| Audit of writes, approvals, denials and sensitive reads; search | AUD-01, AUD-02, AUD-07 | Delivered |
| First-administrator bootstrap; demonstration data (20 centres) | ADR-0009, ADR-0010 | Delivered |

## Not in this slice (stay on the roadmap)

Reporting lines and approval chains (HR-14), clinical privileges (HR-15), temporary cover (IAM-22), credential gate (IAM-23), centre-type templates as stored objects (ADM-13; the demo uses templates in code), capability readiness check (ADM-15), shared-service links (ADM-16), break-the-glass (IAM-06), segregation-of-duties rule engine beyond self-approval (IAM-07), periodic access reviews (IAM-11), retention disposal (AUD-08), department-level assignment scope, single sign-on with a corporate directory (IAM-10).

## Known limitations

- The Keycloak admin API client is exercised on the server, not in CI (tests replace the identity provider with a mock).
- Audit table is not yet partitioned by month.
- Temporary passwords are shown once to the administrator (no email delivery configured).
- User listing for centre-scoped administrators is filtered by current postings.
- Frontend bundle is a single 240 kB (gzip) chunk; route-level code splitting later.
- Automated accessibility scanning (axe) is not yet in CI.

## Acceptance evidence (to be recorded from core-node)

| Item | How it is shown |
|---|---|
| Login and logout | Browser: sign in with a demo account (password + TOTP), sign out returns to the signed-out page |
| Different centres, different departments and positions | `DemoSeedTest`; centre workspace in the browser |
| Multiple scoped assignments | `ravi.visiting` has two centre assignments (`DemoSeedTest`, user page) |
| Responsibility grants nothing | `AccessScenarioTest.responsibilityGrantsNoPermission`; effective-access page |
| Custom role drafting by an authorised administrator | `AccessScenarioTest.customRoleNeedsIndependentApproval`; Roles page |
| Self-approved expansion refused | `AccessScenarioTest` (roles and assignments); audit shows the DENIED event |
| Cross-centre and cross-tenant refusals | `AccessScenarioTest.crossCentreAccessIsRefused`, `crossTenantAccessIsRefused`; `RowLevelSecurityTest` |
| Disabling revokes access within 5 minutes | `AccessScenarioTest.disablingAnAccountEndsAccessImmediately`; browser check on the server |
| Audit identifies actor, action, object, scope, outcome | `AccessScenarioTest.auditIdentifies...`; Audit page |
| Migrations on an empty database | `MigrationPrivilegesTest` (and first start on core-node) |
| Backend and frontend tests pass | GitHub Actions run for the release commit |
| Restart and persistence | `ops/cn restart` then `ops/status.sh`; data still present |
| Backup restore in an isolated database | `ops/backup.sh && ops/restore-test.sh` |
