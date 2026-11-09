-- Laboratory and imaging (radiology): test and procedure catalogues per chain, diagnostic orders
-- with an accession number, sample collection and receipt, results with reference ranges and
-- critical flags, validation by a second person, imaging schedule, acquisition linked to the DICOM
-- study in the image archive (PACS), report and signature.

CREATE SCHEMA diagnostics;
GRANT USAGE ON SCHEMA diagnostics TO core_neha_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA diagnostics GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO core_neha_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA diagnostics GRANT USAGE, SELECT ON SEQUENCES TO core_neha_app;

CREATE TABLE diagnostics.lab_test (
    id           UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id    UUID NOT NULL REFERENCES org.tenant (id),
    code         TEXT NOT NULL CHECK (code ~ '^[A-Z0-9][A-Z0-9_-]{1,39}$'),
    name         TEXT NOT NULL CHECK (length(name) BETWEEN 2 AND 200),
    section      TEXT NOT NULL CHECK (section IN ('BIOCHEMISTRY', 'HAEMATOLOGY', 'CLINICAL_PATHOLOGY', 'MICROBIOLOGY', 'SEROLOGY', 'HISTOPATHOLOGY')),
    sample_type  TEXT NOT NULL CHECK (sample_type IN ('BLOOD', 'SERUM', 'PLASMA', 'URINE', 'SWAB', 'TISSUE', 'TEAR', 'OTHER')),
    container    TEXT,
    loinc_code   TEXT,
    price        NUMERIC(12,2) NOT NULL CHECK (price >= 0),
    tat_hours    INT NOT NULL DEFAULT 24 CHECK (tat_hours BETWEEN 1 AND 720),
    status       TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'RETIRED')),
    version      INT NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, code)
);

CREATE TABLE diagnostics.lab_parameter (
    id             UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id      UUID NOT NULL REFERENCES org.tenant (id),
    test_id        UUID NOT NULL REFERENCES diagnostics.lab_test (id),
    code           TEXT NOT NULL,
    name           TEXT NOT NULL,
    unit           TEXT,
    loinc_code     TEXT,
    value_type     TEXT NOT NULL CHECK (value_type IN ('NUMERIC', 'TEXT')),
    ref_low        NUMERIC(12,3),
    ref_high       NUMERIC(12,3),
    critical_low   NUMERIC(12,3),
    critical_high  NUMERIC(12,3),
    ref_text       TEXT,
    sort_order     INT NOT NULL DEFAULT 0,
    UNIQUE (test_id, code)
);

CREATE TABLE diagnostics.imaging_procedure (
    id           UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id    UUID NOT NULL REFERENCES org.tenant (id),
    code         TEXT NOT NULL CHECK (code ~ '^[A-Z0-9][A-Z0-9_-]{1,39}$'),
    name         TEXT NOT NULL CHECK (length(name) BETWEEN 2 AND 200),
    modality     TEXT NOT NULL CHECK (modality IN ('CR', 'DX', 'CT', 'MR', 'US', 'OPT', 'OP', 'OPV', 'OT')),
    body_part    TEXT,
    preparation  TEXT,
    price        NUMERIC(12,2) NOT NULL CHECK (price >= 0),
    status       TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'RETIRED')),
    version      INT NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, code)
);

