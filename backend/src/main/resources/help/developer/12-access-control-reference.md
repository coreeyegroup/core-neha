# Access control reference
> Every layer of access in one place: the checks on each request, who controls what (provider console and administrators), licence modules, scope, assignment authority, two-person rules, where it lives in the code, and what is still to be built.
<!-- audience: administrator -->

The live tables (every permission, role, position, menu and who holds what) are in the **[Access guide](/help/access)**, built from the database by `GET /v1/access-guide`. This chapter explains the rules behind them.

## The checks on every request

A person can open a screen or do an action only when every check says yes. A failed check refuses the action and writes a *denied* audit entry with the missing permission and scope.

1. **Signed in.** Employee number and password at the identity provider; a one-time code (TOTP) when the account's `mfa_required` is true (the default). The session ends after 30 minutes idle or 12 hours in total.
2. **Account active.** `iam.user_account.status = 'ACTIVE'`.
3. **Organisation active.** `org.tenant.status = 'ACTIVE'`; a suspended organisation loses all access at once.
4. **Module licensed.** `org.permission_licensed(tenant, permission)`: the permission's module (first word of the code) is in `org.tenant_module` and the licence is ACTIVE and within its dates. CORE is always licensed.
5. **Role assigned.** An ACCESS assignment that is ACTIVE (requested by one administrator, approved by another) and inside its valid-from and valid-to dates.
6. **Role published.** The role is ACTIVE and has a PUBLISHED version; only that version's grants count.
7. **Permission in the role.** The exact permission the action needs.
8. **Scope covers the record.** TENANT covers everything; ORG_NODE covers its subtree by hierarchy path; CENTRE covers one centre. Organisation-wide-only permissions (`tenant_only`) count only at TENANT scope.
9. **Business rules.** Two-person rules and the state of the record.

Under all of this, PostgreSQL row-level security on `app.tenant_id` keeps every organisation's rows apart.

Checks 2 to 7 are one SQL statement, `AccessEvaluator.GRANTS_SQL`; check 8 is `AccessEvaluator.covers`. Results are memoised for the web request. Services call `access.require(actor, permission, scope, action, objectType, objectId)` before any change, and `holds` or `centresWhere` to filter lists.

## Who controls what

| Control | Who | Where | Rule |
|---|---|---|---|
| Create an organisation and its first two administrators | Software provider | `ops/bootstrap.sh` | The two administrators get `SYSTEM_ADMINISTRATOR`; both are needed for every later grant |
| Licence: number, licensee, plan, dates, maximum centres, modules | Software provider | `ops/tenant.sh licence` | Reason required; audited as "platform operator (server console)" |
| Suspend or reactivate an organisation | Software provider | `ops/tenant.sh suspend` / `activate` | Reason required |
| List organisations and licences | Software provider | `ops/tenant.sh list` | Licence fields and centre counts only (`org.platform_tenants()`) |
| Demonstration data | Software provider | `ops/demo-seed.sh`, `demo-reset.sh`, `clinic-*.sh` | Refused for any organisation not marked as demo |
| Backups and diagnosis | Software provider | `backup.sh`, `restore-test.sh`, `status.sh`, `login-check.sh`, `pacs-check.sh` | Read only apart from backup; no secrets or patient names printed |
| Hierarchy, legal entities, department library | Administrator | Organisation menus | `org.structure.manage`, organisation-wide only |
| Centres and their departments | Administrator | Centres | `org.centre.create`, `org.centre.configure`; maximum centres from the licence |
| Position library | Administrator | Position library | `workforce.position.define`, organisation-wide only |
| Establishments and postings | HR or administrator | People and postings | `workforce.establish`, `workforce.person.manage` |
| Roles | Two administrators | Roles, Approvals | `iam.role.draft`, `iam.role.approve` |
| User accounts and MFA | User administrator | Users and access | `iam.user.manage`; turning MFA off needs organisation-wide scope and a reason |
| Assignments | Two administrators | Users and access, Approvals | `iam.assignment.request`, `.approve`, `.revoke`, plus assignment authority |
| Responsibilities | Administrator | Users and access | `iam.responsibility.manage`; never grant access |
| Price list, item master, test and imaging catalogues | Accounts manager, stores manager, pathologist, radiologist | Accounts, Inventory, Laboratory, Imaging | `billing.tariff.manage` per centre; the other three organisation-wide only |

The provider has no sign-in to an organisation's data. `SYSTEM_ADMINISTRATOR` holds only the 19 organisation, workforce, access and audit permissions (`Permission.administrative()`), so administrators build roles and give access but do not see patients, bills or clinical records. Seeing a centre's patients needs a clinical role such as the centre director's, given by two people.

