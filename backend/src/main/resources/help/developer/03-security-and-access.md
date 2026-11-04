# Security and the access model
> Permissions, scopes, roles, assignments, authority, independent approval, tenant isolation and audit, as implemented.
<!-- audience: administrator -->

## The pieces

| Code | Database | Notes |
|---|---|---|
| `kernel.Permission` (enum) | `iam.permission` | Must match exactly; `PermissionCatalogueTest` checks. `tenantOnly` permissions count only from a TENANT-scope assignment. |
| `kernel.Scope(type, id)` | `scope_type`, `scope_id` | TENANT, ORG_NODE (covers the node's subtree by `org_node.path` prefix), CENTRE |
| Role, role version | `iam.role`, `iam.role_version`, `iam.role_version_grant` | Only the PUBLISHED version grants; one open draft at a time |
| Assignment | `iam.role_assignment` | kind ACCESS (gives the role's permissions) or AUTHORITY (may request that role, or every role if `role_id` is null, for others at the scope) |
| Responsibility | `iam.responsibility_*` | Never consulted by access checks |

## Evaluating access

`AccessEvaluator` (implements `AccessControl`) loads the user's grants:

```
active user → ACTIVE ACCESS assignments valid today → ACTIVE role → PUBLISHED version → permissions
```

`holds(actor, permission, scope)` is true if any grant has that permission and its scope **covers** the target: tenant covers everything in the tenant; a node covers its subtree; a centre covers only itself. `require(...)` throws `AccessDeniedException` and writes a **DENIED** audit event in its own transaction. `centresWhere(actor, permission)` lists centres for filtering lists.

## Independent approval

- Role versions: `decided_by <> submitted_by` (CHECK) and the service refuses the drafter.
- Assignments: `decided_by <> requested_by AND decided_by <> user_id` (CHECK). Requesting needs AUTHORITY for the role at a covering scope; approving needs `iam.assignment.approve` there.
- Bills: `discount_decided_by <> discount_requested_by` (CHECK).
- Day close: `decided_by <> submitted_by` (CHECK).
- Bootstrap creates exactly **two** administrators so the first change can be approved (ADR-0009). The system administrator role contains only administration permissions (`Permission.administrative()`): org, workforce, iam, audit.

## Approvers cannot widen their own access

`RoleService.decide` refuses to publish a role version that adds permissions when the approver holds (or has pending) an ACCESS assignment of that role (`iam.role.version.self-benefit`, audited as DENIED). Someone who does not hold the role must approve. Consequence: additions to the system administrator role need a third administrator who does not hold it.

## One-time code (TOTP) per account

- `iam.user_account.mfa_required` (V5, default true). Changed by `UserService.changeMfa` only with `iam.user.manage` at **tenant** scope, never for oneself, with a reason; audited as `iam.user.mfa.require` / `iam.user.mfa.exempt`.
- `IdentityProvisioner.setOneTimeCodeRequired`: *required* adds Keycloak's `CONFIGURE_TOTP` required action if the user has no OTP credential and ends their sessions; *not required* deletes OTP credentials and removes the action. Keycloak's default browser flow asks for OTP only when the user has one configured.
- The Keycloak account console is closed at the gateway, so users cannot remove their own authenticator.

## Financial details are gated separately

The visit record returns bills only to holders of `billing.read` or `opd.checkin` at the centre (`billingVisible`); clinical access alone does not reveal money. Bill mutations lock the bill row (`select … for update`) and payment posting is a conditional update on the version, so two concurrent payments cannot both post.

## Help visibility

`HelpService` reads `help/{manual,developer}/NN-slug.md`. The line `<!-- audience: … -->` after the summary is `everyone`, `administrator` (holds `iam.role.approve` group-wide) or permission codes (any one held at some scope). `GET /v1/help` returns only the matching chapters; the frontend never receives the others.

## Revocation

Disabling a user sets status DISABLED, bumps `access_version`, deletes their rows in `session.spring_session` and disables the Keycloak user. Revoking an assignment takes effect on the next request because grants are not cached across requests.

## Tenant isolation (ADR-0007)

- Every tenant-owned table has `tenant_id` and the policy `tenant_isolation` using `current_setting('app.tenant_id')`.
- The application connects as `core_neha_app` (no DDL, not table owner, so RLS applies). Flyway runs as `core_neha_owner`.
- `TenantAwareDataSource` sets `app.tenant_id` from `TenantContext` on each borrowed connection.
- `iam.login_identity` has no RLS (needed before the tenant is known). `org.tenant_id_by_code()` is a narrow SECURITY DEFINER function for operator commands.

## Clinical data separation

`eye.clinical.read` gates optometry, consultation and surgery content. `VisitService.chart` returns `clinicalVisible=false` and null clinical sections for front office; history filters clinical fields by centres where the actor holds the permission. Patient views and clinical views are audited with `recordIndependently`.

## Audit (ADR-0008)

- `AuditLog.record(entry)` is `@Transactional(MANDATORY)`: call it inside the service transaction.
- `AuditLog.recordIndependently(entry)` uses REQUIRES_NEW: for denials and audited reads.
- The runtime role has only SELECT and INSERT on `audit.audit_event`.
- Put identifiers and before/after values in `details`; never secrets, never full clinical text.

## Secrets

Generated on the server by `ops/gen-secrets.sh` into `~/core-neha/secrets` (700/600), mounted as Compose secrets at `/run/secrets`, read by Spring through `configtree`. One-time passwords from seeds are written to `secrets/onboarding/` and never logged.
