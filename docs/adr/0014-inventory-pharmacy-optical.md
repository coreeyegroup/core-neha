# ADR-0014: One stock ledger for pharmacy, optical shop and optical lab

Status: accepted (2026-11-09)

## Decision
- **Inventory owns stock.** Items (per chain), stores (per centre), batches with expiry, and an
  append-only movement ledger. Pharmacy and optical change stock only through `StockLedger`
  (take first-expiry-first-out, take a named batch, put back), always in the caller's transaction.
  Expired batches are never taken. Stock on hand always equals the sum of its movements.
- **Segregation of duties:** adjustments need a second person (DB CHECK and service rule); optical
  QC cannot be done by the fitter (DB CHECK and service rule).
- **Pharmacy:** Schedule H, H1 and X drugs are dispensed only against a prescription in the system,
  as prescribed, within 30 days; every sale line records its batch (traceability).
- **Optical:** the prescription is copied from the optometry record when the order is placed and
  never changed; a lab job carries a frozen JSON specification; QC enforces power and axis
  tolerances; delivery only when the bill is paid; cancel returns stock and cancels the unpaid bill.
- **Billing stays the single place for money.** Pharmacy and optical raise bills through
  `BillingDirectory.raiseSourcedBill`; those lines carry their source and cannot be removed at the
  billing counter. MRP includes GST; the price before tax is MRP / (1 + rate).
- Modules are licensed separately (INVENTORY, PHARMACY, OPTICAL, OPTICAL_LAB; ADR-0013).

## Not yet
Purchase orders and supplier master, refunds and credit notes, GST invoices with HSN summaries,
narcotic (NDPS) registers, contact-lens fitting records, inter-centre transfer acknowledgement.
