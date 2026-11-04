-- Slice 2.1: one patient identity (UHID) for the group, the eye-care outpatient
-- flow (appointment -> check-in -> optometry -> consultation -> counselling ->
-- checkout), eye surgery from advice to operative note, and centre billing
-- with discounts, payments and day close.
-- Same rules as V3: tenant_id on every row, row-level security, runtime role
-- core_neha_app has data access only.

CREATE SCHEMA patient;
CREATE SCHEMA eyecare;
CREATE SCHEMA billing;

GRANT USAGE ON SCHEMA patient, eyecare, billing TO core_neha_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA patient, eyecare, billing
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO core_neha_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA patient, eyecare, billing
    GRANT USAGE, SELECT ON SEQUENCES TO core_neha_app;

------------------------------------------------------------------------------
-- Operator lookup of a tenant by code (operator commands only: the runtime
-- role cannot read other tenants' rows, so this narrow function returns the id
-- and nothing else).
------------------------------------------------------------------------------
CREATE FUNCTION org.tenant_id_by_code(p_code TEXT) RETURNS UUID
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog
AS $$ SELECT id FROM org.tenant WHERE code = p_code $$;
REVOKE ALL ON FUNCTION org.tenant_id_by_code(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION org.tenant_id_by_code(TEXT) TO core_neha_app;

------------------------------------------------------------------------------
-- patient
------------------------------------------------------------------------------
CREATE TABLE patient.uhid_counter (
    tenant_id   UUID PRIMARY KEY REFERENCES org.tenant (id),
    prefix      TEXT NOT NULL DEFAULT 'NH',
    last_value  BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE patient.patient (
    id                      UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id               UUID NOT NULL REFERENCES org.tenant (id),
    uhid                    TEXT NOT NULL,
    given_name              TEXT NOT NULL CHECK (length(given_name) BETWEEN 1 AND 100),
    family_name             TEXT NOT NULL DEFAULT '' CHECK (length(family_name) <= 100),
    sex                     TEXT NOT NULL CHECK (sex IN ('FEMALE', 'MALE', 'OTHER', 'UNKNOWN')),
    birth_date              DATE,
    birth_date_estimated    BOOLEAN NOT NULL DEFAULT false,
    mobile                  TEXT CHECK (mobile IS NULL OR mobile ~ '^[0-9+][0-9 -]{6,19}$'),
    email                   TEXT,
    address_line            TEXT,
    city                    TEXT,
    state                   TEXT,
    pincode                 TEXT CHECK (pincode IS NULL OR pincode ~ '^[0-9]{6}$'),
    abha_number             TEXT CHECK (abha_number IS NULL OR abha_number ~ '^[0-9]{2}-[0-9]{4}-[0-9]{4}-[0-9]{4}$'),
    guardian_name           TEXT,
    emergency_contact_name  TEXT,
    emergency_contact_phone TEXT,
    registered_centre_id    UUID NOT NULL REFERENCES org.centre (id),
    is_demo                 BOOLEAN NOT NULL DEFAULT false,
    status                  TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_by              UUID REFERENCES iam.user_account (id),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                 INT NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, uhid)
);
CREATE INDEX patient_mobile_ix ON patient.patient (tenant_id, mobile);
CREATE INDEX patient_name_ix ON patient.patient (tenant_id, lower(given_name || ' ' || family_name) text_pattern_ops);
CREATE UNIQUE INDEX patient_abha_ux ON patient.patient (tenant_id, abha_number) WHERE abha_number IS NOT NULL;

------------------------------------------------------------------------------
-- eyecare
------------------------------------------------------------------------------
-- Per-centre, per-day running numbers (visit tokens).
CREATE TABLE eyecare.daily_counter (
    tenant_id      UUID NOT NULL REFERENCES org.tenant (id),
    centre_id      UUID NOT NULL REFERENCES org.centre (id),
    business_date  DATE NOT NULL,
    kind           TEXT NOT NULL,
    last_value     INT NOT NULL,
    PRIMARY KEY (centre_id, business_date, kind)
);

CREATE TABLE eyecare.appointment (
    id                    UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id             UUID NOT NULL REFERENCES org.tenant (id),
    centre_id             UUID NOT NULL REFERENCES org.centre (id),
    patient_id            UUID NOT NULL REFERENCES patient.patient (id),
    appointment_date      DATE NOT NULL,
    slot_time             TIME NOT NULL,
    visit_type            TEXT NOT NULL CHECK (visit_type IN ('NEW', 'REVIEW', 'POST_OP')),
    source                TEXT NOT NULL CHECK (source IN ('WALK_IN', 'PHONE', 'ONLINE', 'REFERRAL')),
    consultant_person_id  UUID REFERENCES workforce.person (id),
    reason                TEXT,
    status                TEXT NOT NULL DEFAULT 'BOOKED' CHECK (status IN ('BOOKED', 'CHECKED_IN', 'CANCELLED', 'NO_SHOW')),
    cancel_reason         TEXT,
    visit_id              UUID,
    created_by            UUID NOT NULL REFERENCES iam.user_account (id),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    version               INT NOT NULL DEFAULT 0
);
CREATE INDEX appointment_day_ix ON eyecare.appointment (centre_id, appointment_date, slot_time);

-- One outpatient visit (the encounter). Every department works on this same row
-- and its records; nothing is copied between departments.
CREATE TABLE eyecare.visit (
    id                      UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id               UUID NOT NULL REFERENCES org.tenant (id),
    centre_id               UUID NOT NULL REFERENCES org.centre (id),
    patient_id              UUID NOT NULL REFERENCES patient.patient (id),
    appointment_id          UUID REFERENCES eyecare.appointment (id),
    visit_date              DATE NOT NULL,
    token_no                INT NOT NULL,
    visit_type              TEXT NOT NULL CHECK (visit_type IN ('NEW', 'REVIEW', 'POST_OP')),
    chief_complaint         TEXT,
    consultant_person_id    UUID REFERENCES workforce.person (id),
    stage                   TEXT NOT NULL CHECK (stage IN ('OPTOMETRY', 'CONSULTATION', 'COUNSELLING', 'CHECKOUT', 'COMPLETED', 'CANCELLED')),
    checked_in_by           UUID NOT NULL REFERENCES iam.user_account (id),
    checked_in_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    optometry_done_at       TIMESTAMPTZ,
    consultation_done_at    TIMESTAMPTZ,
    counselling_done_at     TIMESTAMPTZ,
    completed_at            TIMESTAMPTZ,
    completed_by            UUID REFERENCES iam.user_account (id),
    no_charge_reason        TEXT,
    cancel_reason           TEXT,
    version                 INT NOT NULL DEFAULT 0,
    UNIQUE (centre_id, visit_date, token_no)
);
CREATE INDEX visit_day_stage_ix ON eyecare.visit (centre_id, visit_date, stage);
CREATE INDEX visit_patient_ix ON eyecare.visit (patient_id, visit_date DESC);
ALTER TABLE eyecare.appointment ADD CONSTRAINT appointment_visit_fk FOREIGN KEY (visit_id) REFERENCES eyecare.visit (id);

-- Optometry work-up: vision, refraction and pressure for each eye (RE right, LE left).
CREATE TABLE eyecare.optometry_record (
    visit_id            UUID PRIMARY KEY REFERENCES eyecare.visit (id),
    tenant_id           UUID NOT NULL REFERENCES org.tenant (id),
    ucva_re             TEXT,  -- unaided distance vision, Snellen (6/6 ... 6/60, CF, HM, PL, NPL)
    ucva_le             TEXT,
    pgva_re             TEXT,  -- with present glasses
    pgva_le             TEXT,
    near_re             TEXT,  -- near vision (N6 ... N36)
    near_le             TEXT,
    sph_re              NUMERIC(5,2) CHECK (sph_re BETWEEN -30 AND 30),
    cyl_re              NUMERIC(5,2) CHECK (cyl_re BETWEEN -10 AND 10),
    axis_re             INT CHECK (axis_re BETWEEN 0 AND 180),
    add_re              NUMERIC(4,2) CHECK (add_re BETWEEN 0 AND 4),
    bcva_re             TEXT,
    sph_le              NUMERIC(5,2) CHECK (sph_le BETWEEN -30 AND 30),
    cyl_le              NUMERIC(5,2) CHECK (cyl_le BETWEEN -10 AND 10),
    axis_le             INT CHECK (axis_le BETWEEN 0 AND 180),
    add_le              NUMERIC(4,2) CHECK (add_le BETWEEN 0 AND 4),
    bcva_le             TEXT,
    iop_re              INT CHECK (iop_re BETWEEN 0 AND 80),
    iop_le              INT CHECK (iop_le BETWEEN 0 AND 80),
    iop_method          TEXT CHECK (iop_method IS NULL OR iop_method IN ('NCT', 'APPLANATION', 'TONOPEN', 'ICARE')),
    colour_vision       TEXT,
    notes               TEXT,
    recorded_by         UUID NOT NULL REFERENCES iam.user_account (id),
    recorded_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    version             INT NOT NULL DEFAULT 0
);

-- Doctor's consultation: findings, diagnosis, treatment and the surgery decision.
CREATE TABLE eyecare.consultation (
    visit_id              UUID PRIMARY KEY REFERENCES eyecare.visit (id),
    tenant_id             UUID NOT NULL REFERENCES org.tenant (id),
    history               TEXT,
    anterior_segment_re   TEXT,
    anterior_segment_le   TEXT,
    posterior_segment_re  TEXT,
    posterior_segment_le  TEXT,
    diagnosis             TEXT,
    diagnosis_code        TEXT CHECK (diagnosis_code IS NULL OR diagnosis_code ~ '^[A-Z][0-9]{2}(\.[0-9A-Z]{1,4})?$'),
    diagnosis_eye         TEXT CHECK (diagnosis_eye IS NULL OR diagnosis_eye IN ('RE', 'LE', 'BE')),
    medications           TEXT,
    glasses_prescribed    BOOLEAN NOT NULL DEFAULT false,
    advice                TEXT,
    follow_up_days        INT CHECK (follow_up_days BETWEEN 0 AND 730),
    surgery_advised       BOOLEAN NOT NULL DEFAULT false,
    surgery_procedure     TEXT,
    surgery_eye           TEXT CHECK (surgery_eye IS NULL OR surgery_eye IN ('RE', 'LE', 'BE')),
    surgery_urgency       TEXT CHECK (surgery_urgency IS NULL OR surgery_urgency IN ('ROUTINE', 'SOON', 'URGENT')),
    consultant_person_id  UUID REFERENCES workforce.person (id),
    recorded_by           UUID NOT NULL REFERENCES iam.user_account (id),
    recorded_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    version               INT NOT NULL DEFAULT 0,
    CHECK (NOT surgery_advised OR (surgery_procedure IS NOT NULL AND surgery_eye IS NOT NULL))
);

-- An eye surgery from the doctor's advice to the operative note.
CREATE TABLE eyecare.surgery_case (
    id                     UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id              UUID NOT NULL REFERENCES org.tenant (id),
    centre_id              UUID NOT NULL REFERENCES org.centre (id),
    patient_id             UUID NOT NULL REFERENCES patient.patient (id),
    visit_id               UUID NOT NULL REFERENCES eyecare.visit (id),
    procedure_name         TEXT NOT NULL,
    eye                    TEXT NOT NULL CHECK (eye IN ('RE', 'LE', 'BE')),
    urgency                TEXT NOT NULL DEFAULT 'ROUTINE' CHECK (urgency IN ('ROUTINE', 'SOON', 'URGENT')),
    status                 TEXT NOT NULL CHECK (status IN ('ADVISED', 'ACCEPTED', 'DECLINED', 'SCHEDULED', 'READY', 'COMPLETED', 'CANCELLED')),
    advised_by_person_id   UUID REFERENCES workforce.person (id),
    -- counselling
    package_item_id        UUID,
    package_name           TEXT,
    estimated_cost         NUMERIC(12,2) CHECK (estimated_cost >= 0),
    payment_plan           TEXT CHECK (payment_plan IS NULL OR payment_plan IN ('SELF_PAY', 'INSURANCE', 'CORPORATE', 'GOVERNMENT_SCHEME')),
    insurer_name           TEXT,
    counselling_notes      TEXT,
    preferred_date         DATE,
    counselled_by          UUID REFERENCES iam.user_account (id),
    counselled_at          TIMESTAMPTZ,
    -- schedule
    surgery_date           DATE,
    slot_time              TIME,
    ot_room                TEXT,
    surgeon_person_id      UUID REFERENCES workforce.person (id),
    scheduled_by           UUID REFERENCES iam.user_account (id),
    -- pre-operative checklist (WHO surgical safety, adapted for eye day-care)
    consent_signed         BOOLEAN NOT NULL DEFAULT false,
    site_marked            BOOLEAN NOT NULL DEFAULT false,
    fitness_cleared        BOOLEAN NOT NULL DEFAULT false,
    biometry_done          BOOLEAN NOT NULL DEFAULT false,
    iol_power              TEXT,
    pupil_dilated          BOOLEAN NOT NULL DEFAULT false,
    pre_op_bp              TEXT,
    pre_op_blood_sugar     TEXT,
    pre_op_notes           TEXT,
    prepared_by            UUID REFERENCES iam.user_account (id),
    prepared_at            TIMESTAMPTZ,
    -- operation
    anaesthesia            TEXT CHECK (anaesthesia IS NULL OR anaesthesia IN ('TOPICAL', 'PERIBULBAR', 'RETROBULBAR', 'SUBTENON', 'GENERAL')),
    iol_implanted          TEXT,
    operative_notes        TEXT,
    complications          TEXT,
    post_op_instructions   TEXT,
    operated_by_person_id  UUID REFERENCES workforce.person (id),
    started_at             TIMESTAMPTZ,
    completed_at           TIMESTAMPTZ,
    recorded_by            UUID REFERENCES iam.user_account (id),
    cancel_reason          TEXT,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                INT NOT NULL DEFAULT 0,
    CHECK (status NOT IN ('READY', 'COMPLETED') OR (consent_signed AND site_marked AND fitness_cleared)),
    CHECK (status <> 'COMPLETED' OR (operative_notes IS NOT NULL AND anaesthesia IS NOT NULL))
);
CREATE INDEX surgery_case_status_ix ON eyecare.surgery_case (centre_id, status, surgery_date);
CREATE INDEX surgery_case_patient_ix ON eyecare.surgery_case (patient_id);

------------------------------------------------------------------------------
-- billing
------------------------------------------------------------------------------
CREATE TABLE billing.tariff_item (
    id          UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id   UUID NOT NULL REFERENCES org.tenant (id),
    centre_id   UUID NOT NULL REFERENCES org.centre (id),
    code        TEXT NOT NULL CHECK (code ~ '^[A-Z][A-Z0-9_]{1,39}$'),
    name        TEXT NOT NULL,
    category    TEXT NOT NULL CHECK (category IN ('REGISTRATION', 'CONSULTATION', 'INVESTIGATION', 'PROCEDURE', 'SURGERY_PACKAGE', 'OPTICAL', 'PHARMACY', 'OTHER')),
    price       NUMERIC(12,2) NOT NULL CHECK (price >= 0),
    gst_rate    NUMERIC(4,2) NOT NULL DEFAULT 0 CHECK (gst_rate IN (0, 5, 12, 18, 28)),
    status      TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'RETIRED')),
    updated_by  UUID REFERENCES iam.user_account (id),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    version     INT NOT NULL DEFAULT 0,
    UNIQUE (centre_id, code)
);

