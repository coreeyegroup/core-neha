# ADR-0009: First-administrator bootstrap

- Status: Accepted (proposed detail)
- Date: 2026-11-04

A tenant's first administrator is created by an operator command on the server, not through the web interface. The command requires a one-time bootstrap code generated into the secrets folder, creates the Keycloak user (with a temporary password and required TOTP setup) and the application user with a bootstrap assignment limited to configuring access governance. The command refuses to run again for a tenant that already has an administrator. Everything it does is audited. After bootstrap, all assignments follow normal assignment-authority and approval rules, including for the first administrator.
