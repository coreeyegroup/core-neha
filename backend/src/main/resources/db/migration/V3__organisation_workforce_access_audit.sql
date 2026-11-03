-- Slice 1.1: organisation structure, workforce, access control and audit.
-- Feature IDs: ADM-01, ADM-12, ADM-14, ADM-18, HR-01, HR-11..14, IAM-01, IAM-02,
-- IAM-04, IAM-12..15, IAM-18, IAM-21, AUD-01, AUD-02, AUD-07.
-- Every tenant-owned table carries tenant_id and is protected by row-level
-- security on app.tenant_id (set per connection by the application).

------------------------------------------------------------------------------
-- org
------------------------------------------------------------------------------
CREATE TABLE org.tenant (
    id          UUID PRIMARY KEY DEFAULT uuidv7(),
    code        TEXT NOT NULL UNIQUE CHECK (code ~ '^[a-z0-9][a-z0-9-]{1,39}$'),
    name        TEXT NOT NULL CHECK (length(name) BETWEEN 2 AND 200),
    is_demo     BOOLEAN NOT NULL DEFAULT false,
    status      TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    version     INT NOT NULL DEFAULT 0
);

CREATE TABLE org.legal_entity (
    id          UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id   UUID NOT NULL REFERENCES org.tenant (id),
    code        TEXT NOT NULL,
    name        TEXT NOT NULL CHECK (length(name) BETWEEN 2 AND 200),
    status      TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'RETIRED')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    version     INT NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, code)
);

-- Tenant-defined hierarchy levels (for example STATE, CITY, AREA); not a fixed set (ADM-12).
CREATE TABLE org.hierarchy_level (
    id          UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id   UUID NOT NULL REFERENCES org.tenant (id),
    code        TEXT NOT NULL CHECK (code ~ '^[A-Z][A-Z0-9_]{1,29}$'),
    name        TEXT NOT NULL,
    rank        INT  NOT NULL CHECK (rank BETWEEN 1 AND 20),
    status      TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'RETIRED')),
    version     INT NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, code),
    UNIQUE (tenant_id, rank)
);

-- Nodes of the hierarchy. path = '/<root id>/.../<own id>/' for subtree scope checks.
CREATE TABLE org.org_node (
    id          UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id   UUID NOT NULL REFERENCES org.tenant (id),
    level_id    UUID NOT NULL REFERENCES org.hierarchy_level (id),
    parent_id   UUID REFERENCES org.org_node (id),
    code        TEXT NOT NULL,
    name        TEXT NOT NULL,
    path        TEXT NOT NULL,
    status      TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'RETIRED')),
    version     INT NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, code)
);
CREATE INDEX org_node_path_ix ON org.org_node (tenant_id, path text_pattern_ops);

CREATE TABLE org.centre (
    id               UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id        UUID NOT NULL REFERENCES org.tenant (id),
    node_id          UUID NOT NULL REFERENCES org.org_node (id),
    legal_entity_id  UUID NOT NULL REFERENCES org.legal_entity (id),
    code             TEXT NOT NULL,
    name             TEXT NOT NULL CHECK (length(name) BETWEEN 2 AND 200),
    centre_type      TEXT NOT NULL,
    status           TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'RETIRED')),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    version          INT NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, code)
);

-- Group department library (ADM-14).
CREATE TABLE org.department_definition (
    id          UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id   UUID NOT NULL REFERENCES org.tenant (id),
    code        TEXT NOT NULL CHECK (code ~ '^[A-Z][A-Z0-9_]{1,39}$'),
    name        TEXT NOT NULL,
    category    TEXT NOT NULL CHECK (category IN ('CLINICAL', 'CLINICAL_SUPPORT', 'ADMINISTRATIVE', 'OPERATIONS')),
    status      TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'RETIRED')),
    version     INT NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, code)
);

-- Enablement of a department at a centre, effective-dated (ADM-14, ADM-18).
CREATE TABLE org.centre_department (
    id              UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id       UUID NOT NULL REFERENCES org.tenant (id),
    centre_id       UUID NOT NULL REFERENCES org.centre (id),
    department_id   UUID NOT NULL REFERENCES org.department_definition (id),
    local_name      TEXT,
    valid_from      DATE NOT NULL,
    valid_to        DATE,
    version         INT NOT NULL DEFAULT 0,
    CHECK (valid_to IS NULL OR valid_to >= valid_from)
);
CREATE UNIQUE INDEX centre_department_open_ux ON org.centre_department (centre_id, department_id) WHERE valid_to IS NULL;

