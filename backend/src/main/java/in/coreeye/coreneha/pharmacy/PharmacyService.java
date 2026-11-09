package in.coreeye.coreneha.pharmacy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import in.coreeye.coreneha.audit.AuditEntry;
import in.coreeye.coreneha.audit.AuditLog;
import in.coreeye.coreneha.billing.BillingDirectory;
import in.coreeye.coreneha.inventory.StockLedger;
import in.coreeye.coreneha.kernel.AccessControl;
import in.coreeye.coreneha.kernel.Actor;
import in.coreeye.coreneha.kernel.BusinessTime;
import in.coreeye.coreneha.kernel.NotFoundException;
import in.coreeye.coreneha.kernel.Permission;
import in.coreeye.coreneha.kernel.RuleViolationException;
import in.coreeye.coreneha.kernel.Scope;
import in.coreeye.coreneha.patient.PatientDirectory;
import in.coreeye.coreneha.pharmacy.PharmacyModels.PrescriptionLineView;
import in.coreeye.coreneha.pharmacy.PharmacyModels.PrescriptionView;
import in.coreeye.coreneha.pharmacy.PharmacyModels.SaleLineView;
import in.coreeye.coreneha.pharmacy.PharmacyModels.SaleView;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PharmacyService {

    /** Drugs that are dispensed only against a prescription written in the system. */
    static final Set<String> PRESCRIPTION_ONLY = Set.of("H", "H1", "X");
    private static final Set<String> PHARMACY_CATEGORIES = Set.of("DRUG", "CONSUMABLE", "CONTACT_LENS");

    static String userName(String column) {
        return "(select trim(wp.given_name || ' ' || wp.family_name) from iam.user_account ua "
                + "join workforce.person wp on wp.id = ua.person_id where ua.id = " + column + ")";
    }

    private static final String PRESCRIPTION_SELECT = """
            select r.id, r.centre_id, r.patient_id, p.uhid as patient_uhid, trim(p.given_name || ' ' || p.family_name) as patient_name,
                   r.visit_ref, r.prescription_no, r.status, r.notes, %s as prescribed_by_name, r.prescribed_at, r.version
            from pharmacy.prescription r join patient.patient p on p.id = r.patient_id""".formatted(userName("r.prescribed_by"));

    private static final String SALE_SELECT = """
            select s.id, s.centre_id, s.store_id, st.name as store_name, s.sale_no, s.patient_id, p.uhid as patient_uhid,
                   trim(p.given_name || ' ' || p.family_name) as patient_name, s.prescription_id, r.prescription_no, s.bill_id,
                   b.bill_no, b.status as bill_status, s.total_amount, s.returned_amount, %s as sold_by_name, s.sold_at
            from pharmacy.sale s join patient.patient p on p.id = s.patient_id join inventory.store st on st.id = s.store_id
            left join pharmacy.prescription r on r.id = s.prescription_id left join billing.bill b on b.id = s.bill_id"""
            .formatted(userName("s.sold_by"));

    record PrescriptionRow(UUID id, UUID centreId, UUID patientId, String patientUhid, String patientName, UUID visitRef,
                           String prescriptionNo, String status, String notes, String prescribedByName, java.time.Instant prescribedAt,
                           int version) {
    }

    record SaleRow(UUID id, UUID centreId, UUID storeId, String storeName, String saleNo, UUID patientId, String patientUhid,
                   String patientName, UUID prescriptionId, String prescriptionNo, UUID billId, String billNo, String billStatus,
                   BigDecimal totalAmount, BigDecimal returnedAmount, String soldByName, java.time.Instant soldAt) {
    }

    private final JdbcClient jdbc;
    private final AccessControl access;
    private final AuditLog audit;
    private final StockLedger stock;
    private final BillingDirectory billing;
    private final PatientDirectory patients;

    PharmacyService(JdbcClient jdbc, AccessControl access, AuditLog audit, StockLedger stock, BillingDirectory billing,
                    PatientDirectory patients) {
        this.jdbc = jdbc;
        this.access = access;
        this.audit = audit;
        this.stock = stock;
        this.billing = billing;
        this.patients = patients;
    }

    // ---------------------------------------------------------------- prescriptions

    @Transactional
    public PrescriptionView prescribe(Actor actor, UUID centreId, PharmacyModels.CreatePrescription cmd) {
        Scope scope = Scope.centre(centreId);
        access.require(actor, Permission.PHARMACY_PRESCRIBE, scope, "pharmacy.prescription.create", "patient", cmd.patientId().toString());
        var patient = activePatient(cmd.patientId());
        String no = stock.nextNumber(actor, centreId, "RX");
        UUID id = jdbc.sql("""
                insert into pharmacy.prescription (tenant_id, centre_id, patient_id, visit_ref, prescription_no, notes, prescribed_by)
                values (:t, :c, :p, :v, :no, :n, :by) returning id""")
                .param("t", actor.tenantId()).param("c", centreId).param("p", patient.id()).param("v", cmd.visitRef()).param("no", no)
                .param("n", cmd.notes()).param("by", actor.userId()).query(UUID.class).single();
        for (PharmacyModels.PrescriptionLineInput l : cmd.lines()) {
            var item = stock.item(l.itemId()).orElseThrow(() -> new NotFoundException("Medicine not found"));
            if (!PHARMACY_CATEGORIES.contains(item.category()) || !"ACTIVE".equals(item.status())) {
                throw new RuleViolationException("pharmacy.not-a-medicine", item.name() + " cannot be prescribed");
            }
            jdbc.sql("""
                    insert into pharmacy.prescription_line (tenant_id, prescription_id, item_id, dose, frequency, duration_days, eye, route,
                        quantity, instructions)
                    values (:t, :r, :i, :d, :f, :dur, :eye, :route, :q, :ins)""")
                    .param("t", actor.tenantId()).param("r", id).param("i", item.id()).param("d", l.dose()).param("f", l.frequency())
                    .param("dur", l.durationDays()).param("eye", l.eye()).param("route", l.route() == null ? "TOPICAL" : l.route())
                    .param("q", l.quantity()).param("ins", l.instructions()).update();
        }
        audit.record(AuditEntry.success(actor, "pharmacy.prescription.create", "prescription", id, scope,
                Map.of("prescriptionNo", no, "patient", patient.uhid(), "lines", cmd.lines().size())));
        return prescription(id);
    }

    public List<PrescriptionView> prescriptions(Actor actor, UUID centreId, String status) {
        requireRead(actor, centreId);
        Map<String, Object> p = new HashMap<>();
        p.put("c", centreId);
        p.put("s", status);
        return jdbc.sql(PRESCRIPTION_SELECT + """
                 where r.centre_id = :c and (cast(:s as text) is null or r.status = :s)
                   and r.prescribed_at > now() - interval '60 days'
                 order by r.prescribed_at desc limit 300""").params(p).query(PrescriptionRow.class).list()
                .stream().map(this::withLines).toList();
    }

    public List<PrescriptionView> prescriptionsForPatient(Actor actor, UUID patientId) {
        List<PrescriptionRow> rows = jdbc.sql(PRESCRIPTION_SELECT + " where r.patient_id = :p order by r.prescribed_at desc limit 100")
                .param("p", patientId).query(PrescriptionRow.class).list();
        return rows.stream().filter(r -> canRead(actor, r.centreId())).map(this::withLines).toList();
    }

    public PrescriptionView getPrescription(Actor actor, UUID id) {
        PrescriptionView v = prescription(id);
        requireRead(actor, v.centreId());
        return v;
    }

    @Transactional
    public PrescriptionView cancelPrescription(Actor actor, UUID id, String reason) {
        PrescriptionView v = prescription(id);
        access.require(actor, Permission.PHARMACY_PRESCRIBE, Scope.centre(v.centreId()), "pharmacy.prescription.cancel", "prescription",
                id.toString());
        if (!"OPEN".equals(v.status())) {
            throw new RuleViolationException("pharmacy.prescription.dispensed", "Medicines have been dispensed on this prescription");
        }
        jdbc.sql("update pharmacy.prescription set status = 'CANCELLED', notes = coalesce(notes || ' ', '') || :r, version = version + 1 where id = :id")
                .param("r", "Cancelled: " + reason).param("id", id).update();
        audit.record(AuditEntry.success(actor, "pharmacy.prescription.cancel", "prescription", id, Scope.centre(v.centreId()),
                Map.of("prescriptionNo", v.prescriptionNo(), "reason", reason)));
        return prescription(id);
    }

    private PrescriptionView prescription(UUID id) {
        return jdbc.sql(PRESCRIPTION_SELECT + " where r.id = :id").param("id", id).query(PrescriptionRow.class).optional()
                .map(this::withLines).orElseThrow(() -> new NotFoundException("Prescription not found"));
    }

    private PrescriptionView withLines(PrescriptionRow r) {
        List<PrescriptionLineView> lines = jdbc.sql("""
                select l.id, l.item_id, i.code as item_code, i.name as item_name, i.schedule, l.dose, l.frequency, l.duration_days, l.eye,
                       l.route, l.quantity, l.instructions, l.dispensed_qty
                from pharmacy.prescription_line l join inventory.item i on i.id = l.item_id where l.prescription_id = :r order by i.name""")
                .param("r", r.id()).query(PrescriptionLineView.class).list();
        return new PrescriptionView(r.id(), r.centreId(), r.patientId(), r.patientUhid(), r.patientName(), r.visitRef(), r.prescriptionNo(),
                r.status(), r.notes(), r.prescribedByName(), r.prescribedAt(), r.version(), lines);
    }

    // ---------------------------------------------------------------- dispensing

    @Transactional
    public SaleView dispense(Actor actor, PharmacyModels.Dispense cmd) {
        var store = stock.store(cmd.storeId()).orElseThrow(() -> new NotFoundException("Store not found"));
        if (!"PHARMACY".equals(store.storeType()) || !"ACTIVE".equals(store.status())) {
            throw new RuleViolationException("pharmacy.not-a-pharmacy", store.name() + " is not an active pharmacy store");
        }
        Scope scope = Scope.centre(store.centreId());
        access.require(actor, Permission.PHARMACY_DISPENSE, scope, "pharmacy.dispense", "patient", cmd.patientId().toString());
        var patient = activePatient(cmd.patientId());
        PrescriptionView rx = null;
        if (cmd.prescriptionId() != null) {
            rx = prescription(cmd.prescriptionId());
            if (!rx.patientId().equals(patient.id())) {
                throw new RuleViolationException("pharmacy.prescription.other-patient", "This prescription is for another patient");
            }
            if ("CANCELLED".equals(rx.status()) || "DISPENSED".equals(rx.status())) {
                throw new RuleViolationException("pharmacy.prescription.closed", "Prescription " + rx.prescriptionNo() + " is " + rx.status().toLowerCase());
            }
            if (rx.prescribedAt().isBefore(java.time.Instant.now().minus(java.time.Duration.ofDays(30)))) {
                throw new RuleViolationException("pharmacy.prescription.old", "Prescription " + rx.prescriptionNo() + " is older than 30 days; ask the doctor to renew it");
            }
        }
        String saleNo = stock.nextNumber(actor, store.centreId(), "PH");
        UUID saleId = jdbc.sql("""
                insert into pharmacy.sale (tenant_id, centre_id, store_id, sale_no, patient_id, prescription_id, total_amount, sold_by)
                values (:t, :c, :s, :no, :p, :r, 0, :by) returning id""")
                .param("t", actor.tenantId()).param("c", store.centreId()).param("s", store.id()).param("no", saleNo).param("p", patient.id())
                .param("r", cmd.prescriptionId()).param("by", actor.userId()).query(UUID.class).single();
        List<BillingDirectory.SourcedLine> billLines = new ArrayList<>();
        for (PharmacyModels.DispenseLineInput l : cmd.lines()) {
            var item = stock.item(l.itemId()).orElseThrow(() -> new NotFoundException("Medicine not found"));
            if (!PHARMACY_CATEGORIES.contains(item.category())) {
                throw new RuleViolationException("pharmacy.not-a-medicine", item.name() + " is not sold at the pharmacy");
            }
            PrescriptionLineView rxLine = null;
            if (l.prescriptionLineId() != null) {
                if (rx == null) {
                    throw new RuleViolationException("pharmacy.prescription.missing", "Choose the prescription for " + item.name());
                }
                rxLine = rx.lines().stream().filter(x -> x.id().equals(l.prescriptionLineId())).findFirst()
                        .orElseThrow(() -> new RuleViolationException("pharmacy.prescription.line", item.name() + " is not on this prescription"));
                if (!rxLine.itemId().equals(item.id())) {
                    throw new RuleViolationException("pharmacy.prescription.substitute", "Dispense the medicine as prescribed (" + rxLine.itemName() + ")");
                }
                int remaining = rxLine.quantity() - rxLine.dispensedQty();
                if (l.quantity() > remaining) {
                    throw new RuleViolationException("pharmacy.prescription.quantity",
                            "Only " + remaining + " of " + item.name() + " remain on the prescription");
                }
            } else if (item.schedule() != null && PRESCRIPTION_ONLY.contains(item.schedule())) {
                throw new RuleViolationException("pharmacy.prescription-only",
                        item.name() + " is a Schedule " + item.schedule() + " drug and needs a doctor's prescription");
            }
            for (StockLedger.Taken t : stock.takeFirstExpiry(actor, store.id(), item.id(), l.quantity(), "SALE", "PHARMACY_SALE", saleId, saleNo)) {
                BigDecimal amount = t.mrp().multiply(BigDecimal.valueOf(t.quantity()));
                UUID lineId = jdbc.sql("""
                        insert into pharmacy.sale_line (tenant_id, sale_id, item_id, batch_id, prescription_line_id, quantity, mrp, gst_rate, amount)
                        values (:t, :s, :i, :b, :rl, :q, :mrp, :g, :a) returning id""")
                        .param("t", actor.tenantId()).param("s", saleId).param("i", item.id()).param("b", t.batchId())
                        .param("rl", l.prescriptionLineId()).param("q", t.quantity()).param("mrp", t.mrp()).param("g", item.gstRate())
                        .param("a", amount).query(UUID.class).single();
                // MRP includes GST; billing adds GST on top, so the price before tax is MRP / (1 + rate).
                BigDecimal excl = t.mrp().multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(100).add(item.gstRate()), 2, RoundingMode.HALF_UP);
                billLines.add(new BillingDirectory.SourcedLine(item.name() + " (batch " + t.batchNo() + ")", "PHARMACY", t.quantity(), excl,
                        item.gstRate()));
            }
            if (rxLine != null) {
                jdbc.sql("update pharmacy.prescription_line set dispensed_qty = dispensed_qty + :q where id = :id")
                        .param("q", l.quantity()).param("id", rxLine.id()).update();
            }
        }
        if (rx != null) {
            jdbc.sql("""
                    update pharmacy.prescription r set version = version + 1, status = case
                        when not exists (select 1 from pharmacy.prescription_line l where l.prescription_id = r.id and l.dispensed_qty < l.quantity)
                        then 'DISPENSED' else 'PARTLY_DISPENSED' end
                    where id = :id""").param("id", rx.id()).update();
        }
        var bill = billing.raiseSourcedBill(actor, store.centreId(), patient.id(), rx == null ? null : rx.visitRef(), "PHARMACY_SALE",
                saleId, billLines);
        jdbc.sql("update pharmacy.sale set bill_id = :b, total_amount = :a where id = :id")
                .param("b", bill.id()).param("a", bill.netAmount()).param("id", saleId).update();
        audit.record(AuditEntry.success(actor, "pharmacy.dispense", "sale", saleId, scope,
                Map.of("saleNo", saleNo, "patient", patient.uhid(), "bill", bill.billNo(), "lines", cmd.lines().size(),
                        "prescription", rx == null ? "over the counter" : rx.prescriptionNo())));
        return sale(saleId);
    }

    @Transactional
    public SaleView returnToStock(Actor actor, UUID saleId, List<PharmacyModels.ReturnLine> lines) {
        SaleView sale = sale(saleId);
        Scope scope = Scope.centre(sale.centreId());
        access.require(actor, Permission.PHARMACY_RETURN, scope, "pharmacy.return", "sale", saleId.toString());
        if (sale.soldAt().isBefore(java.time.Instant.now().minus(java.time.Duration.ofDays(30)))) {
            throw new RuleViolationException("pharmacy.return.late", "Medicines can be returned only within 30 days of sale");
        }
        BigDecimal credit = BigDecimal.ZERO;
        for (PharmacyModels.ReturnLine r : lines) {
            SaleLineView line = sale.lines().stream().filter(x -> x.id().equals(r.saleLineId())).findFirst()
                    .orElseThrow(() -> new NotFoundException("Sale line not found"));
            if (r.quantity() > line.quantity() - line.returnedQty()) {
                throw new RuleViolationException("pharmacy.return.quantity", "Only " + (line.quantity() - line.returnedQty())
                        + " of " + line.itemName() + " can be returned");
            }
            if (line.expiryDate() != null && !line.expiryDate().isAfter(BusinessTime.today())) {
                throw new RuleViolationException("pharmacy.return.expired", line.itemName() + " has expired and cannot go back into stock");
            }
            UUID batchId = jdbc.sql("select batch_id from pharmacy.sale_line where id = :id").param("id", line.id()).query(UUID.class).single();
            stock.putBack(actor, batchId, r.quantity(), "SALE_RETURN", "PHARMACY_SALE", saleId, sale.saleNo(), r.reason());
            BigDecimal amount = line.mrp().multiply(BigDecimal.valueOf(r.quantity()));
            jdbc.sql("update pharmacy.sale_line set returned_qty = returned_qty + :q where id = :id").param("q", r.quantity())
                    .param("id", line.id()).update();
            jdbc.sql("""
                    insert into pharmacy.sale_return (tenant_id, sale_id, sale_line_id, quantity, amount, reason, returned_by)
                    values (:t, :s, :l, :q, :a, :r, :by)""").param("t", actor.tenantId()).param("s", saleId).param("l", line.id())
                    .param("q", r.quantity()).param("a", amount).param("r", r.reason()).param("by", actor.userId()).update();
            credit = credit.add(amount);
        }
        jdbc.sql("update pharmacy.sale set returned_amount = returned_amount + :a where id = :id").param("a", credit).param("id", saleId).update();
        audit.record(AuditEntry.success(actor, "pharmacy.return", "sale", saleId, scope,
                Map.of("saleNo", sale.saleNo(), "lines", lines.size(), "amount", credit.toPlainString())));
        return sale(saleId);
    }

    public List<SaleView> sales(Actor actor, UUID centreId, LocalDate date) {
        requireRead(actor, centreId);
        LocalDate d = date == null ? BusinessTime.today() : date;
        return jdbc.sql(SALE_SELECT + " where s.centre_id = :c and (s.sold_at at time zone 'Asia/Kolkata')::date = :d order by s.sold_at desc")
                .param("c", centreId).param("d", d).query(SaleRow.class).list().stream().map(this::withLines).toList();
    }

    public SaleView getSale(Actor actor, UUID saleId) {
        SaleView s = sale(saleId);
        requireRead(actor, s.centreId());
        return s;
    }

    private SaleView sale(UUID id) {
        return jdbc.sql(SALE_SELECT + " where s.id = :id").param("id", id).query(SaleRow.class).optional().map(this::withLines)
                .orElseThrow(() -> new NotFoundException("Sale not found"));
    }

    private SaleView withLines(SaleRow s) {
        List<SaleLineView> lines = jdbc.sql("""
                select l.id, l.item_id, i.code as item_code, i.name as item_name, b.batch_no, b.expiry_date, l.quantity, l.mrp, l.gst_rate,
                       l.amount, l.returned_qty
                from pharmacy.sale_line l join inventory.item i on i.id = l.item_id join inventory.stock_batch b on b.id = l.batch_id
                where l.sale_id = :s order by i.name""").param("s", s.id()).query(SaleLineView.class).list();
        return new SaleView(s.id(), s.centreId(), s.storeId(), s.storeName(), s.saleNo(), s.patientId(), s.patientUhid(), s.patientName(),
                s.prescriptionId(), s.prescriptionNo(), s.billId(), s.billNo(), s.billStatus(), s.totalAmount(), s.returnedAmount(),
                s.soldByName(), s.soldAt(), lines);
    }

    // ---------------------------------------------------------------- helpers

    private PatientDirectory.PatientRef activePatient(UUID patientId) {
        var p = patients.patient(patientId).orElseThrow(() -> new NotFoundException("Patient not found"));
        if (!"ACTIVE".equals(p.status())) {
            throw new RuleViolationException("patient.inactive", "The patient record is inactive");
        }
        return p;
    }

    private boolean canRead(Actor actor, UUID centreId) {
        Scope s = Scope.centre(centreId);
        return access.holds(actor, Permission.PHARMACY_READ, s) || access.holds(actor, Permission.PHARMACY_PRESCRIBE, s)
                || access.holds(actor, Permission.PHARMACY_DISPENSE, s);
    }

    private void requireRead(Actor actor, UUID centreId) {
        if (!canRead(actor, centreId)) {
            access.require(actor, Permission.PHARMACY_READ, Scope.centre(centreId), "pharmacy.read", "centre", centreId.toString());
        }
    }
}
