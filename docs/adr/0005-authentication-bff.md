# ADR-0005: Authentication: Keycloak OIDC with a backend-for-frontend session

- Status: Accepted (proposed detail)
- Date: 2026-11-04

Keycloak 26.8 (realm `core-neha`) authenticates staff with password + TOTP (MFA required by default), brute-force protection and a strong password policy. The Spring Boot backend is the OIDC **confidential client** (authorisation code + PKCE). The browser receives only an `HttpOnly` session cookie and a CSRF token; it never sees access or refresh tokens.

Sessions are stored server-side in PostgreSQL. Idle timeout 30 minutes, absolute 12 hours (Phase 6). Logout ends the application session and the Keycloak session (RP-initiated logout; back-channel logout endpoint registered).

**Revocation (requirement: at most 5 minutes):** disabling an account or removing access (a) deletes the user's server sessions immediately, (b) disables the Keycloak user, and (c) every request re-checks account status and the current access version with a cache of at most 60 seconds. Access tokens live 5 minutes.

**Rejected:** tokens in the browser (single-page OIDC client): weaker on a shared origin and harder to revoke.