------------------------------------------------------------------------------
-- workforce
------------------------------------------------------------------------------
CREATE TABLE workforce.position_definition (
    id          UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id   UUID NOT NULL REFERENCES org.tenant (id),
    code        TEXT NOT NULL CHECK (code ~ '^[A-Z][A-Z0-9_]{1,39}$'),
    title       TEXT NOT NULL,
    category    TEXT NOT NULL CHECK (category IN ('CLINICAL', 'NURSING', 'ALLIED_HEALTH', 'TECHNICAL', 'ADMINISTRATIVE', 'MANAGEMENT', 'SUPPORT')),
    grade       TEXT,
    status      TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'RETIRED')),
    version     INT NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, code)
);

-- A position established at a centre, optionally within an enabled department (HR-12).
CREATE TABLE workforce.establishment (
    id                    UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id             UUID NOT NULL REFERENCES org.tenant (id),
    position_id           UUID NOT NULL REFERENCES workforce.position_definition (id),
    centre_id             UUID NOT NULL REFERENCES org.centre (id),
    centre_department_id  UUID REFERENCES org.centre_department (id),
    approved_headcount    INT NOT NULL CHECK (approved_headcount BETWEEN 1 AND 10000),
    valid_from            DATE NOT NULL,
    valid_to              DATE,
    version               INT NOT NULL DEFAULT 0,
    CHECK (valid_to IS NULL OR valid_to >= valid_from)
);

CREATE TABLE workforce.person (
    id            UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id     UUID NOT NULL REFERENCES org.tenant (id),
    given_name    TEXT NOT NULL,
    family_name   TEXT NOT NULL,
    email         TEXT NOT NULL,
    is_demo       BOOLEAN NOT NULL DEFAULT false,
    status        TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    version       INT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX person_email_ux ON workforce.person (tenant_id, lower(email));

-- A person holding an established position, with independent dates (HR-13).
CREATE TABLE workforce.posting (
    id                UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id         UUID NOT NULL REFERENCES org.tenant (id),
    person_id         UUID NOT NULL REFERENCES workforce.person (id),
    establishment_id  UUID NOT NULL REFERENCES workforce.establishment (id),
    fte               NUMERIC(3,2) NOT NULL DEFAULT 1.00 CHECK (fte > 0 AND fte <= 1),
    valid_from        DATE NOT NULL,
    valid_to          DATE,
    version           INT NOT NULL DEFAULT 0,
    CHECK (valid_to IS NULL OR valid_to >= valid_from)
);

------------------------------------------------------------------------------
-- iam
------------------------------------------------------------------------------
-- Permission catalogue; rows must match the code enum (checked by a test).
CREATE TABLE iam.permission (
    code         TEXT PRIMARY KEY,
    module       TEXT NOT NULL,
    description  TEXT NOT NULL,
    tenant_only  BOOLEAN NOT NULL
);

CREATE TABLE iam.user_account (
    id              UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id       UUID NOT NULL REFERENCES org.tenant (id),
    person_id       UUID NOT NULL REFERENCES workforce.person (id),
    username        TEXT NOT NULL,
    status          TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DISABLED')),
    access_version  INT  NOT NULL DEFAULT 0,
    disabled_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         INT NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, username),
    UNIQUE (person_id)
);

-- Login lookup across tenants: identity-provider subject -> user. No RLS (used before the tenant is known).
CREATE TABLE iam.login_identity (
    subject     TEXT PRIMARY KEY,
    user_id     UUID NOT NULL UNIQUE REFERENCES iam.user_account (id),
    tenant_id   UUID NOT NULL REFERENCES org.tenant (id)
);

CREATE TABLE iam.role (
    id           UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id    UUID NOT NULL REFERENCES org.tenant (id),
    code         TEXT NOT NULL CHECK (code ~ '^[A-Z][A-Z0-9_]{1,49}$'),
    name         TEXT NOT NULL,
    description  TEXT,
    is_system    BOOLEAN NOT NULL DEFAULT false,
    status       TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'RETIRED')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    version      INT NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, code)
);

