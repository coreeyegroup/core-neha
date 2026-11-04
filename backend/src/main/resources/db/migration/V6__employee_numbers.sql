-- A permanent employee number per person (for example E10001). It is the person's login
-- id by default and never changes when their position, department or centre changes;
-- positions are postings, shown as tags.
CREATE TABLE workforce.employee_counter (
    tenant_id   UUID PRIMARY KEY REFERENCES org.tenant (id),
    prefix      TEXT NOT NULL DEFAULT 'E',
    last_value  BIGINT NOT NULL DEFAULT 10000
);
ALTER TABLE workforce.employee_counter ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON workforce.employee_counter
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);

ALTER TABLE workforce.person ADD COLUMN employee_no TEXT;

-- Number existing people in order of creation.
UPDATE workforce.person p SET employee_no = 'E' || (10000 + x.rn)
FROM (SELECT id, row_number() OVER (PARTITION BY tenant_id ORDER BY created_at, id) AS rn FROM workforce.person) x
WHERE x.id = p.id;
INSERT INTO workforce.employee_counter (tenant_id, last_value)
SELECT t.id, 10000 + (SELECT count(*) FROM workforce.person p WHERE p.tenant_id = t.id) FROM org.tenant t;

ALTER TABLE workforce.person ALTER COLUMN employee_no SET NOT NULL;
CREATE UNIQUE INDEX person_employee_no_ux ON workforce.person (tenant_id, employee_no);
