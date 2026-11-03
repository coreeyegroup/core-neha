-- Baseline: one schema per module and the privilege model (ADR-0007, ADR-0008).
-- Runs as core_neha_owner. core_neha_app is the runtime role: data access only,
-- no DDL; audit tables are insert-and-read only for it.

CREATE SCHEMA org;
CREATE SCHEMA iam;
CREATE SCHEMA workforce;
CREATE SCHEMA audit;
CREATE SCHEMA session;

GRANT USAGE ON SCHEMA org, iam, workforce, audit, session TO core_neha_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA org, iam, workforce, session
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO core_neha_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA org, iam, workforce, session
    GRANT USAGE, SELECT ON SEQUENCES TO core_neha_app;

-- Audit: no UPDATE or DELETE for the runtime role.
ALTER DEFAULT PRIVILEGES IN SCHEMA audit
    GRANT SELECT, INSERT ON TABLES TO core_neha_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA audit
    GRANT USAGE, SELECT ON SEQUENCES TO core_neha_app;