## Licence modules

| Module | Permission prefix | Without it |
|---|---|---|
| CORE (always) | `org.` `workforce.` `iam.` `audit.` `patient.` `opd.` | Cannot be removed |
| EYE_CARE | `eye.` | Visits run as a queue only; no eye records |
| BILLING | `billing.` | No bills; pharmacy, optical and diagnostics cannot charge |
| INVENTORY | `inventory.` | No stock; pharmacy and optical cannot take stock |
| PHARMACY | `pharmacy.` | No prescriptions or dispensing |
| OPTICAL | `optical.` | No spectacle orders |
| OPTICAL_LAB | `opticallab.` | No manufacturing jobs |
| LABORATORY | `lab.` | No lab orders or results |
| RADIOLOGY | `radiology.` | No imaging; the image viewer refuses every study |

The mapping is `org.permission_module` (V8). A new permission prefix is CORE unless that function is changed in a migration.

## Scope and assignment authority

- A higher scope includes the lower ones, by the node's path.
- An assignment linked to a posting must cover the posting's centre.
- Assignments carry valid-from and valid-to dates; a locum or visiting consultant can get access for exact dates.
- **Assignment authority** is an AUTHORITY assignment: which roles (or all, when `role_id` is null) an administrator may hand out, and at which scope. A request outside the requester's authority is refused (`iam.assignment.no-authority`).
- Ending a posting does not end access yet; disable the account or revoke the assignment.

## Two-person rules

| Work | First person | A different person | Enforced in |
|---|---|---|---|
| Role version | Drafts and submits | Approves; refused if the approver holds the role and the version adds permissions | `RoleService` |
| Role assignment | Requests (never for themselves) | Approves | `AssignmentService` |
| Discount | Requests | Decides | `BillingService` |
| Daily cash close | Submits | Decides | `AccountsService` |
| Stock adjustment | Requests | Decides | `InventoryService` and a database check |
| Optical lab job | Fits | Quality check and dispatch | `OpticalService` and a database check |
| Lab result | Enters | Validates | `DiagnosticsService` and a database check |
| Imaging report | Writes | Not enforced yet | (to build) |

Other fixed rules: bill lines raised by pharmacy, optical or diagnostics cannot be removed at the billing counter; the image gateway (`ImagingAccess`, nginx `auth_request`) allows GET and HEAD for one study linked to an order at a centre where the user has an imaging permission, never the archive listing or its own pages.

## Adding a permission or a module

1. Add the permission to `iam.permission` in a new migration, and to the `Permission` enum with its `tenantOnly` flag.
2. If it starts a new module, extend `org.permission_module` and `TenantLicensing.ALL_MODULES`, and backfill `org.tenant_module` where existing organisations should have it.
3. Call `access.require` in the service before the change; filter lists with `centresWhere`.
4. Add the menu entry in `frontend/src/layout/navigation.ts` with `permission` or `anyOf`.
5. Give it to roles through the Roles screen (two people), or in the demonstration seed.
6. Add a test for allowed, refused and out-of-scope. The access guide picks it up with no further change.

## Still to be built

| Requirement | Priority |
|---|---|
| Centre director read access to laboratory, imaging, pharmacy, optical, optical lab and inventory | MVP |
| Imaging report verified by a different radiologist (with a co-sign option for single-radiologist centres) | MVP |
| Licence command refuses module sets that cannot work (pharmacy or optical without inventory and billing; lab or imaging without billing) | MVP |
| "Why can't I?" check for any user and action (extends the effective-access explanation) | MVP |
| Access matrix export (role × permission, user × centre × role) | MVP |
| Licence expiry warnings to administrators at 30, 7 and 1 days | MVP |
| Access ends automatically when a posting ends | MVP |
| Break-the-glass access with reason, time limit and review | Phase 2 |
| Restricted patients (VIP, staff, psychiatry, HIV, PCPNDT) | Phase 2 |
| Department scope below centre | Phase 2 |
| Provider role templates per centre type | Phase 2 |
| Provider console in the browser with its own sign-in | Phase 2 |
| Time-limited, consented, read-only provider support access | Phase 2 |
| Access review every 90 days | Phase 2 |
| Field masking for phone, address and ID numbers | Phase 2 |
| External users: patient portal, referring doctor, insurer or TPA | Phase 2 |
| Sign-in limits by network and working hours | Optional |
| User seats per licence | Optional |
