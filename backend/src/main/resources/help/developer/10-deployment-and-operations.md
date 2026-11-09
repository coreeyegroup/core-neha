# Deployment and operations
> The server layout, configuration and secrets, the ops scripts, updating, backups, moving servers, and known operational issues.
<!-- audience: administrator -->

The full runbook is `docs/operations/OPERATIONS.md` in the repository. This chapter is the overview.

## Layout on the server

```
~/core-neha/repo       git checkout (this repository)
~/core-neha/config     core-neha.env (public URL, base path, ports, environment), bootstrap.env
~/core-neha/secrets    generated passwords and client secrets (700/600); onboarding/ one-time passwords
~/core-neha/backups    database dumps, 14 days
```

Compose project `core-neha`: `postgres`, `keycloak`, `backend`, `gateway`. Memory limits 1.5 GB / 1.25 GB / 1.5 GB / 128 MB. The host's Caddy routes `coreeye.in/core-neha/*` to `127.0.0.1:18400` through a marked block managed by `ops/caddy-route.sh`.

## Scripts (`ops/`)

| Script | Does |
|---|---|
| `cn …` | `docker compose` with the right files: `ops/cn ps`, `ops/cn logs --since 30m backend`, `ops/cn up -d` |
| `init-config.sh` | Create `config/core-neha.env` from the example |
| `gen-secrets.sh` | Generate all secrets on the server, never printed |
| `preflight.sh` | Check Docker, config, secrets and modes, free ports, subnets |
| `status.sh` | Containers, localhost-only ports, routes through the gateway, issuer, resources, other services still active |
| `backup.sh` / `restore-test.sh` | Dump both databases / restore the latest into a throwaway container |
| `caddy-route.sh add|remove|show` | Publish or remove the public route (sudo) |
| `bootstrap.sh` | Create a real tenant and its two first administrators (production onboarding) |
| `demo-seed.sh` | Load the 20-centre demonstration tenant (demo only, once) |
| `demo-reset.sh` | Demo only: back up, wipe the application and Keycloak databases, and reload both demo seeds (asks you to type RESET DEMO) |
| `clinic-seed.sh` | Add Vijaya Eye Clinic to the demonstration tenant (demo only, once, after demo-seed) |
| `clinic-staff.sh` | Add the clinic's full staff: 40 positions in its six departments, two people each, with logins (demo only; safe to run again, adds only what is missing) |
| `tenant.sh` | Software vendor's console: list hospital chains, set a chain's licence (modules, validity, centres), suspend or reactivate a chain (ADR-0013) |
| `login-check.sh` | Read-only sign-in diagnosis: each step, the accounts in both databases, and the reason for recent failed sign-ins |

## Website contact details

Edit `frontend/website/assets/site-config.js` (phone, WhatsApp, email, address lines, timings, map link, booking link), commit, then on the server `git pull` and `ops/cn up -d --build gateway`.

## Updating

```bash
cd ~/core-neha/repo
ops/backup.sh
git pull --ff-only
ops/preflight.sh && ops/cn up -d --build
ops/cn restart gateway        # the gateway resolves the backend's address only at start
ops/status.sh
```

Migrations run automatically when the backend starts. They are forward-only: to roll back, check out the previous commit **and** restore the backup taken before the update.

## Moving to another server or domain

Copy `repo`, `config`, `secrets` and the latest backup; adjust `CORE_NEHA_PUBLIC_BASE_URL` / `CORE_NEHA_BASE_PATH` if the address changes; `ops/cn up -d --build`; restore; update Keycloak redirect URIs if the address changed; add the proxy route; point DNS. Nothing in the code depends on the IP address.

## Identity administration

The Keycloak admin console is only on `127.0.0.1:18480`. From your computer: `ssh -L 18480:127.0.0.1:18480 coreeye@<server>` then `http://localhost:18480/core-neha/auth/admin/`. Use it for password and authenticator resets. Replace the temporary bootstrap admin with named administrators with MFA.

## Known operational issues

| Symptom | Cause | Remedy |
|---|---|---|
| Pasted commands close the SSH session | `set -e` typed directly in an interactive shell | Paste blocks wrapped in `( set -euo pipefail … )` |
| One-off command: "Address already in use" | A fixed container IP (fixed in compose; backend has none now) | Pull the latest compose file |
| `demo-seed` fails "tenant already exists" | Seeds run once | Skip it; run `clinic-seed.sh` alone |
| 502 after updating the backend | Gateway still has the old backend address | `ops/cn restart gateway` |
| sudo password prompt swallows pasted lines | sudo asked for a password mid-paste | Run `sudo -v` alone first |

## Logs and monitoring

`ops/cn logs` shows JSON logs (ECS format for the backend). The gateway logs paths without query strings. Health: `ops/status.sh`. Nightly backup via the user crontab (see the operations guide), and copy backups off the server.