CREATE TABLE iam.role_version (
    id             UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id      UUID NOT NULL REFERENCES org.tenant (id),
    role_id        UUID NOT NULL REFERENCES iam.role (id),
    version_no     INT  NOT NULL,
    status         TEXT NOT NULL CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'PUBLISHED', 'SUPERSEDED', 'REJECTED')),
    change_note    TEXT,
    created_by     UUID REFERENCES iam.user_account (id),
    submitted_by   UUID REFERENCES iam.user_account (id),
    submitted_at   TIMESTAMPTZ,
    decided_by     UUID REFERENCES iam.user_account (id),
    decided_at     TIMESTAMPTZ,
    decision_note  TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    version        INT NOT NULL DEFAULT 0,
    UNIQUE (role_id, version_no),
    -- Independent approval: the decider is never the submitter (IAM-12, IAM-18).
    CHECK (decided_by IS NULL OR submitted_by IS NULL OR decided_by <> submitted_by)
);
CREATE UNIQUE INDEX role_version_published_ux ON iam.role_version (role_id) WHERE status = 'PUBLISHED';
CREATE UNIQUE INDEX role_version_open_ux ON iam.role_version (role_id) WHERE status IN ('DRAFT', 'PENDING_APPROVAL');

CREATE TABLE iam.role_version_grant (
    role_version_id  UUID NOT NULL REFERENCES iam.role_version (id) ON DELETE CASCADE,
    permission_code  TEXT NOT NULL REFERENCES iam.permission (code),
    tenant_id        UUID NOT NULL REFERENCES org.tenant (id),
    PRIMARY KEY (role_version_id, permission_code)
);

-- Grants of access, and grants of the authority to give access. Both follow the
-- same lifecycle and need an independent approver unless created by bootstrap.
CREATE TABLE iam.role_assignment (
    id              UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id       UUID NOT NULL REFERENCES org.tenant (id),
    kind            TEXT NOT NULL CHECK (kind IN ('ACCESS', 'AUTHORITY')),
    user_id         UUID NOT NULL REFERENCES iam.user_account (id),
    -- For AUTHORITY rows a NULL role means authority over every role.
    role_id         UUID REFERENCES iam.role (id),
    scope_type      TEXT NOT NULL CHECK (scope_type IN ('TENANT', 'ORG_NODE', 'CENTRE')),
    scope_id        UUID NOT NULL,
    posting_id      UUID REFERENCES workforce.posting (id),
    valid_from      DATE NOT NULL,
    valid_to        DATE,
    status          TEXT NOT NULL CHECK (status IN ('PENDING_APPROVAL', 'ACTIVE', 'REJECTED', 'REVOKED')),
    reason          TEXT NOT NULL,
    requested_by    UUID REFERENCES iam.user_account (id),
    requested_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_by      UUID REFERENCES iam.user_account (id),
    decided_at      TIMESTAMPTZ,
    decision_note   TEXT,
    via_bootstrap   BOOLEAN NOT NULL DEFAULT false,
    version         INT NOT NULL DEFAULT 0,
    CHECK (valid_to IS NULL OR valid_to >= valid_from),
    -- No self-approval and no approving one's own access (IAM-02, IAM-18).
    CHECK (decided_by IS NULL OR (decided_by <> requested_by AND decided_by <> user_id)),
    CHECK (requested_by IS NOT NULL OR via_bootstrap),
    CHECK (kind = 'AUTHORITY' OR role_id IS NOT NULL)
);
CREATE INDEX role_assignment_user_ix ON iam.role_assignment (user_id) WHERE status = 'ACTIVE';

-- Responsibilities: accountability, never access (IAM-15).
CREATE TABLE iam.responsibility_type (
    id          UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id   UUID NOT NULL REFERENCES org.tenant (id),
    code        TEXT NOT NULL CHECK (code ~ '^[A-Z][A-Z0-9_]{1,49}$'),
    name        TEXT NOT NULL,
    status      TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'RETIRED')),
    version     INT NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, code)
);

CREATE TABLE iam.responsibility_assignment (
    id                   UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id            UUID NOT NULL REFERENCES org.tenant (id),
    person_id            UUID NOT NULL REFERENCES workforce.person (id),
    responsibility_id    UUID NOT NULL REFERENCES iam.responsibility_type (id),
    scope_type           TEXT NOT NULL CHECK (scope_type IN ('TENANT', 'ORG_NODE', 'CENTRE')),
    scope_id             UUID NOT NULL,
    valid_from           DATE NOT NULL,
    valid_to             DATE,
    assigned_by          UUID NOT NULL REFERENCES iam.user_account (id),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    version              INT NOT NULL DEFAULT 0,
    CHECK (valid_to IS NULL OR valid_to >= valid_from)
);

