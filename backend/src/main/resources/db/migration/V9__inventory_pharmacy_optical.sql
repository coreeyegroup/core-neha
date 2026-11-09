-- Inventory (one stock ledger for every store), pharmacy, optical shop and optical manufacturing.
-- Item master is per tenant (chain); stores belong to a centre; stock is held by batch with expiry;
-- every quantity change is a movement row (append-only), so stock on hand is always explainable.

CREATE SCHEMA inventory;
CREATE SCHEMA pharmacy;
CREATE SCHEMA optical;
GRANT USAGE ON SCHEMA inventory, pharmacy, optical TO core_neha_app;

------------------------------------------------------------------------------
-- inventory
------------------------------------------------------------------------------
CREATE TABLE inventory.item (
    id              UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id       UUID NOT NULL REFERENCES org.tenant (id),
    code            TEXT NOT NULL CHECK (code ~ '^[A-Z0-9][A-Z0-9_-]{1,39}$'),
    name            TEXT NOT NULL CHECK (length(name) BETWEEN 2 AND 200),
    category        TEXT NOT NULL CHECK (category IN ('DRUG', 'CONSUMABLE', 'SURGICAL', 'FRAME', 'SPECTACLE_LENS', 'LENS_BLANK',
                                                      'CONTACT_LENS', 'OPTICAL_ACCESSORY', 'LAB_REAGENT')),
    unit            TEXT NOT NULL CHECK (length(unit) BETWEEN 1 AND 20),
    hsn_code        TEXT,
    gst_rate        NUMERIC(4,2) NOT NULL DEFAULT 0 CHECK (gst_rate IN (0, 5, 12, 18, 28)),
    mrp             NUMERIC(12,2) CHECK (mrp IS NULL OR mrp >= 0),
    -- drugs
    generic_name    TEXT,
    strength        TEXT,
    dosage_form     TEXT,
    schedule        TEXT CHECK (schedule IN ('OTC', 'G', 'H', 'H1', 'X')),
    manufacturer    TEXT,
    -- optical
    brand           TEXT,
    model           TEXT,
    colour          TEXT,
    size_text       TEXT,
    lens_type       TEXT CHECK (lens_type IN ('SINGLE_VISION', 'BIFOCAL', 'PROGRESSIVE')),
    lens_index      NUMERIC(3,2) CHECK (lens_index IS NULL OR lens_index BETWEEN 1.40 AND 1.90),
    coating         TEXT,
    batch_tracked   BOOLEAN NOT NULL DEFAULT true,
    expiry_tracked  BOOLEAN NOT NULL DEFAULT false,
    reorder_level   INT NOT NULL DEFAULT 0 CHECK (reorder_level >= 0),
    status          TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'RETIRED')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         INT NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, code),
    -- Schedule H1 and X drugs are dispensed only against a prescription and must be traceable by batch.
    CHECK (category <> 'DRUG' OR (schedule IS NOT NULL AND batch_tracked AND expiry_tracked))
);

CREATE TABLE inventory.store (
    id          UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id   UUID NOT NULL REFERENCES org.tenant (id),
    centre_id   UUID NOT NULL REFERENCES org.centre (id),
    code        TEXT NOT NULL CHECK (code ~ '^[A-Z0-9][A-Z0-9_-]{1,39}$'),
    name        TEXT NOT NULL CHECK (length(name) BETWEEN 2 AND 200),
    store_type  TEXT NOT NULL CHECK (store_type IN ('PHARMACY', 'OPTICAL', 'OPTICAL_LAB', 'OT', 'CENTRAL')),
    status      TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'RETIRED')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    version     INT NOT NULL DEFAULT 0,
    UNIQUE (centre_id, code)
);

CREATE TABLE inventory.stock_batch (
    id           UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id    UUID NOT NULL REFERENCES org.tenant (id),
    store_id     UUID NOT NULL REFERENCES inventory.store (id),
    item_id      UUID NOT NULL REFERENCES inventory.item (id),
    batch_no     TEXT NOT NULL,
    expiry_date  DATE,
    mrp          NUMERIC(12,2) NOT NULL CHECK (mrp >= 0),
    unit_cost    NUMERIC(12,2) NOT NULL CHECK (unit_cost >= 0),
    qty_on_hand  INT NOT NULL CHECK (qty_on_hand >= 0),
    received_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    version      INT NOT NULL DEFAULT 0,
    UNIQUE (store_id, item_id, batch_no)
);
CREATE INDEX stock_batch_store_item_ix ON inventory.stock_batch (store_id, item_id, expiry_date);

