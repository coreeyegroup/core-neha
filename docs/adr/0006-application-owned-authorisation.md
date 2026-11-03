# ADR-0006: Application-owned authorisation

- Status: Accepted
- Date: 2026-11-04

Keycloak answers "who is this?"; the application answers "may they do this, here?". Permissions are a catalogue defined in code by each module (for example `org.centre.create`). Role versions grant permissions with scope; assignments bind a person to a role version for a scope and dates. Every endpoint declares its permission and scope type; a build test fails if one does not. There are no role-name checks and no "admin can do everything" path. The first administrator is created by a one-time, audited bootstrap (ADR-0009).
