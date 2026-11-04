# Testing and CI
> What is tested, where, and what CI checks on every push.

## Backend tests (`backend/src/test`)

| Test | Proves |
|---|---|
| `ModularityTest` | Module boundaries and no cycles (Spring Modulith) |
| `MigrationPrivilegesTest` | Migrations apply to an empty database; the runtime role cannot create tables or use `public` |
| `RowLevelSecurityTest` | One tenant cannot read or write another's rows; audit is append-only |
| `PermissionCatalogueTest` | `Permission` enum equals `iam.permission` |
| `ApplicationStartupTest` | The application starts with the production configuration |
| `OpenApiContractTest` | Exports the OpenAPI contract to `target/openapi.json` |
| `iam/AccessScenarioTest` | Ten ordered acceptance scenarios of slice 1.1: bootstrap, roles with independent approval, scoped assignments, authority, revocation, denial audit |
| `operations/DemoSeedTest` | The 20-centre demonstration tenant through the normal rules |
| `operations/ClinicSeedTest` | Vijay Eye Clinic end to end, then the boundaries: front office cannot see findings or record optometry; accounts and administrators cannot search patients; discount self-approval refused; no payment while a discount is pending; no checkout with balance; no surgery before the checklist; the accountant cannot approve the day close |

All integration tests use one PostgreSQL 18.6 container (Testcontainers) with the production role model. Keycloak is replaced by a mock `IdentityProvisioner`.

## Frontend tests

- **Unit** (Vitest): HTTP client errors and CSRF, navigation visibility, session.
- **Browser** (Playwright, desktop and phone): administration flows and four clinic personas (reception check-in, optometry boundaries, doctor's visit view, accounts discount approval) against the mock API.

## CI (`.github/workflows/ci.yml`)

| Job | Steps |
|---|---|
| Backend build and tests | `mvn verify`; compare the exported contract with `contracts/openapi.json` (warning and printed contract if different) |
| Staff application build and tests | `npm ci`; regenerate the client and fail if it differs; lint; typecheck; unit tests; build; Playwright |
| Deployment files check | shellcheck of all scripts; `docker compose config`; realm JSON validity |

A change is ready when all three jobs are green and the contract step says "contract unchanged".

## Acceptance on the server

Each slice has a notes file in `docs/` (`slice-1.1.md`, `slice-2.1.md`) with an acceptance checklist run on core-node with real Keycloak sign-in; record the evidence there.