CREATE TABLE inventory.stock_movement (
    id             UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id      UUID NOT NULL REFERENCES org.tenant (id),
    store_id       UUID NOT NULL REFERENCES inventory.store (id),
    item_id        UUID NOT NULL REFERENCES inventory.item (id),
    batch_id       UUID NOT NULL REFERENCES inventory.stock_batch (id),
    movement_type  TEXT NOT NULL CHECK (movement_type IN ('RECEIPT', 'SALE', 'SALE_RETURN', 'ISSUE', 'TRANSFER_OUT', 'TRANSFER_IN',
                                                          'ADJUSTMENT', 'CONSUMPTION')),
    quantity       INT NOT NULL CHECK (quantity <> 0),
    balance_after  INT NOT NULL CHECK (balance_after >= 0),
    ref_type       TEXT,
    ref_id         UUID,
    ref_no         TEXT,
    reason         TEXT,
    moved_by       UUID NOT NULL REFERENCES iam.user_account (id),
    moved_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX stock_movement_store_time_ix ON inventory.stock_movement (store_id, moved_at DESC);
CREATE INDEX stock_movement_ref_ix ON inventory.stock_movement (ref_type, ref_id);

CREATE TABLE inventory.doc_counter (
    tenant_id   UUID NOT NULL REFERENCES org.tenant (id),
    centre_id   UUID NOT NULL REFERENCES org.centre (id),
    doc_type    TEXT NOT NULL,
    period      TEXT NOT NULL,
    last_value  INT NOT NULL,
    PRIMARY KEY (centre_id, doc_type, period)
);

CREATE TABLE inventory.receipt (
    id                   UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id            UUID NOT NULL REFERENCES org.tenant (id),
    centre_id            UUID NOT NULL REFERENCES org.centre (id),
    store_id             UUID NOT NULL REFERENCES inventory.store (id),
    receipt_no           TEXT NOT NULL,
    supplier_name        TEXT NOT NULL CHECK (length(supplier_name) BETWEEN 2 AND 200),
    supplier_invoice_no  TEXT NOT NULL,
    invoice_date         DATE NOT NULL,
    total_cost           NUMERIC(14,2) NOT NULL,
    received_by          UUID NOT NULL REFERENCES iam.user_account (id),
    received_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, receipt_no),
    UNIQUE (store_id, supplier_name, supplier_invoice_no)
);

CREATE TABLE inventory.receipt_line (
    id           UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id    UUID NOT NULL REFERENCES org.tenant (id),
    receipt_id   UUID NOT NULL REFERENCES inventory.receipt (id),
    item_id      UUID NOT NULL REFERENCES inventory.item (id),
    batch_id     UUID NOT NULL REFERENCES inventory.stock_batch (id),
    quantity     INT NOT NULL CHECK (quantity > 0),
    unit_cost    NUMERIC(12,2) NOT NULL CHECK (unit_cost >= 0),
    mrp          NUMERIC(12,2) NOT NULL CHECK (mrp >= 0)
);

CREATE TABLE inventory.adjustment (
    id            UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id     UUID NOT NULL REFERENCES org.tenant (id),
    centre_id     UUID NOT NULL REFERENCES org.centre (id),
    store_id      UUID NOT NULL REFERENCES inventory.store (id),
    batch_id      UUID NOT NULL REFERENCES inventory.stock_batch (id),
    quantity      INT NOT NULL CHECK (quantity <> 0),
    reason        TEXT NOT NULL CHECK (reason IN ('DAMAGED', 'EXPIRED', 'COUNT_CORRECTION', 'LOST', 'OTHER')),
    note          TEXT NOT NULL,
    status        TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    requested_by  UUID NOT NULL REFERENCES iam.user_account (id),
    requested_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_by    UUID REFERENCES iam.user_account (id),
    decided_at    TIMESTAMPTZ,
    decision_note TEXT,
    version       INT NOT NULL DEFAULT 0,
    -- Stock write-offs and corrections need a second person (segregation of duties).
    CHECK (decided_by IS NULL OR decided_by <> requested_by)
);

