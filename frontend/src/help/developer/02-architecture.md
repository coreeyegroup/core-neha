# Architecture
> Runtime components, the request path, modules and their dependencies, and how a request is processed.

## Runtime components

```
Browser
  │ https://coreeye.in/core-neha/...
  ▼
Caddy (host, TLS)  ── handle /core-neha/*  →  127.0.0.1:18400
  ▼
gateway (nginx, container)          network "edge" + "backplane"
  ├─ /core-neha/            → staff app (static, built into the image)
  ├─ /core-neha/api/        → backend:8080   (Spring Boot, context path /core-neha/api)
  ├─ /core-neha/auth/       → keycloak:8080  (login pages, OIDC endpoints)
  └─ /core-neha/auth/admin, /realms/master, metrics, health → 403
backend   ── postgres:5432 (database core_neha, role core_neha_app)
          ── keycloak:8080 (token, JWKS, userinfo, admin API for provisioning)
keycloak  ── postgres:5432 (database keycloak)
postgres  (volume core-neha_pgdata)
```

- Only two ports are published, both on 127.0.0.1: **18400** (gateway) and **18480** (Keycloak admin console, reached through an SSH tunnel).
- The `backplane` network is internal (no internet). The `edge` network exists only to publish the ports.
- The public address is configuration (`CORE_NEHA_PUBLIC_BASE_URL`, `CORE_NEHA_BASE_PATH`), so the system can move to another domain or server without code changes (ADR-0004).

## Authentication: backend for frontend (ADR-0005)

The browser never holds tokens.

1. The app calls `GET /api/v1/me`; without a session it gets **401** and shows **Sign in**.
2. **Sign in** goes to `/api/oauth2/authorization/keycloak`; the backend (confidential client `core-neha-staff`) redirects to Keycloak.
3. After password and TOTP, Keycloak redirects to `/api/login/oauth2/code/keycloak`. The backend exchanges the code over the internal network and validates the ID token against the **public** issuer.
4. The backend creates a server session (Spring Session JDBC, table `session.spring_session`) and sets the cookie `CORE_NEHA_SESSION` (HttpOnly, Secure, SameSite=Lax).
5. Mutating requests carry the CSRF token from the `XSRF-TOKEN` cookie in the `X-XSRF-TOKEN` header (`frontend/src/api/http.ts` does it).
6. Sessions expire after 30 minutes idle and 12 hours absolute. Logout ends the session and Keycloak's session.

## Request processing in the backend

```
HTTP request
 → Spring Security (session, CSRF)
 → ActorContext filter: subject → iam.login_identity → iam.user_account (must be ACTIVE)
     → Actor(userId, tenantId, personId, username, accessVersion)
     → TenantContext.set(tenantId)        ─┐
 → Controller (thin: parse, validate, call) │
 → Service @Transactional                   │ TenantAwareDataSource sets
     access.require(actor, permission, scope, …)   app.tenant_id on every
     SQL via JdbcClient                     │ connection → RLS filters rows
     audit.record(AuditEntry…)  (same tx)  ─┘
 → ProblemResponses maps DomainException → RFC 9457 problem JSON {code, detail, requestId}
```

The actor is re-resolved on **every** request, so disabling a user or revoking an assignment takes effect on their next request. Access grants are memoised only within one request.

## Modules and dependencies

| Module | Owns (schema) | Depends on |
|---|---|---|
| kernel | — (shared types) | — |
| platform | — | kernel |
| audit | `audit` | kernel |
| org | `org` | kernel, audit |
| workforce | `workforce` | kernel, audit, org |
| iam | `iam`, `session` | kernel, audit, org, workforce |
| patient | `patient` | kernel, audit, org |
| billing | `billing` | kernel, audit, org, patient |
| eyecare | `eyecare` | kernel, audit, org, patient, workforce, billing |
| operations | — | everything (seed and bootstrap commands) |

Rules:

- A module exposes a **directory** interface for others (`OrgDirectory`, `WorkforceDirectory`, `PatientDirectory`, `BillingDirectory`): plain lookups with no access checks; the caller checks its own permission.
- Dependencies point one way. Billing refers to a visit or surgery only by id (`visit_ref`, `surgery_ref`) and never reads clinical data.
- Read models may join another module's table **for display names** (patient name, staff name). Writes and business rules stay in the owning module (ADR-0011).
- Spring Modulith's `ModularityTest` verifies there are no cycles and no access to another module's internals.

## Operator commands

`OperationsRunner` runs a command when `CORE_NEHA_COMMAND` is set (`bootstrap`, `demo-seed`, `clinic-seed`) and then exits. The `ops/*.sh` scripts start a one-off backend container with that variable and no web server. Commands go through the same services and rules as the web application.