CREATE TABLE diagnostics.diagnostic_order (
    id                  UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id           UUID NOT NULL REFERENCES org.tenant (id),
    centre_id           UUID NOT NULL REFERENCES org.centre (id),
    request_no          TEXT NOT NULL,
    accession_no        TEXT NOT NULL,
    patient_id          UUID NOT NULL REFERENCES patient.patient (id),
    visit_ref           UUID,
    kind                TEXT NOT NULL CHECK (kind IN ('LAB', 'IMAGING')),
    lab_test_id         UUID REFERENCES diagnostics.lab_test (id),
    procedure_id        UUID REFERENCES diagnostics.imaging_procedure (id),
    priority            TEXT NOT NULL DEFAULT 'ROUTINE' CHECK (priority IN ('ROUTINE', 'URGENT', 'STAT')),
    clinical_notes      TEXT,
    status              TEXT NOT NULL DEFAULT 'ORDERED' CHECK (status IN ('ORDERED', 'COLLECTED', 'IN_LAB', 'RESULTED', 'VALIDATED',
                                                                         'SCHEDULED', 'ACQUIRED', 'REPORTED', 'VERIFIED', 'CANCELLED')),
    bill_id             UUID REFERENCES billing.bill (id),
    ordered_by          UUID NOT NULL REFERENCES iam.user_account (id),
    ordered_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- laboratory
    collected_by        UUID REFERENCES iam.user_account (id),
    collected_at        TIMESTAMPTZ,
    received_by         UUID REFERENCES iam.user_account (id),
    received_at         TIMESTAMPTZ,
    rejection_note      TEXT,
    result_by           UUID REFERENCES iam.user_account (id),
    result_at           TIMESTAMPTZ,
    validated_by        UUID REFERENCES iam.user_account (id),
    validated_at        TIMESTAMPTZ,
    has_critical        BOOLEAN NOT NULL DEFAULT false,
    -- imaging
    scheduled_at        TIMESTAMPTZ,
    performed_by        UUID REFERENCES iam.user_account (id),
    performed_at        TIMESTAMPTZ,
    study_instance_uid  TEXT CHECK (study_instance_uid IS NULL OR study_instance_uid ~ '^[0-9]+(\.[0-9]+)*$'),
    image_count         INT,
    findings            TEXT,
    impression          TEXT,
    reported_by         UUID REFERENCES iam.user_account (id),
    reported_at         TIMESTAMPTZ,
    verified_by         UUID REFERENCES iam.user_account (id),
    verified_at         TIMESTAMPTZ,
    cancel_reason       TEXT,
    version             INT NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, accession_no),
    CHECK ((kind = 'LAB') = (lab_test_id IS NOT NULL)),
    CHECK ((kind = 'IMAGING') = (procedure_id IS NOT NULL)),
    -- Laboratory results are validated by someone other than the person who entered them.
    CHECK (validated_by IS NULL OR validated_by <> result_by)
);
CREATE INDEX diagnostic_order_centre_ix ON diagnostics.diagnostic_order (centre_id, kind, status, ordered_at DESC);
CREATE INDEX diagnostic_order_patient_ix ON diagnostics.diagnostic_order (patient_id, ordered_at DESC);
CREATE UNIQUE INDEX diagnostic_order_study_ux ON diagnostics.diagnostic_order (study_instance_uid) WHERE study_instance_uid IS NOT NULL;

CREATE TABLE diagnostics.lab_result (
    id             UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id      UUID NOT NULL REFERENCES org.tenant (id),
    order_id       UUID NOT NULL REFERENCES diagnostics.diagnostic_order (id),
    parameter_id   UUID NOT NULL REFERENCES diagnostics.lab_parameter (id),
    value_numeric  NUMERIC(14,4),
    value_text     TEXT,
    flag           TEXT CHECK (flag IN ('N', 'L', 'H', 'LL', 'HH', 'A')),
    entered_by     UUID NOT NULL REFERENCES iam.user_account (id),
    entered_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (order_id, parameter_id)
);

DO $$
DECLARE t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY['diagnostics.lab_test', 'diagnostics.lab_parameter', 'diagnostics.imaging_procedure',
                             'diagnostics.diagnostic_order', 'diagnostics.lab_result'] LOOP
        EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON %s USING (tenant_id = nullif(current_setting(''app.tenant_id'', true), '''')::uuid) '
            'WITH CHECK (tenant_id = nullif(current_setting(''app.tenant_id'', true), '''')::uuid)', t);
    END LOOP;
END $$;

ALTER TABLE billing.bill_line DROP CONSTRAINT bill_line_source_type_check;
ALTER TABLE billing.bill_line ADD CONSTRAINT bill_line_source_type_check
    CHECK (source_type IN ('PHARMACY_SALE', 'SPECTACLE_ORDER', 'DIAGNOSTIC_ORDER'));

INSERT INTO iam.permission (code, module, description, tenant_only) VALUES
  ('lab.catalogue.manage',       'lab',       'Maintain the chain''s laboratory test catalogue, parameters and reference ranges', true),
  ('lab.order',                  'lab',       'Order laboratory tests for a patient', false),
  ('lab.read',                   'lab',       'View laboratory orders and released results', false),
  ('lab.collect',                'lab',       'Collect and label samples', false),
  ('lab.process',                'lab',       'Receive or reject samples in the laboratory and enter results', false),
  ('lab.validate',               'lab',       'Validate and release results entered by someone else', false),
  ('radiology.catalogue.manage', 'radiology', 'Maintain the chain''s imaging procedure catalogue', true),
  ('radiology.order',            'radiology', 'Order imaging (X-ray, CT, MRI, ultrasound, OCT, fundus imaging) for a patient', false),
  ('radiology.read',             'radiology', 'View imaging orders, released reports and the images', false),
  ('radiology.perform',          'radiology', 'Schedule and perform imaging, and link the images', false),
  ('radiology.report',           'radiology', 'Write imaging reports', false),
  ('radiology.verify',           'radiology', 'Sign and release imaging reports', false);
