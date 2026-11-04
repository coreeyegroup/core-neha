-- Employee numbers are login ids, and the identity provider's realm is shared by every
-- tenant, so a number must be unique across tenants, not only within one. One sequence
-- for all tenants replaces the per-tenant counter; numbers already issued are kept.
CREATE SEQUENCE workforce.employee_no_seq START WITH 10001 MINVALUE 10001;
SELECT setval('workforce.employee_no_seq',
              greatest(10001, coalesce((SELECT max(substring(employee_no FROM 2)::bigint) FROM workforce.person), 10000) + 1),
              false);

DROP INDEX workforce.person_employee_no_ux;
CREATE UNIQUE INDEX person_employee_no_ux ON workforce.person (employee_no);

DROP TABLE workforce.employee_counter;