CREATE TABLE inventory.transfer (
    id            UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id     UUID NOT NULL REFERENCES org.tenant (id),
    transfer_no   TEXT NOT NULL,
    from_store_id UUID NOT NULL REFERENCES inventory.store (id),
    to_store_id   UUID NOT NULL REFERENCES inventory.store (id),
    note          TEXT,
    sent_by       UUID NOT NULL REFERENCES iam.user_account (id),
    sent_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, transfer_no),
    CHECK (from_store_id <> to_store_id)
);

------------------------------------------------------------------------------
-- pharmacy
------------------------------------------------------------------------------
CREATE TABLE pharmacy.prescription (
    id              UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id       UUID NOT NULL REFERENCES org.tenant (id),
    centre_id       UUID NOT NULL REFERENCES org.centre (id),
    patient_id      UUID NOT NULL REFERENCES patient.patient (id),
    visit_ref       UUID,
    prescription_no TEXT NOT NULL,
    status          TEXT NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'PARTLY_DISPENSED', 'DISPENSED', 'CANCELLED')),
    notes           TEXT,
    prescribed_by   UUID NOT NULL REFERENCES iam.user_account (id),
    prescribed_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         INT NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, prescription_no)
);
CREATE INDEX prescription_centre_status_ix ON pharmacy.prescription (centre_id, status, prescribed_at DESC);

CREATE TABLE pharmacy.prescription_line (
    id               UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id        UUID NOT NULL REFERENCES org.tenant (id),
    prescription_id  UUID NOT NULL REFERENCES pharmacy.prescription (id),
    item_id          UUID NOT NULL REFERENCES inventory.item (id),
    dose             TEXT NOT NULL,
    frequency        TEXT NOT NULL,
    duration_days    INT NOT NULL CHECK (duration_days BETWEEN 1 AND 365),
    eye              TEXT CHECK (eye IN ('RE', 'LE', 'BE')),
    route            TEXT NOT NULL DEFAULT 'TOPICAL' CHECK (route IN ('TOPICAL', 'ORAL', 'INJECTION', 'OTHER')),
    quantity         INT NOT NULL CHECK (quantity BETWEEN 1 AND 999),
    instructions     TEXT,
    dispensed_qty    INT NOT NULL DEFAULT 0 CHECK (dispensed_qty >= 0 AND dispensed_qty <= quantity)
);

CREATE TABLE pharmacy.sale (
    id               UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id        UUID NOT NULL REFERENCES org.tenant (id),
    centre_id        UUID NOT NULL REFERENCES org.centre (id),
    store_id         UUID NOT NULL REFERENCES inventory.store (id),
    sale_no          TEXT NOT NULL,
    patient_id       UUID NOT NULL REFERENCES patient.patient (id),
    prescription_id  UUID REFERENCES pharmacy.prescription (id),
    bill_id          UUID REFERENCES billing.bill (id),
    total_amount     NUMERIC(12,2) NOT NULL,
    returned_amount  NUMERIC(12,2) NOT NULL DEFAULT 0,
    sold_by          UUID NOT NULL REFERENCES iam.user_account (id),
    sold_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, sale_no)
);

CREATE TABLE pharmacy.sale_line (
    id                    UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id             UUID NOT NULL REFERENCES org.tenant (id),
    sale_id               UUID NOT NULL REFERENCES pharmacy.sale (id),
    item_id               UUID NOT NULL REFERENCES inventory.item (id),
    batch_id              UUID NOT NULL REFERENCES inventory.stock_batch (id),
    prescription_line_id  UUID REFERENCES pharmacy.prescription_line (id),
    quantity              INT NOT NULL CHECK (quantity > 0),
    mrp                   NUMERIC(12,2) NOT NULL,
    gst_rate              NUMERIC(4,2) NOT NULL,
    amount                NUMERIC(12,2) NOT NULL,
    returned_qty          INT NOT NULL DEFAULT 0 CHECK (returned_qty >= 0 AND returned_qty <= quantity)
);

