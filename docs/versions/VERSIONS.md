# core-neha version matrix

Status: **verified by lookup 2026-11-03/04; final confirmation = successful Maven and npm dependency resolution and a green build on core-node** (recorded in this file when the backend and frontend increments are built).

Raw evidence: `version-check-20261103T1921Z.txt` (run on core-node) plus npm peer-dependency checks.

## Pinning policy

1. Every version is pinned exactly: container images by full tag (digest recorded after first pull), Maven by exact version, npm by exact version with a lockfile.
2. npm packages: the newest release that is at least 14 days old. Newer releases wait.
3. JVM stack: the Spring Boot version that Spring Initializr offers as its default, and the companion versions that Initializr maps to it (Modulith, springdoc). Libraries managed by Spring Boot's dependency management are not overridden.
4. LTS or supported lines only. No milestones, release candidates or snapshots.
5. A change to this matrix needs an entry in the change log below.

## Runtime and infrastructure

| Component | Pinned | Source of truth | Support / note |
|---|---|---|---|
| Java runtime (containers) | Eclipse Temurin **25.0.5+8** (`eclipse-temurin:25.0.5_8-jre-noble`) | endoflife.date, Docker Hub | LTS, supported to 2031-09-30. Host Java 17 is not used. |
| Build JDK | `maven:3.9.16-eclipse-temurin-25-noble` | Docker Hub, Maven Central | Maven 3.9.16 (3.10.0 is a new minor line; adopt later by ADR) |
| PostgreSQL | **18.6** (`postgres:18.6-alpine3.24`) | endoflife.date, Docker Hub | Supported to 2030-11-14. Separate from the host's PostgreSQL 14. |
| Keycloak | **26.8.1** (`quay.io/keycloak/keycloak:26.8.1`) | endoflife.date, quay.io | Current line; 26.7 reached end of life 2026-10-01 |
| Gateway | nginx **1.30.6** (`nginx:1.30.6-alpine3.24`) | endoflife.date, Docker Hub | Stable line |
| Node.js (build containers) | **24.22.2** LTS "Krypton" (`node:24.22.2-alpine3.24`) | nodejs.org, endoflife.date | Maintenance to 2028-04-30. Node 26 entered LTS on 2026-10-28; move by ADR at Stage 2. |
| Docker Engine / Compose (host) | 29.9.2 / v5.7.0 | Installed on core-node | |

## Backend (Maven)

| Library | Pinned | Source | Note |
|---|---|---|---|
| Spring Boot | **4.1.2** | Spring Initializr default; Maven Central | Open-source support to 2027-07-31. 4.0 ends 2026-12-31. |
| Spring Modulith BOM | **2.1.1** | Initializr mapping for Boot 4.1.x | Maven Central also has 2.1.2; adopt after Initializr maps it or by ADR |
| springdoc-openapi | **3.2.0** | Initializr mapping for Boot 4.1.x | |
| Flyway, PostgreSQL JDBC, Testcontainers, JUnit, Jackson, Micrometer | Spring Boot managed | Boot 4.1.2 BOM | Not overridden. Flyway needs `flyway-database-postgresql`. |
| ArchUnit | **1.5.1** | Maven Central | |
| Testcontainers Keycloak | **4.4.0** | Maven Central | Compatibility with Keycloak 26.8 confirmed by test run |
| Structured logging | Spring Boot built-in (ECS JSON) | Boot | No logstash encoder needed |
| db-scheduler | not used in slice 1.1 | | Added when a scheduled job is needed (ADR) |

## Frontend (npm)

| Package | Pinned | Note |
|---|---|---|
| react, react-dom | 19.3.1 | |
| @types/react, @types/react-dom | 19.3.0 | |
| vite | 8.3.5 | Exactly 14 days old on 2026-11-04 |
| @vitejs/plugin-react | 6.1.2 | 6.2.1 too new |
| typescript | **6.0.3** | **Correction:** TypeScript 7.0.2 is available, but typescript-eslint 8.72 supports TypeScript `>=4.8.4 <6.1.0` only |
| @mui/material, @mui/icons-material | 9.5.0 | MUI Community (MIT) |
| @emotion/react / @emotion/styled | 11.14.0 / 11.14.1 | MUI peer dependencies |
| react-router | 8.5.0 | 8.5.1 too new; requires Node >= 22.22 |
| @tanstack/react-query | 5.104.5 | 5.105.x too new |
| Data grid | MUI Table + own sorting/filter component | Decision I-A8 (proposed): no licensed grid; @tanstack/react-table 9 not adopted yet |
| react-hook-form | 7.90.0 | 7.91.0 too new |
| @hookform/resolvers | **5.9.2** | **Correction:** 7.0.x is too new; 6.0.0 has conflicting optional peer dependencies (npm refuses to resolve); 5.9.2 supports zod 4 |
| zod | 4.6.7 | |
| vitest | 5.0.5 | 5.1.0 too new |
| @testing-library/react / jest-dom / user-event | 16.3.3 / 7.0.1 / 14.6.8 | |
| jsdom | 30.1.3 | |
| @playwright/test | 1.64.1 | |
| orval | 8.44.0 | 8.46.0 too new |
| eslint / @eslint/js | 10.13.0 / 10.0.1 | |
| typescript-eslint | 8.72.0 | |
| eslint-plugin-react-hooks | 7.1.1 | |
| @fontsource/ibm-plex-sans | 5.3.0 | Self-hosted font; no external font CDN |
| @types/node | 24.19.2 | Matches the Node 24 build image |
| npm | 11 (bundled with Node 24) | npm 10 fails to resolve the dependency tree (installer bug); the lockfile is produced with npm 11 |

**Not used:** oidc-client-ts. The browser never holds tokens; see ADR-0005.

## Change log

| Date | Change | Reason |
|---|---|---|
| 2026-11-04 | Initial matrix | Version check on core-node and npm peer checks |
| 2026-11-04 | TypeScript 6.0.3 instead of 7.0.x | typescript-eslint peer range |
| 2026-11-04 | Node 24 LTS for builds (baseline said "current LTS") | Node 26 LTS only 6 days old |
| 2026-11-04 | Maven 3.9.16 (not 3.10.0) | Proven line; 3.10 adoption by ADR |
| 2026-11-04 | @hookform/resolvers 5.9.2; no TanStack Table; npm 11 for installs | Dependency resolution on the real lockfile |
| 2026-11-04 | No RestClient auto-configuration (Boot 4 moved it to a separate starter) | The Keycloak admin client builds RestClient directly |

## Verified by build

- Backend: Maven resolution and 24 tests green on GitHub Actions (Spring Boot 4.1.2, Modulith 2.1.1, Testcontainers 2.0.5, PostgreSQL 18.6 container).
- Frontend: npm 11 install, type-check, lint, 7 unit tests, production build and 12 browser workflow tests green locally; repeated in CI.
