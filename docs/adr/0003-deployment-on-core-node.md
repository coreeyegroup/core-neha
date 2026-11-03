# ADR-0003: Deployment layout on core-node (temporary server)

- Status: Accepted
- Date: 2026-11-04

- Docker Engine + Compose on core-node; Compose project `core-neha`.
- Containers: `gateway` (nginx), `keycloak`, `postgres` now; `backend` added in the next increment.
- Only two host ports, both on 127.0.0.1: 18400 (gateway) and 18480 (Keycloak admin console, SSH tunnel only).
- `postgres` sits only on an internal Docker network (`backplane`, no outbound access, no host port).
- Memory limits: postgres 1536 MB, keycloak 1280 MB, backend 1536 MB (next increment), gateway 128 MB; about 4.5 GB of 15 GB.
- Files: `~/core-neha/repo` (Git), `~/core-neha/config` (non-secret settings), `~/core-neha/secrets` (mode 700, files 600), `~/core-neha/backups` (mode 700).
- Automatic start: Docker service enabled; containers `restart: unless-stopped`.
- Existing services on core-node (Caddy, Apache, PostgreSQL 14, Kafka, COREI OS) are not modified, except one marked route block added to Caddy (ADR-0004).

**Why:** isolation and resource limits on a shared server; reproducible move to the next server (copy repo, config, secrets, restore backup).
