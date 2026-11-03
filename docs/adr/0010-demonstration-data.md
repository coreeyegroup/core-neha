# ADR-0010: Demonstration data separated from production onboarding

- Status: Accepted
- Date: 2026-11-04

Synthetic seed data (one demonstration tenant with 20 centres of different capability combinations) is loaded only by an explicit `demo` command and only when `CORE_NEHA_ENVIRONMENT=demo`. It is never part of Flyway migrations. Demonstration records and identities are labelled as such in the interface. Seed user passwords are generated on the server into the secrets folder; none are written in the repository.
