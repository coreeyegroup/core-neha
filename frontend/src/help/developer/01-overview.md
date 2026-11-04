# Overview
> What core-neha is, the technology stack, the repository layout, and the principles every change must keep.

## What it is

core-neha is a hospital management system for a multi-centre group: one installation, one database, many centres (hospitals, clinics, optical stores, laboratories). Slice 1.1 built the organisation and access foundation; slice 2.1 added patients, the eye-care outpatient visit, surgery, and billing for an eye clinic.

## Stack

| Layer | Technology (exact versions in `docs/versions/VERSIONS.md`) |
|---|---|
| Backend | Java 25, Spring Boot 4.1, Spring Modulith 2.1, Spring Security (OAuth2 client), Spring Session JDBC, JdbcClient (no JPA), Flyway, springdoc OpenAPI |
| Database | PostgreSQL 18, one schema per module, row-level security per tenant |
| Identity | Keycloak 26 (OIDC, TOTP), realm `core-neha` |
| Frontend | React 19, TypeScript 6, Vite 8, MUI 9, TanStack Query 5, React Router 8, Orval-generated API client |
| Gateway | nginx 1.30 (serves the app, proxies `/api` and `/auth`) |
| Runtime | Docker Compose on one Ubuntu server, behind the host's Caddy |
| CI | GitHub Actions: backend tests, frontend tests and browser tests, deployment file checks |

## Repository layout

```
backend/                  Spring Boot application
  src/main/java/in/coreeye/coreneha/
    kernel/               shared types: Actor, Permission, Scope, AccessControl, exceptions, BusinessTime
    platform/             tenant-aware data source, problem responses
    audit/                audit log writer and search
    org/                  tenant, hierarchy, legal entities, departments, centres
    workforce/            positions, establishments, people, postings
    iam/                  users, roles, assignments, responsibilities, security config, Keycloak
    patient/              patient identity (UHID)
    eyecare/              appointments, visits, optometry, consultation, surgery, clinic board
    billing/              price list, bills, payments, discounts, collections, day close
    operations/           server-side commands: bootstrap, demo seed, clinic seed
  src/main/resources/db/migration/   Flyway V1..V4
  src/test/java/...       integration tests (Testcontainers PostgreSQL)
frontend/                 staff application (React)
  src/api/generated/      Orval client generated from contracts/openapi.json (do not edit)
  src/pages/, src/pages/clinic/   screens
  src/help/manual, src/help/developer   this knowledge centre (Markdown)
  e2e/                    Playwright tests and the mock API
contracts/openapi.json    the API contract (exported by the backend, checked in CI)
deploy/                   compose.yaml, postgres init, keycloak image and realm, gateway config, caddy snippet, env examples
ops/                      operator scripts (cn, init-config, gen-secrets, preflight, status, backup, restore-test, caddy-route, bootstrap, demo-seed, clinic-seed)
docs/                     ADRs, version matrix, operations guide, slice notes
.github/workflows/ci.yml  CI
```

## Principles (do not break these)

1. **Modular monolith.** One deployable; modules talk only through public types in their base package. `ModularityTest` fails the build otherwise.
2. **Every action checks a permission, never a role name.** `AccessControl.require(actor, Permission, Scope, …)`. There is no "admin can do everything" path.
3. **Every change is audited in the same transaction.** A failed audit write rolls the change back.
4. **Two people for anything that expands access or moves money off the normal path**, enforced in code and by database CHECK constraints.
5. **Tenant isolation in the database** by row-level security, not only in code.
6. **The API contract is the source of truth for the frontend.** Change Java, export the contract, regenerate the client.
7. **No secrets in the repository, chat or logs.** Secrets live in `~/core-neha/secrets` on the server.
8. **Simple, proven designs.** Plain SQL with JdbcClient, records for models, no ORM, no message broker until needed.

## Where to start reading code

1. `kernel/AccessControl.java` and `iam/AccessEvaluator.java`: how access works.
2. `org/OrgService.java`: the standard shape of a service (check, change, audit).
3. `eyecare/VisitService.java`: a full workflow with stages.
4. `operations/ClinicSeed.java`: the whole clinic built through the services, which is the best end-to-end example.
5. `frontend/src/pages/clinic/VisitChart.tsx`: how a screen uses the generated client and permissions.