CREATE TABLE pharmacy.sale_return (
    id           UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id    UUID NOT NULL REFERENCES org.tenant (id),
    sale_id      UUID NOT NULL REFERENCES pharmacy.sale (id),
    sale_line_id UUID NOT NULL REFERENCES pharmacy.sale_line (id),
    quantity     INT NOT NULL CHECK (quantity > 0),
    amount       NUMERIC(12,2) NOT NULL,
    reason       TEXT NOT NULL,
    returned_by  UUID NOT NULL REFERENCES iam.user_account (id),
    returned_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

------------------------------------------------------------------------------
-- optical shop and optical manufacturing (lab)
------------------------------------------------------------------------------
CREATE TABLE optical.spectacle_order (
    id               UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id        UUID NOT NULL REFERENCES org.tenant (id),
    centre_id        UUID NOT NULL REFERENCES org.centre (id),
    store_id         UUID NOT NULL REFERENCES inventory.store (id),
    order_no         TEXT NOT NULL,
    patient_id       UUID NOT NULL REFERENCES patient.patient (id),
    visit_ref        UUID,
    -- prescription (copied from the optometry record when ordered; never changed afterwards)
    sph_re NUMERIC(5,2), cyl_re NUMERIC(5,2), axis_re INT CHECK (axis_re BETWEEN 0 AND 180), add_re NUMERIC(4,2),
    sph_le NUMERIC(5,2), cyl_le NUMERIC(5,2), axis_le INT CHECK (axis_le BETWEEN 0 AND 180), add_le NUMERIC(4,2),
    pd_re            NUMERIC(4,1) CHECK (pd_re BETWEEN 20 AND 45),
    pd_le            NUMERIC(4,1) CHECK (pd_le BETWEEN 20 AND 45),
    fitting_height   NUMERIC(4,1),
    lens_type        TEXT NOT NULL CHECK (lens_type IN ('SINGLE_VISION', 'BIFOCAL', 'PROGRESSIVE')),
    lens_index       NUMERIC(3,2) NOT NULL,
    coating          TEXT,
    tint             TEXT,
    frame_source     TEXT NOT NULL CHECK (frame_source IN ('STOCK', 'PATIENT_OWN')),
    frame_item_id    UUID REFERENCES inventory.item (id),
    frame_batch_id   UUID REFERENCES inventory.stock_batch (id),
    lens_source      TEXT NOT NULL CHECK (lens_source IN ('STOCK', 'LAB')),
    lens_item_id     UUID REFERENCES inventory.item (id),
    lens_batch_id    UUID REFERENCES inventory.stock_batch (id),
    frame_price      NUMERIC(12,2) NOT NULL DEFAULT 0,
    lens_price       NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_amount     NUMERIC(12,2) NOT NULL,
    bill_id          UUID REFERENCES billing.bill (id),
    promised_date    DATE,
    status           TEXT NOT NULL DEFAULT 'ORDERED' CHECK (status IN ('ORDERED', 'IN_LAB', 'READY', 'DELIVERED', 'CANCELLED')),
    notes            TEXT,
    ordered_by       UUID NOT NULL REFERENCES iam.user_account (id),
    ordered_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    delivered_by     UUID REFERENCES iam.user_account (id),
    delivered_at     TIMESTAMPTZ,
    cancel_reason    TEXT,
    version          INT NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, order_no),
    CHECK ((frame_source = 'STOCK') = (frame_batch_id IS NOT NULL)),
    CHECK ((lens_source = 'STOCK') = (lens_batch_id IS NOT NULL))
);
CREATE INDEX spectacle_order_centre_status_ix ON optical.spectacle_order (centre_id, status, ordered_at DESC);

CREATE TABLE optical.lab_job (
    id               UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id        UUID NOT NULL REFERENCES org.tenant (id),
    lab_centre_id    UUID NOT NULL REFERENCES org.centre (id),
    lab_store_id     UUID NOT NULL REFERENCES inventory.store (id),
    job_no           TEXT NOT NULL,
    order_id         UUID NOT NULL REFERENCES optical.spectacle_order (id),
    specification    JSONB NOT NULL,   -- frozen at job creation: prescription, lens, frame, measurements
    status           TEXT NOT NULL DEFAULT 'RECEIVED' CHECK (status IN ('RECEIVED', 'SURFACING', 'COATING', 'EDGING', 'FITTING',
                                                                         'QC', 'READY', 'DISPATCHED', 'CANCELLED')),
    blank_item_id    UUID REFERENCES inventory.item (id),
    remake_count     INT NOT NULL DEFAULT 0,
    fitted_by        UUID REFERENCES iam.user_account (id),
    qc_by            UUID REFERENCES iam.user_account (id),
    qc_result        TEXT CHECK (qc_result IN ('PASS', 'FAIL')),
    qc_measured      JSONB,
    qc_note          TEXT,
    received_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    dispatched_at    TIMESTAMPTZ,
    version          INT NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, job_no),
    -- Quality check by someone other than the person who fitted the lenses.
    CHECK (qc_by IS NULL OR fitted_by IS NULL OR qc_by <> fitted_by)
);
CREATE INDEX lab_job_centre_status_ix ON optical.lab_job (lab_centre_id, status, received_at);

