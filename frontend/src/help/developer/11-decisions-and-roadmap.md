# Decisions and roadmap
> The architecture decision records, known limitations, and what comes next.

## Architecture decision records (`docs/adr/`)

| ADR | Decision |
|---|---|
| 0001 | Modular monolith with Spring Modulith; split only when there is a real reason |
| 0002 | Every version pinned; npm packages at least 14 days old; LTS lines only |
| 0003 | Deployment on core-node with Docker Compose, isolated from existing services |
| 0004 | Public address `https://coreeye.in/core-neha` as configuration (temporary, shared origin) |
| 0005 | Keycloak OIDC with a backend-for-frontend session; no tokens in the browser |
| 0006 | Application-owned authorisation: permissions, scoped assignments, independent approval |
| 0007 | Tenant isolation by row-level security; runtime role without DDL |
| 0008 | Audit in the same transaction; append-only for the runtime role |
| 0009 | Bootstrap creates exactly two administrators |
| 0010 | Demonstration data separated from production onboarding |
| 0011 | Patients (UHID), the eye-care visit, billing; administrators do not see patients or money |

Write a new ADR for any decision that would surprise the next developer: a new dependency, a new module, a change of pattern.

## Known limitations

- Completed consultations cannot be amended (addendum workflow planned).
- No patient merge for duplicate UHIDs.
- No refunds or credit notes; no GST tax invoice format with HSN/SAC; GST rate per price item only.
- Insurance is a payment mode only: no pre-authorisation, enhancement or claim tracking yet.
- No pharmacy, optical orders, inventory or purchase.
- Reports are per centre; no city, state or group roll-up yet.
- Audit table not yet partitioned; no retention disposal.
- Password and authenticator reset through the identity console only.
- Frontend is one bundle; route-level code splitting later.

## Roadmap (from the requirements phases)

1. Clinical depth: consultation addenda, templates, prescriptions printout, investigations (OCT, biometry) orders and results.
2. Insurance/TPA: pre-authorisation, enhancement, discharge approval, claims, settlement.
3. Pharmacy and optical: dispensing from prescriptions, spectacle orders to the optical lab, stock.
4. Inventory and purchase: indents, purchase orders, goods receipt, batch and expiry.
5. Finance: refunds, credit notes, GST invoices, export to the accounting system.
6. Group reporting: dashboards by centre, area, city, state and group.
7. Patient-facing: appointment booking, reports and bills on a portal, SMS/WhatsApp notifications; ABHA linking.
8. Operations: monitoring and alerting, off-server backups, the move to the permanent server and domain.
