# ADR-0008: Audit integrity

- Status: Accepted
- Date: 2026-11-04

Protected changes and their audit events are written in the same database transaction: if the audit write fails, the change rolls back. Denied attempts are audited in a separate transaction so the denial is recorded even though the request fails. Audit rows are insert-only for the runtime role (no update or delete grant). Each event records actor, posting/assignment used, action, object, scope (tenant, centre), outcome, reason, request ID and time provenance.
