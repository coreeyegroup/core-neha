# core-neha operations guide

All commands run as `coreeye` from `~/core-neha/repo`. Commands marked **sudo** change the host.

## 1. Services and addresses

| Service | Container | Reachable at | Notes |
|---|---|---|---|
| Gateway (nginx 1.30.6) | `core-neha-gateway-1` | 127.0.0.1:18400 | Public through Caddy at `https://coreeye.in/core-neha` |
| Identity (Keycloak 26.8.1) | `core-neha-keycloak-1` | via gateway `/core-neha/auth/`; admin console 127.0.0.1:18480 only | Admin console and master realm refused publicly |
| Database (PostgreSQL 18.6) | `core-neha-postgres-1` | internal network only | Databases `core_neha`, `keycloak`; volume `core-neha_pgdata` |

Memory limits: postgres 1536 MB, keycloak 1280 MB, gateway 128 MB (backend 1536 MB when added).

## 2. First installation

```bash
ops/init-config.sh
ops/gen-secrets.sh
ops/preflight.sh
ops/cn up -d --build
ops/status.sh
sudo -v && ops/caddy-route.sh add      # sudo: publishes the route
```

## 3. Administration access (SSH tunnel)

From your own computer:

```bash
ssh -L 18480:127.0.0.1:18480 coreeye@coreeye.in
```

Then open `http://localhost:18480/core-neha/auth/admin/`. The temporary Keycloak administrator is `core-neha-bootstrap-admin`; its password is in `~/core-neha/secrets/keycloak_bootstrap_admin_password` (read it on the server with `cat`, never paste it into chat or tickets). Replace this temporary account with a named administrator account with MFA, then delete the temporary one.

## 4. Logs

```bash
ops/cn logs --since 30m gateway      # JSON access log: path only, no query strings
ops/cn logs --since 30m keycloak     # JSON
ops/cn logs --since 30m postgres
```

Docker keeps at most 5 × 20 MB per container (local log driver).

## 5. Backup and restore

```bash
ops/backup.sh            # ~/core-neha/backups/<UTC timestamp>/ ; keeps 14 days
ops/restore-test.sh      # restores the latest backup into an isolated throwaway container
```

Nightly backup (user crontab, no sudo):

```bash
( crontab -l 2>/dev/null; echo '15 21 * * * $HOME/core-neha/repo/ops/backup.sh >> $HOME/core-neha/backups/backup.log 2>&1' ) | crontab -
```

21:15 UTC is 02:45 IST. Copy backups off this server regularly; a backup on the same disk does not protect against losing the server.

**Real restore (replaces live data — only after an incident decision):**

```bash
ops/cn stop keycloak gateway
ops/cn exec -T postgres dropdb -U postgres --force core_neha
ops/cn exec -T postgres createdb -U postgres -O core_neha_owner core_neha
ops/cn exec -T postgres pg_restore -U postgres --exit-on-error -d core_neha < ~/core-neha/backups/<ts>/core_neha.dump
# repeat for keycloak (owner keycloak), then:
ops/cn up -d && ops/status.sh
```

## 6. Update and rollback

```bash
cd ~/core-neha/repo
git fetch && git log --oneline HEAD..origin/main    # review what changes (once a remote exists)
ops/backup.sh                                        # always back up first
git pull --ff-only
ops/preflight.sh && ops/cn up -d --build && ops/status.sh
```

Rollback: `git checkout <previous commit>` then `ops/cn up -d --build`. If a database migration ran (from increment 2 on), restore the pre-update backup as in section 5; migrations are forward-only.

## 7. Moving to another server or domain

1. On the new server: install Docker, copy `~/core-neha/repo`, `~/core-neha/config`, `~/core-neha/secrets` (keep modes 700/600) and the latest backup.
2. If the address changes, edit `CORE_NEHA_PUBLIC_BASE_URL` and `CORE_NEHA_BASE_PATH` in `config/core-neha.env` (ADR-0004), then `ops/init-config.sh`.
3. `ops/cn up -d --build`, restore the backup (section 5), update the Keycloak client redirect URIs if the address changed, `ops/status.sh`.
4. Point DNS to the new server and add the proxy route there.

## 8. Removing the public route

```bash
sudo -v && ops/caddy-route.sh remove
```

## 9. Stop and start

```bash
ops/cn stop      # data stays in the volume
ops/cn up -d
```

`ops/cn down -v` would delete the database volume. Never run it on a server with data you need.
