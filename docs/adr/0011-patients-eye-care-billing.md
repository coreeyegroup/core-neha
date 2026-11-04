# ADR-0011: Patients, the eye-care visit and billing (slice 2.1)

Status: accepted, 2026-11-04

## Context

The first clinical slice has to show one centre working end to end: front office,
optometry, consultation, surgery counselling, the operation theatre and accounts,
each signing in to its own work, on one patient record.

## Decisions

1. **Three new modules.** `patient` (one identity, UHID), `eyecare` (appointment,
   visit, optometry, consultation, surgery) and `billing` (price list, bills, payments,
   day close). Dependencies point one way: eyecare -> billing -> patient. Billing refers
   to a visit or surgery by id only and never reads clinical content.
2. **One UHID per patient for the group** (`NH` + 8 digits, per tenant counter). A
   patient is registered at a centre; staff with `patient.read` at any centre can find
   them. Registration warns about likely duplicates (same mobile and given name, or same
   name and date of birth) and needs an explicit confirmation to proceed.
3. **One visit, fixed stages.** OPTOMETRY -> CONSULTATION -> COUNSELLING (only when
   surgery is advised) -> CHECKOUT -> COMPLETED. Each department completes only its own
   stage; every department reads and writes the same visit, nothing is copied.
4. **Clinical content is separate from the visit's progress.** Front office and accounts
   see stages, waiting times and bills; findings, diagnosis and surgery details need
   `eye.clinical.read`. Opening a clinical record is audited.
5. **Segregation of duties in code and in the database.** A discount is applied only
   after someone other than the requester approves it; no payment is taken while a
   discount waits; a bill is cancelled only by an approver and only before payment;
   a day close is approved by someone other than the submitter. A surgery cannot be
   ready without consent, site marking and fitness, nor completed without anaesthesia
   and the operative note (CHECK constraints).
6. **Administrators do not see patients or money.** The bootstrap system administrator
   role now grants only administration permissions (org, workforce, iam, audit).
7. **Business dates are IST days** (`BusinessTime`), not UTC.
8. **Read models may join other modules' tables for display names** (patient name,
   staff name). Writes and rules stay inside the owning module.

## Consequences

- Insurance pre-authorisation, pharmacy dispensing, optical orders, refunds and credit
  notes, GST invoices with HSN/SAC codes and ABDM linking come in later slices.
- Amending a completed consultation is not possible yet (addendum workflow later).