CREATE TABLE billing.counter (
    tenant_id   UUID NOT NULL REFERENCES org.tenant (id),
    centre_id   UUID NOT NULL REFERENCES org.centre (id),
    period      TEXT NOT NULL,          -- financial year, e.g. 2026-27
    last_value  INT NOT NULL,
    PRIMARY KEY (centre_id, period)
);

CREATE TABLE billing.bill (
    id                     UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id              UUID NOT NULL REFERENCES org.tenant (id),
    centre_id              UUID NOT NULL REFERENCES org.centre (id),
    patient_id             UUID NOT NULL REFERENCES patient.patient (id),
    bill_no                TEXT NOT NULL,
    bill_date              DATE NOT NULL,
    visit_ref              UUID,     -- eye-care visit the bill belongs to (opaque to billing)
    surgery_ref            UUID,     -- surgery case for a package bill
    status                 TEXT NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'PAID', 'CANCELLED')),
    gross_amount           NUMERIC(12,2) NOT NULL DEFAULT 0,
    tax_amount             NUMERIC(12,2) NOT NULL DEFAULT 0,
    discount_amount        NUMERIC(12,2) NOT NULL DEFAULT 0,  -- approved discount only
    net_amount             NUMERIC(12,2) NOT NULL DEFAULT 0,
    paid_amount            NUMERIC(12,2) NOT NULL DEFAULT 0,
    discount_requested     NUMERIC(12,2),
    discount_reason        TEXT,
    discount_status        TEXT NOT NULL DEFAULT 'NONE' CHECK (discount_status IN ('NONE', 'PENDING', 'APPROVED', 'REJECTED')),
    discount_requested_by  UUID REFERENCES iam.user_account (id),
    discount_decided_by    UUID REFERENCES iam.user_account (id),
    discount_decision_note TEXT,
    cancel_reason          TEXT,
    created_by             UUID NOT NULL REFERENCES iam.user_account (id),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at              TIMESTAMPTZ,
    version                INT NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, bill_no),
    CHECK (paid_amount <= net_amount),
    CHECK (discount_amount <= gross_amount + tax_amount),
    -- Discounts need someone other than the requester (segregation of duties).
    CHECK (discount_decided_by IS NULL OR discount_decided_by <> discount_requested_by)
);
CREATE INDEX bill_day_ix ON billing.bill (centre_id, bill_date);
CREATE INDEX bill_patient_ix ON billing.bill (patient_id);
CREATE INDEX bill_visit_ix ON billing.bill (visit_ref);

