# core-neha

Hospital management platform for a multi-centre group. This repository holds the permanent product: source, deployment, operations and decision records.

> **Demonstration environment.** The current deployment at `https://coreeye.in/core-neha` holds synthetic data only (ADR-0004, ADR-0010).

## Status

| Increment | Contents | State |
|---|---|---|
| 1. Environment | Compose stack (PostgreSQL 18.6, Keycloak 26.8.1, nginx gateway), secrets, backup, restore test, Caddy route | Delivered and running on core-node |
| 2. Backend foundation | Spring Boot 4.1.2 modular monolith: organisation, workforce, access, audit; OIDC login; bootstrap and demo commands | Delivered; CI green |
| 3. Staff frontend | React 19 + MUI 9: centres, departments, positions, people, users and access, roles, approvals, audit | Delivered; CI green |
| 4. Slice 1.1 acceptance | Evidence on core-node (see docs/slice-1.1.md) | Next |

## Layout on the server

```
~/core-neha/
  repo/       this Git repository
  config/     core-neha.env (not secret, not in Git)
  secrets/    generated secrets (700 / 600, never in Git)
  backups/    database backups (700 / 600)
```

## Quick reference

```bash
cd ~/core-neha/repo
ops/init-config.sh      # create and check ~/core-neha/config/core-neha.env
ops/gen-secrets.sh      # generate missing secrets (never prints or replaces them)
ops/preflight.sh        # read-only checks
ops/cn up -d --build    # start or update the stack
ops/status.sh           # health, exposure and resource report
ops/backup.sh           # back up both databases
ops/restore-test.sh     # restore the latest backup into a throwaway container
ops/caddy-route.sh add  # publish https://coreeye.in/core-neha (needs sudo)
ops/demo-seed.sh        # load the synthetic 20-centre demonstration tenant (demo environment only)
ops/clinic-staff.sh     # demo only: add the clinic's full staff (40 positions, two people each)
ops/demo-reset.sh       # demo only: wipe and reload the demo data (type RESET DEMO)
ops/login-check.sh      # read-only sign-in diagnosis
ops/tenant.sh list      # vendor: hospital chains and licences (also: licence, suspend, activate)
ops/clinic-seed.sh      # add Vijaya Eye Clinic: 6 departments, 12 logins, synthetic patients (after demo-seed)
ops/bootstrap.sh        # create a real tenant with its two first administrators
```

Details: [docs/operations/OPERATIONS.md](docs/operations/OPERATIONS.md) · Versions: [docs/versions/VERSIONS.md](docs/versions/VERSIONS.md) · Decisions: [docs/adr](docs/adr/README.md)

## Public website and staff application

- `https://coreeye.in/core-neha/` is the Vijaya Eye Clinic website (`frontend/website/`, contact details in `assets/site-config.js`).
- `https://coreeye.in/core-neha/staff/` is the staff application (Staff login on the website).

## Knowledge centre

Inside the staff application at **Help → Help centre** (the **?** button opens the chapter for the
current screen). Each person sees only the chapters for their own work; the developer guide is for
group-wide administrators. Sources: `backend/src/main/resources/help/{manual,developer}/`; update
them in the same change as the feature.