CREATE TABLE optical.lab_job_event (
    id           UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id    UUID NOT NULL REFERENCES org.tenant (id),
    job_id       UUID NOT NULL REFERENCES optical.lab_job (id),
    from_status  TEXT,
    to_status    TEXT NOT NULL,
    note         TEXT,
    actor_id     UUID NOT NULL REFERENCES iam.user_account (id),
    at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

------------------------------------------------------------------------------
-- billing: lines raised by pharmacy and optical carry their source, not a price-list item
------------------------------------------------------------------------------
ALTER TABLE billing.bill_line ALTER COLUMN tariff_item_id DROP NOT NULL;
ALTER TABLE billing.bill_line ADD COLUMN source_type TEXT CHECK (source_type IN ('PHARMACY_SALE', 'SPECTACLE_ORDER'));
ALTER TABLE billing.bill_line ADD COLUMN source_ref UUID;
ALTER TABLE billing.bill_line ADD CONSTRAINT bill_line_priced_or_sourced
    CHECK ((tariff_item_id IS NOT NULL) <> (source_type IS NOT NULL));

------------------------------------------------------------------------------
-- row-level security
------------------------------------------------------------------------------
DO $$
DECLARE t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY['inventory.item', 'inventory.store', 'inventory.stock_batch', 'inventory.stock_movement',
                             'inventory.doc_counter', 'inventory.receipt', 'inventory.receipt_line', 'inventory.adjustment',
                             'inventory.transfer', 'pharmacy.prescription', 'pharmacy.prescription_line', 'pharmacy.sale',
                             'pharmacy.sale_line', 'pharmacy.sale_return', 'optical.spectacle_order', 'optical.lab_job',
                             'optical.lab_job_event'] LOOP
        EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON %s USING (tenant_id = nullif(current_setting(''app.tenant_id'', true), '''')::uuid) '
            'WITH CHECK (tenant_id = nullif(current_setting(''app.tenant_id'', true), '''')::uuid)', t);
    END LOOP;
END $$;

-- The stock ledger is append-only.
REVOKE UPDATE, DELETE ON inventory.stock_movement, optical.lab_job_event FROM core_neha_app;

INSERT INTO iam.permission (code, module, description, tenant_only) VALUES
  ('inventory.item.manage',      'inventory', 'Create and change items in the chain''s item master (drugs, frames, lenses, blanks, consumables)', true),
  ('inventory.read',             'inventory', 'View stores, stock by batch and expiry, and the stock ledger', false),
  ('inventory.store.manage',     'inventory', 'Open and retire stores at a centre', false),
  ('inventory.receive',          'inventory', 'Receive goods from a supplier into a store (goods receipt)', false),
  ('inventory.transfer',         'inventory', 'Send stock from one store to another', false),
  ('inventory.adjust.request',   'inventory', 'Request a stock adjustment (damage, expiry, count correction)', false),
  ('inventory.adjust.approve',   'inventory', 'Approve or reject stock adjustments requested by someone else', false),
  ('pharmacy.prescribe',         'pharmacy',  'Write medicine prescriptions for a patient', false),
  ('pharmacy.read',              'pharmacy',  'View prescriptions and pharmacy sales', false),
  ('pharmacy.dispense',          'pharmacy',  'Dispense medicines against a prescription or over the counter, raising the bill', false),
  ('pharmacy.return',            'pharmacy',  'Take back dispensed medicines into stock', false),
  ('optical.read',               'optical',   'View spectacle orders', false),
  ('optical.order',              'optical',   'Take spectacle orders: prescription, frame, lenses and price', false),
  ('optical.deliver',            'optical',   'Deliver finished spectacles to the patient', false),
  ('opticallab.read',            'opticallab','View optical manufacturing jobs', false),
  ('opticallab.process',         'opticallab','Move a manufacturing job through surfacing, coating, edging and fitting', false),
  ('opticallab.qc',              'opticallab','Quality-check finished spectacles (not your own fitting) and dispatch them', false);
