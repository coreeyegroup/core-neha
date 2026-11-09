# ADR-0013: Hospital chains as tenants, licensed by the vendor

Status: accepted (2026-11-09)

## Context
The software is provided to many unrelated hospital chains (for example three chains, each with
branches in several states, cities and areas). Each chain has its own licence, management,
structure, staff, logins and patients, and must never see another chain's data. All chains run
the same product, with configuration rather than code branches for their differences.

## Decision
- **One tenant per chain.** Everything a chain owns (legal entities, hierarchy, centres,
  departments, positions, people, logins, roles, patients, visits, bills, audit) carries its
  `tenant_id`, and PostgreSQL row-level security restricts every query to the tenant of the
  signed-in user (ADR-0003). A person who works for two chains has two separate accounts.
- **Login ids are employee numbers unique across all chains** (V7), because the identity provider
  realm is shared; a chain that needs its own identity provider or its own database can be given
  a dedicated installation of the same software.
- **Licence per chain** (V8): licence number, licensee, plan, validity, maximum centres and the
  licensed modules (EYE_CARE, BILLING, INVENTORY, PHARMACY, OPTICAL, OPTICAL_LAB, LABORATORY,
  RADIOLOGY). Core functions (organisation, people, access, audit, patients, OPD queue) are always
  included. The access evaluator grants a permission only if its module is licensed today and the
  chain is active; a suspended chain has no access at all. Centre creation stops at the limit.
- **The vendor manages licences only from the server console** (`ops/tenant.sh`): no web route
  changes a licence, so no hospital user can widen their own. The vendor's list shows licence and
  size only, never patient or staff data. Every vendor change is written to that chain's audit trail.
- Each chain's staff can read their own licence (Organisation → Licence).

## Consequences
- Per-chain customisation is configuration: departments per centre, positions, roles, price lists,
  and licensed modules. Branding per chain and a web console for the vendor are later work.
- Tests (`MultiTenantTest`) prove isolation across two chains, licence switching, the centre limit
  and suspension.
