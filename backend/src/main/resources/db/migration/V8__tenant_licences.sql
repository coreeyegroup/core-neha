-- Licences: each tenant is a separate hospital chain (its own legal entities, management,
-- structure, staff and patients, isolated by row-level security). The software vendor
-- licenses each chain for a set of modules, a validity period and a number of centres.
-- A permission whose module is not licensed is never granted, whatever roles say.

CREATE TABLE org.tenant_licence (
    tenant_id      UUID PRIMARY KEY REFERENCES org.tenant (id),
    licence_no     TEXT NOT NULL UNIQUE CHECK (length(licence_no) BETWEEN 3 AND 60),
    licensee_name  TEXT NOT NULL CHECK (length(licensee_name) BETWEEN 2 AND 200),
    plan           TEXT NOT NULL DEFAULT 'STANDARD' CHECK (plan IN ('DEMO', 'CLINIC', 'STANDARD', 'ENTERPRISE')),
    valid_from     DATE NOT NULL,
    valid_to       DATE,
    max_centres    INT CHECK (max_centres IS NULL OR max_centres > 0),
    status         TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    notes          TEXT,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    version        INT NOT NULL DEFAULT 0,
    CHECK (valid_to IS NULL OR valid_to >= valid_from)
);

CREATE TABLE org.tenant_module (
    tenant_id    UUID NOT NULL REFERENCES org.tenant (id),
    module_code  TEXT NOT NULL CHECK (module_code IN ('EYE_CARE', 'BILLING', 'INVENTORY', 'PHARMACY', 'OPTICAL', 'OPTICAL_LAB',
                                                      'LABORATORY', 'RADIOLOGY')),
    enabled_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, module_code)
);

DO $$
DECLARE t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY['org.tenant_licence', 'org.tenant_module'] LOOP
        EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON %s USING (tenant_id = nullif(current_setting(''app.tenant_id'', true), '''')::uuid) '
            'WITH CHECK (tenant_id = nullif(current_setting(''app.tenant_id'', true), '''')::uuid)', t);
    END LOOP;
END $$;

-- The module a permission belongs to; CORE (organisation, people, access, audit, patients, OPD queue) is always licensed.
CREATE FUNCTION org.permission_module(p_code TEXT) RETURNS TEXT
    LANGUAGE sql IMMUTABLE SET search_path = pg_catalog
AS $$
    SELECT CASE split_part(p_code, '.', 1)
        WHEN 'eye' THEN 'EYE_CARE'
        WHEN 'billing' THEN 'BILLING'
        WHEN 'inventory' THEN 'INVENTORY'
        WHEN 'pharmacy' THEN 'PHARMACY'
        WHEN 'optical' THEN 'OPTICAL'
        WHEN 'opticallab' THEN 'OPTICAL_LAB'
        WHEN 'lab' THEN 'LABORATORY'
        WHEN 'radiology' THEN 'RADIOLOGY'
        ELSE 'CORE' END
$$;

-- True if the tenant's licence is active today and covers the permission's module.
CREATE FUNCTION org.permission_licensed(p_tenant UUID, p_code TEXT) RETURNS BOOLEAN
    LANGUAGE sql STABLE SET search_path = pg_catalog
AS $$
    SELECT org.permission_module(p_code) = 'CORE'
        OR EXISTS (SELECT 1 FROM org.tenant_licence l
                   JOIN org.tenant_module m ON m.tenant_id = l.tenant_id AND m.module_code = org.permission_module(p_code)
                   WHERE l.tenant_id = p_tenant AND l.status = 'ACTIVE'
                     AND l.valid_from <= current_date AND (l.valid_to IS NULL OR l.valid_to >= current_date))
$$;

-- The vendor's view of all tenants (no tenant data beyond the licence); used by the server console only.
CREATE FUNCTION org.platform_tenants()
    RETURNS TABLE (tenant_id UUID, code TEXT, name TEXT, is_demo BOOLEAN, status TEXT, licence_no TEXT, licensee_name TEXT,
                   plan TEXT, valid_from DATE, valid_to DATE, max_centres INT, licence_status TEXT, modules TEXT, centres BIGINT,
                   users BIGINT)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog
AS $$
    SELECT t.id, t.code, t.name, t.is_demo, t.status, l.licence_no, l.licensee_name, l.plan, l.valid_from, l.valid_to,
           l.max_centres, l.status,
           (SELECT string_agg(m.module_code, ',' ORDER BY m.module_code) FROM org.tenant_module m WHERE m.tenant_id = t.id),
           (SELECT count(*) FROM org.centre c WHERE c.tenant_id = t.id),
           (SELECT count(*) FROM iam.user_account u WHERE u.tenant_id = t.id)
    FROM org.tenant t LEFT JOIN org.tenant_licence l ON l.tenant_id = t.id
    ORDER BY t.created_at
$$;
REVOKE ALL ON FUNCTION org.platform_tenants() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION org.platform_tenants() TO core_neha_app;

-- Existing tenants keep everything they had: a licence with every module.
INSERT INTO org.tenant_licence (tenant_id, licence_no, licensee_name, plan, valid_from, notes)
SELECT id, 'LIC-' || upper(code), name, CASE WHEN is_demo THEN 'DEMO' ELSE 'STANDARD' END, current_date,
       'Created by migration V8 for a tenant that existed before licensing'
FROM org.tenant;
INSERT INTO org.tenant_module (tenant_id, module_code)
SELECT t.id, m FROM org.tenant t
CROSS JOIN unnest(ARRAY['EYE_CARE', 'BILLING', 'INVENTORY', 'PHARMACY', 'OPTICAL', 'OPTICAL_LAB', 'LABORATORY', 'RADIOLOGY']) AS m;