------------------------------------------------------------------------------
-- audit (insert and read only for the runtime role; see V1)
------------------------------------------------------------------------------
CREATE TABLE audit.audit_event (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id        UUID,
    occurred_at      TIMESTAMPTZ NOT NULL,
    recorded_at      TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    actor_user_id    UUID,
    actor_label      TEXT NOT NULL,
    action           TEXT NOT NULL,
    object_type      TEXT NOT NULL,
    object_id        TEXT,
    scope_type       TEXT,
    scope_id         UUID,
    outcome          TEXT NOT NULL CHECK (outcome IN ('SUCCESS', 'DENIED', 'FAILED')),
    reason           TEXT,
    request_id       TEXT,
    details          JSONB
);
CREATE INDEX audit_event_tenant_time_ix ON audit.audit_event (tenant_id, occurred_at DESC);
CREATE INDEX audit_event_object_ix ON audit.audit_event (tenant_id, object_type, object_id);
CREATE INDEX audit_event_actor_ix ON audit.audit_event (tenant_id, actor_user_id, occurred_at DESC);

------------------------------------------------------------------------------
-- Row-level security on tenant_id (ADR-0007). The owner role bypasses RLS;
-- the runtime role core_neha_app does not.
------------------------------------------------------------------------------
DO $$
DECLARE t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'org.legal_entity', 'org.hierarchy_level', 'org.org_node', 'org.centre',
        'org.department_definition', 'org.centre_department',
        'workforce.position_definition', 'workforce.establishment', 'workforce.person', 'workforce.posting',
        'iam.user_account', 'iam.role', 'iam.role_version', 'iam.role_version_grant', 'iam.role_assignment',
        'iam.responsibility_type', 'iam.responsibility_assignment']
    LOOP
        EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON %s USING (tenant_id = nullif(current_setting(''app.tenant_id'', true), '''')::uuid) '
            || 'WITH CHECK (tenant_id = nullif(current_setting(''app.tenant_id'', true), '''')::uuid)', t);
    END LOOP;
END $$;

-- Audit: readable within the tenant; events without a tenant (for example a
-- login by an unknown identity) can be written but are read only by operators.
ALTER TABLE audit.audit_event ENABLE ROW LEVEL SECURITY;
CREATE POLICY audit_read ON audit.audit_event FOR SELECT
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY audit_insert ON audit.audit_event FOR INSERT
    WITH CHECK (tenant_id IS NULL OR tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);

-- The tenant row itself: visible only to its own context.
ALTER TABLE org.tenant ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_self ON org.tenant
    USING (id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (id = nullif(current_setting('app.tenant_id', true), '')::uuid);

------------------------------------------------------------------------------
-- Permission catalogue for slice 1.1
------------------------------------------------------------------------------
INSERT INTO iam.permission (code, module, description, tenant_only) VALUES
  ('org.structure.read',        'org',       'View hierarchy, legal entities and department library', false),
  ('org.structure.manage',      'org',       'Define hierarchy levels, nodes, legal entities and departments', true),
  ('org.centre.create',         'org',       'Create a centre under a hierarchy node', false),
  ('org.centre.read',           'org',       'View centres and their configuration', false),
  ('org.centre.configure',      'org',       'Enable or retire departments at a centre', false),
  ('workforce.position.define', 'workforce', 'Define positions in the group library', true),
  ('workforce.read',            'workforce', 'View positions, establishments, people and postings', false),
  ('workforce.establish',       'workforce', 'Establish positions at centres and departments', false),
  ('workforce.person.manage',   'workforce', 'Create people and manage their postings', false),
  ('iam.role.read',             'iam',       'View roles, role versions and the permission catalogue', true),
  ('iam.role.draft',            'iam',       'Draft and submit role versions', true),
  ('iam.role.approve',          'iam',       'Approve or reject submitted role versions', true),
  ('iam.user.manage',           'iam',       'Create, disable and enable user accounts', false),
  ('iam.assignment.read',       'iam',       'View assignments, responsibilities and effective access', false),
  ('iam.assignment.request',    'iam',       'Request role assignments within assignment authority', false),
  ('iam.assignment.approve',    'iam',       'Approve or reject requested assignments', false),
  ('iam.assignment.revoke',     'iam',       'Revoke active assignments', false),
  ('iam.responsibility.manage', 'iam',       'Define and assign responsibilities', false),
  ('audit.read',                'audit',     'Search the audit trail', false);
