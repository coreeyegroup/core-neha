# ADR-0007: Tenant and centre isolation in the database

- Status: Accepted (proposed detail)
- Date: 2026-11-04

Every business table carries `tenant_id`; centre-owned tables also carry `centre_id`. The runtime database role `core_neha_app` does not own tables, so PostgreSQL row-level security on `tenant_id` applies to it (set per transaction with `SET LOCAL`). Centre scope is enforced in the application's permission evaluator. Migrations run as `core_neha_owner`.