CREATE TABLE billing.bill_line (
    id              UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id       UUID NOT NULL REFERENCES org.tenant (id),
    bill_id         UUID NOT NULL REFERENCES billing.bill (id),
    tariff_item_id  UUID NOT NULL REFERENCES billing.tariff_item (id),
    description     TEXT NOT NULL,
    category        TEXT NOT NULL,
    quantity        INT NOT NULL CHECK (quantity BETWEEN 1 AND 999),
    unit_price      NUMERIC(12,2) NOT NULL CHECK (unit_price >= 0),
    gst_rate        NUMERIC(4,2) NOT NULL,
    amount          NUMERIC(12,2) NOT NULL,
    tax_amount      NUMERIC(12,2) NOT NULL,
    added_by        UUID NOT NULL REFERENCES iam.user_account (id),
    added_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE billing.payment (
    id            UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id     UUID NOT NULL REFERENCES org.tenant (id),
    centre_id     UUID NOT NULL REFERENCES org.centre (id),
    bill_id       UUID NOT NULL REFERENCES billing.bill (id),
    business_date DATE NOT NULL,
    mode          TEXT NOT NULL CHECK (mode IN ('CASH', 'CARD', 'UPI', 'INSURANCE', 'CORPORATE')),
    amount        NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    reference     TEXT,
    received_by   UUID NOT NULL REFERENCES iam.user_account (id),
    received_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (mode = 'CASH' OR reference IS NOT NULL)
);
CREATE INDEX payment_day_ix ON billing.payment (centre_id, business_date);

-- Daily cash reconciliation: submitted by one person, approved by another.
CREATE TABLE billing.day_close (
    id              UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id       UUID NOT NULL REFERENCES org.tenant (id),
    centre_id       UUID NOT NULL REFERENCES org.centre (id),
    business_date   DATE NOT NULL,
    system_cash     NUMERIC(12,2) NOT NULL,
    system_card     NUMERIC(12,2) NOT NULL,
    system_upi      NUMERIC(12,2) NOT NULL,
    system_other    NUMERIC(12,2) NOT NULL,
    counted_cash    NUMERIC(12,2) NOT NULL CHECK (counted_cash >= 0),
    variance        NUMERIC(12,2) NOT NULL,
    notes           TEXT,
    status          TEXT NOT NULL DEFAULT 'SUBMITTED' CHECK (status IN ('SUBMITTED', 'APPROVED', 'REJECTED')),
    submitted_by    UUID NOT NULL REFERENCES iam.user_account (id),
    submitted_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_by      UUID REFERENCES iam.user_account (id),
    decided_at      TIMESTAMPTZ,
    decision_note   TEXT,
    version         INT NOT NULL DEFAULT 0,
    CHECK (decided_by IS NULL OR decided_by <> submitted_by)
);
CREATE UNIQUE INDEX day_close_open_ux ON billing.day_close (centre_id, business_date) WHERE status <> 'REJECTED';

------------------------------------------------------------------------------
-- Row-level security
------------------------------------------------------------------------------
DO $$
DECLARE t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'patient.uhid_counter', 'patient.patient',
        'eyecare.daily_counter', 'eyecare.appointment', 'eyecare.visit', 'eyecare.optometry_record',
        'eyecare.consultation', 'eyecare.surgery_case',
        'billing.tariff_item', 'billing.counter', 'billing.bill', 'billing.bill_line', 'billing.payment', 'billing.day_close']
    LOOP
        EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON %s USING (tenant_id = nullif(current_setting(''app.tenant_id'', true), '''')::uuid) '
            || 'WITH CHECK (tenant_id = nullif(current_setting(''app.tenant_id'', true), '''')::uuid)', t);
    END LOOP;
END $$;

------------------------------------------------------------------------------
-- Permission catalogue for slice 2.1
------------------------------------------------------------------------------
INSERT INTO iam.permission (code, module, description, tenant_only) VALUES
  ('patient.read',              'patient',  'Search and view patient demographics', false),
  ('patient.register',          'patient',  'Register patients and update their demographics', false),
  ('opd.queue.read',            'eyecare',  'View appointments, the visit queue and the clinic board', false),
  ('opd.appointment.manage',    'eyecare',  'Book, reschedule and cancel appointments', false),
  ('opd.checkin',               'eyecare',  'Check patients in, start walk-in visits and complete checkout', false),
  ('eye.clinical.read',         'eyecare',  'View eye examination, consultation and surgery records', false),
  ('eye.optometry.record',      'eyecare',  'Record vision, refraction and eye pressure', false),
  ('eye.consultation.record',   'eyecare',  'Record consultation, diagnosis, treatment and surgery advice', false),
  ('eye.counselling.record',    'eyecare',  'Record surgery counselling and the patient''s decision', false),
  ('eye.surgery.schedule',      'eyecare',  'Schedule surgeries in the operation theatre', false),
  ('eye.surgery.prepare',       'eyecare',  'Complete the pre-operative checklist', false),
  ('eye.surgery.operate',       'eyecare',  'Record the operation and complete the surgery', false),
  ('billing.read',              'billing',  'View bills and payments', false),
  ('billing.charge',            'billing',  'Raise bills, add charges and request discounts', false),
  ('billing.collect',           'billing',  'Collect payments against bills', false),
  ('billing.discount.approve',  'billing',  'Approve or reject discounts requested by others', false),
  ('billing.tariff.manage',     'billing',  'Maintain the centre price list', false),
  ('billing.report.read',       'billing',  'View collection and outstanding reports', false),
  ('billing.dayclose.submit',   'billing',  'Submit the daily cash reconciliation', false),
  ('billing.dayclose.approve',  'billing',  'Approve or reject a daily cash reconciliation', false);
