# ADR-0004: Public address: https://coreeye.in/core-neha (temporary, shared origin)

- Status: Accepted with conditions
- Date: 2026-11-04

All public URLs derive from one setting, `CORE_NEHA_PUBLIC_BASE_URL` (now `https://coreeye.in/core-neha`), and its path `CORE_NEHA_BASE_PATH` (`/core-neha`). No IP address appears in configuration. The existing Caddy forwards `/core-neha/*` (prefix kept) to 127.0.0.1:18400 through a marked block (`# CORE NEHA BEGIN/END`).

The gateway refuses the Keycloak admin console and master realm publicly; administration uses the localhost-only port through an SSH tunnel.

**Risk:** under a path, core-neha shares the browser origin `coreeye.in` with other applications on that host. Script running in any of them could act within a logged-in core-neha session.

**Conditions:**
1. Only synthetic demonstration data while on the shared origin.
2. Before any real patient or staff data, move to a dedicated hostname (for example `hms.<domain>`): change the two settings, rebuild the Keycloak image (its relative path is a build option), update DNS and the proxy.
3. Cookies are host-only, `Secure`, `HttpOnly`, `SameSite=Lax` and path-scoped to the base path.
