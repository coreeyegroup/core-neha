# ADR-0012: Public website, role-filtered help, per-account one-time code

Status: accepted, 2026-11-04

## Decisions

1. **The public website is served at the base path** (`/core-neha/`, later the hospital's domain root) and the **staff application moves to `/staff/`**. The website is static HTML/CSS with a strict same-origin CSP, no tracking and no cookies; contact details come from one file (`assets/site-config.js`) and empty values are hidden rather than invented. The registered Keycloak post-logout address `<base>/signed-out` is redirected by the gateway to `<base>/staff/signed-out`, so the realm (already imported on servers) does not change.
2. **Help is served by the backend and filtered by audience.** Each chapter declares `everyone`, `administrator` or permission codes; the developer guide is for group-wide administrators only. Users never receive chapters outside their work, not even in the JavaScript bundle.
3. **One-time code per account**, on by default. Only a group-wide user administrator can turn it off or on for another account, with a reason, audited. The Keycloak account console is closed so users cannot remove their own authenticator.
4. From the architecture review: approvers cannot widen their own access through a role they hold; bills are hidden from the visit record without billing access; bill mutations are serialised; contract drift fails CI.

## Consequences

- Turning the code off weakens sign-in for that account; management reviews such accounts quarterly.
- Adding permissions to the system administrator role needs an approver who does not hold it.
