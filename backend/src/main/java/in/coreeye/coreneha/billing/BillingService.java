package in.coreeye.coreneha.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import in.coreeye.coreneha.audit.AuditEntry;
import in.coreeye.coreneha.audit.AuditLog;
import in.coreeye.coreneha.billing.BillingModels.BillDetail;
import in.coreeye.coreneha.billing.BillingModels.BillLineView;
import in.coreeye.coreneha.billing.BillingModels.BillView;
import in.coreeye.coreneha.billing.BillingModels.PaymentView;
import in.coreeye.coreneha.billing.BillingModels.TariffItemView;
import in.coreeye.coreneha.kernel.AccessControl;
import in.coreeye.coreneha.kernel.Actor;
import in.coreeye.coreneha.kernel.BusinessTime;
import in.coreeye.coreneha.kernel.ConflictException;
import in.coreeye.coreneha.kernel.NotFoundException;
import in.coreeye.coreneha.kernel.Permission;
import in.coreeye.coreneha.kernel.RuleViolationException;
import in.coreeye.coreneha.kernel.Scope;
import in.coreeye.coreneha.org.OrgDirectory;
import in.coreeye.coreneha.patient.PatientDirectory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Price list, bills, discounts and payments of a centre. Rules:
 * charges come only from the centre's active price list; a discount is applied
 * only after someone other than the requester approves it; no payment is taken
 * while a discount waits for a decision; a bill closes (PAID) when its balance is
 * zero; only an unpaid bill can be cancelled, and only by an approver.
 */
@Service
public class BillingService implements BillingDirectory {

    static final Set<BigDecimal> GST_RATES = Set.of(new BigDecimal("0"), new BigDecimal("5"), new BigDecimal("12"),
            new BigDecimal("18"), new BigDecimal("28"));

    /** Display name of a staff user, for read models. */
    static String userName(String column) {
        return "(select trim(wp.given_name || ' ' || wp.family_name) from iam.user_account ua "
                + "join workforce.person wp on wp.id = ua.person_id where ua.id = " + column + ")";
    }

    static final String BILL_SELECT = """
            select b.id, b.centre_id, c.name as centre_name, b.patient_id, p.uhid as patient_uhid,
                   trim(p.given_name || ' ' || p.family_name) as patient_name, b.bill_no, b.bill_date, b.visit_ref, b.surgery_ref,
                   b.status, b.gross_amount, b.tax_amount, b.discount_amount, b.net_amount, b.paid_amount,
                   (b.net_amount - b.paid_amount) as balance, b.discount_requested, b.discount_reason, b.discount_status,
                   %s as discount_requested_by_name, %s as discount_decided_by_name, b.discount_decision_note, b.cancel_reason,
                   %s as created_by_name, b.created_at, b.version
            from billing.bill b join org.centre c on c.id = b.centre_id join patient.patient p on p.id = b.patient_id
            """.formatted(userName("b.discount_requested_by"), userName("b.discount_decided_by"), userName("b.created_by"));

    private final JdbcClient jdbc;
    private final AccessControl access;
    private final AuditLog audit;
    private final OrgDirectory org;
    private final PatientDirectory patients;

    BillingService(JdbcClient jdbc, AccessControl access, AuditLog audit, OrgDirectory org, PatientDirectory patients) {
        this.jdbc = jdbc;
        this.access = access;
        this.audit = audit;
        this.org = org;
        this.patients = patients;
    }

    // ---------------------------------------------------------------- directory

    @Override
    public List<BillSummary> billsForVisit(UUID visitId) {
        return jdbc.sql("""
                select id, bill_no, status, net_amount, paid_amount, net_amount - paid_amount as balance, discount_status
                from billing.bill where visit_ref = :v order by created_at
                """).param("v", visitId).query(BillSummary.class).list();
    }

    @Override
    public List<BillSummary> billsForSurgery(UUID surgeryId) {
        return jdbc.sql("""
                select id, bill_no, status, net_amount, paid_amount, net_amount - paid_amount as balance, discount_status
                from billing.bill where surgery_ref = :s order by created_at
                """).param("s", surgeryId).query(BillSummary.class).list();
    }

    @Override
    public java.util.Optional<TariffRef> tariffRef(UUID tariffItemId) {
        return jdbc.sql("select id, centre_id, code, name, category, price, status from billing.tariff_item where id = :id")
                .param("id", tariffItemId).query(TariffRef.class).optional();
    }

    // ---------------------------------------------------------------- tariff

    @Transactional(readOnly = true)
    public List<TariffItemView> tariff(Actor actor, UUID centreId, boolean includeRetired) {
        Scope scope = activeCentre(centreId);
        if (!access.holds(actor, Permission.BILLING_READ, scope) && !access.holds(actor, Permission.BILLING_TARIFF_MANAGE, scope)
                && !access.holds(actor, Permission.EYE_COUNSELLING_RECORD, scope)) {
            access.require(actor, Permission.BILLING_READ, scope, "billing.tariff.list", "tariff_item", null);
        }
        return jdbc.sql("""
                select id, centre_id, code, name, category, price, gst_rate, status, updated_at, version
                from billing.tariff_item where centre_id = :c and (:all or status = 'ACTIVE') order by category, name
                """).param("c", centreId).param("all", includeRetired).query(TariffItemView.class).list();
    }

    @Transactional
    public TariffItemView createTariffItem(Actor actor, UUID centreId, BillingModels.CreateTariffItem cmd) {
        Scope scope = activeCentre(centreId);
        access.require(actor, Permission.BILLING_TARIFF_MANAGE, scope, "billing.tariff.create", "tariff_item", null);
        checkGst(cmd.gstRate());
        UUID id;
        try {
            id = jdbc.sql("""
                    insert into billing.tariff_item (tenant_id, centre_id, code, name, category, price, gst_rate, updated_by)
                    values (:t, :c, :code, :name, :cat, :price, :gst, :by) returning id
                    """).param("t", actor.tenantId()).param("c", centreId).param("code", cmd.code()).param("name", cmd.name().trim())
                    .param("cat", cmd.category()).param("price", money(cmd.price())).param("gst", cmd.gstRate())
                    .param("by", actor.userId()).query(UUID.class).single();
        } catch (DuplicateKeyException e) {
            throw new ConflictException("billing.tariff.duplicate", "This centre already has a price list item with this code");
        }
        audit.record(AuditEntry.success(actor, "billing.tariff.create", "tariff_item", id, scope,
                Map.of("code", cmd.code(), "price", cmd.price().toPlainString())));
        return tariffItem(id);
    }

    @Transactional
    public TariffItemView updateTariffItem(Actor actor, UUID centreId, UUID itemId, BillingModels.UpdateTariffItem cmd) {
        Scope scope = activeCentre(centreId);
        access.require(actor, Permission.BILLING_TARIFF_MANAGE, scope, "billing.tariff.update", "tariff_item", itemId.toString());
        checkGst(cmd.gstRate());
        TariffItemView before = tariffItem(itemId);
        if (!before.centreId().equals(centreId)) {
            throw new NotFoundException("Price list item not found");
        }
        int n = jdbc.sql("""
                update billing.tariff_item set name = :name, price = :price, gst_rate = :gst, status = :status,
                    updated_by = :by, updated_at = now(), version = version + 1
                where id = :id and version = :v
                """).param("name", cmd.name().trim()).param("price", money(cmd.price())).param("gst", cmd.gstRate())
                .param("status", cmd.status()).param("by", actor.userId()).param("id", itemId).param("v", cmd.expectedVersion())
                .update();
        if (n == 0) {
            throw new ConflictException("billing.tariff.changed", "Someone else changed this item. Reload and try again.");
        }
        audit.record(AuditEntry.success(actor, "billing.tariff.update", "tariff_item", itemId, scope,
                Map.of("code", before.code(), "priceBefore", before.price().toPlainString(), "priceAfter", cmd.price().toPlainString(),
                        "status", cmd.status())));
        return tariffItem(itemId);
    }

    private TariffItemView tariffItem(UUID id) {
        return jdbc.sql("select id, centre_id, code, name, category, price, gst_rate, status, updated_at, version from billing.tariff_item where id = :id")
                .param("id", id).query(TariffItemView.class).optional().orElseThrow(() -> new NotFoundException("Price list item not found"));
    }

    // ---------------------------------------------------------------- bills

    @Transactional
    public BillDetail createBill(Actor actor, UUID centreId, BillingModels.CreateBill cmd) {
        Scope scope = activeCentre(centreId);
        access.require(actor, Permission.BILLING_CHARGE, scope, "billing.bill.create", "bill", null);
        var patient = patients.patient(cmd.patientId()).orElseThrow(() -> new NotFoundException("Patient not found"));
        if (!"ACTIVE".equals(patient.status())) {
            throw new RuleViolationException("patient.inactive", "The patient record is inactive");
        }
        LocalDate today = BusinessTime.today();
        String period = financialYear(today);
        int seq = jdbc.sql("""
                insert into billing.counter (tenant_id, centre_id, period, last_value) values (:t, :c, :p, 1)
                on conflict (centre_id, period) do update set last_value = billing.counter.last_value + 1 returning last_value
                """).param("t", actor.tenantId()).param("c", centreId).param("p", period).query(Integer.class).single();
        String centreCode = org.centre(centreId).orElseThrow().code();
        String billNo = centreCode + "/" + period + "/" + String.format("%06d", seq);
        UUID id = jdbc.sql("""
                insert into billing.bill (tenant_id, centre_id, patient_id, bill_no, bill_date, visit_ref, surgery_ref, created_by)
                values (:t, :c, :p, :no, :d, :v, :s, :by) returning id
                """).param("t", actor.tenantId()).param("c", centreId).param("p", cmd.patientId()).param("no", billNo)
                .param("d", today).param("v", cmd.visitRef()).param("s", cmd.surgeryRef()).param("by", actor.userId())
                .query(UUID.class).single();
        insertLines(actor, centreId, id, cmd.lines());
        recompute(id);
        audit.record(AuditEntry.success(actor, "billing.bill.create", "bill", id, scope,
                Map.of("billNo", billNo, "patient", patient.uhid(), "lines", cmd.lines().size())));
        return detailRow(id);
    }

    @Transactional
    public BillDetail addLines(Actor actor, UUID billId, BillingModels.AddLines cmd) {
        BillView bill = billRow(billId);
        Scope scope = Scope.centre(bill.centreId());
        access.require(actor, Permission.BILLING_CHARGE, scope, "billing.bill.add-lines", "bill", billId.toString());
        requireOpen(bill, cmd.expectedVersion());
        if ("PENDING".equals(bill.discountStatus())) {
            throw new RuleViolationException("billing.discount-pending", "A discount is waiting for approval; decide it before changing charges");
        }
        insertLines(actor, bill.centreId(), billId, cmd.lines());
        bump(billId);
        recompute(billId);
        audit.record(AuditEntry.success(actor, "billing.bill.add-lines", "bill", billId, scope,
                Map.of("billNo", bill.billNo(), "lines", cmd.lines().size())));
        return detailRow(billId);
    }

    @Transactional
    public BillDetail removeLine(Actor actor, UUID billId, BillingModels.RemoveLine cmd) {
        BillView bill = billRow(billId);
        Scope scope = Scope.centre(bill.centreId());
        access.require(actor, Permission.BILLING_CHARGE, scope, "billing.bill.remove-line", "bill", billId.toString());
        requireOpen(bill, cmd.expectedVersion());
        if (bill.paidAmount().signum() > 0 || !"NONE".equals(bill.discountStatus()) && !"REJECTED".equals(bill.discountStatus())) {
            throw new RuleViolationException("billing.bill.locked", "Charges cannot be removed after a payment or a discount");
        }
        String description = jdbc.sql("delete from billing.bill_line where id = :l and bill_id = :b returning description")
                .param("l", cmd.lineId()).param("b", billId).query(String.class).optional()
                .orElseThrow(() -> new NotFoundException("Bill line not found"));
        bump(billId);
        recompute(billId);
        audit.record(AuditEntry.success(actor, "billing.bill.remove-line", "bill", billId, scope,
                Map.of("billNo", bill.billNo(), "line", description)));
        return detailRow(billId);
    }

    @Transactional
    public BillDetail requestDiscount(Actor actor, UUID billId, BillingModels.RequestDiscount cmd) {
        BillView bill = billRow(billId);
        Scope scope = Scope.centre(bill.centreId());
        access.require(actor, Permission.BILLING_CHARGE, scope, "billing.discount.request", "bill", billId.toString());
        requireOpen(bill, cmd.expectedVersion());
        if ("PENDING".equals(bill.discountStatus()) || "APPROVED".equals(bill.discountStatus())) {
            throw new RuleViolationException("billing.discount.exists", "This bill already has a discount");
        }
        BigDecimal maximum = bill.grossAmount().add(bill.taxAmount()).subtract(bill.paidAmount());
        if (money(cmd.amount()).compareTo(maximum) > 0) {
            throw new RuleViolationException("billing.discount.too-large", "The discount cannot exceed the unpaid amount (₹" + maximum + ")");
        }
        jdbc.sql("""
                update billing.bill set discount_requested = :a, discount_reason = :r, discount_status = 'PENDING',
                    discount_requested_by = :by, discount_decided_by = null, discount_decision_note = null, version = version + 1
                where id = :id
                """).param("a", money(cmd.amount())).param("r", cmd.reason().trim()).param("by", actor.userId()).param("id", billId).update();
        audit.record(AuditEntry.success(actor, "billing.discount.request", "bill", billId, scope,
                Map.of("billNo", bill.billNo(), "amount", cmd.amount().toPlainString(), "reason", cmd.reason())));
        return detailRow(billId);
    }

    @Transactional
    public BillDetail decideDiscount(Actor actor, UUID billId, BillingModels.ApprovalDecision cmd) {
        BillView bill = billRow(billId);
        Scope scope = Scope.centre(bill.centreId());
        access.require(actor, Permission.BILLING_DISCOUNT_APPROVE, scope, "billing.discount.decide", "bill", billId.toString());
        requireOpen(bill, cmd.expectedVersion());
        if (!"PENDING".equals(bill.discountStatus())) {
            throw new RuleViolationException("billing.discount.not-pending", "There is no discount waiting for a decision");
        }
        UUID requestedBy = jdbc.sql("select discount_requested_by from billing.bill where id = :id").param("id", billId)
                .query(UUID.class).single();
        if (actor.userId().equals(requestedBy)) {
            throw new RuleViolationException("billing.discount.self-approval", "You cannot decide a discount you requested");
        }
        if (cmd.approve()) {
            jdbc.sql("""
                    update billing.bill set discount_status = 'APPROVED', discount_amount = discount_requested,
                        discount_decided_by = :by, discount_decision_note = :n, version = version + 1 where id = :id
                    """).param("by", actor.userId()).param("n", cmd.note()).param("id", billId).update();
        } else {
            jdbc.sql("""
                    update billing.bill set discount_status = 'REJECTED', discount_amount = 0,
                        discount_decided_by = :by, discount_decision_note = :n, version = version + 1 where id = :id
                    """).param("by", actor.userId()).param("n", cmd.note()).param("id", billId).update();
        }
        recompute(billId);
        audit.record(AuditEntry.success(actor, cmd.approve() ? "billing.discount.approve" : "billing.discount.reject", "bill",
                billId, scope, Map.of("billNo", bill.billNo(), "amount", String.valueOf(bill.discountRequested()))));
        return detailRow(billId);
    }

    @Transactional
    public BillDetail collect(Actor actor, UUID billId, BillingModels.CollectPayment cmd) {
        BillView bill = billRow(billId);
        Scope scope = Scope.centre(bill.centreId());
        access.require(actor, Permission.BILLING_COLLECT, scope, "billing.payment.collect", "bill", billId.toString());
        requireOpen(bill, cmd.expectedVersion());
        if ("PENDING".equals(bill.discountStatus())) {
            throw new RuleViolationException("billing.discount-pending", "A discount is waiting for approval; collect after the decision");
        }
        BigDecimal amount = money(cmd.amount());
        if (amount.compareTo(bill.balance()) > 0) {
            throw new RuleViolationException("billing.payment.too-large", "The amount is more than the balance (₹" + bill.balance() + ")");
        }
        String reference = cmd.reference() == null || cmd.reference().isBlank() ? null : cmd.reference().trim();
        if (!"CASH".equals(cmd.mode()) && reference == null) {
            throw new RuleViolationException("billing.payment.reference", "Enter the transaction or approval reference for " + cmd.mode());
        }
        UUID paymentId = jdbc.sql("""
                insert into billing.payment (tenant_id, centre_id, bill_id, business_date, mode, amount, reference, received_by)
                values (:t, :c, :b, :d, :m, :a, :r, :by) returning id
                """).param("t", actor.tenantId()).param("c", bill.centreId()).param("b", billId).param("d", BusinessTime.today())
                .param("m", cmd.mode()).param("a", amount).param("r", reference).param("by", actor.userId())
                .query(UUID.class).single();
        jdbc.sql("update billing.bill set paid_amount = paid_amount + :a, version = version + 1 where id = :id")
                .param("a", amount).param("id", billId).update();
        recompute(billId);
        audit.record(AuditEntry.success(actor, "billing.payment.collect", "bill", billId, scope,
                Map.of("billNo", bill.billNo(), "mode", cmd.mode(), "amount", amount.toPlainString(), "paymentId", paymentId.toString())));
        return detailRow(billId);
    }

    @Transactional
    public BillDetail cancel(Actor actor, UUID billId, BillingModels.CancelBill cmd) {
        BillView bill = billRow(billId);
        Scope scope = Scope.centre(bill.centreId());
        access.require(actor, Permission.BILLING_DISCOUNT_APPROVE, scope, "billing.bill.cancel", "bill", billId.toString());
        requireOpen(bill, cmd.expectedVersion());
        if (bill.paidAmount().signum() > 0) {
            throw new RuleViolationException("billing.bill.has-payments", "A bill with payments cannot be cancelled");
        }
        jdbc.sql("update billing.bill set status = 'CANCELLED', cancel_reason = :r, closed_at = now(), version = version + 1 where id = :id")
                .param("r", cmd.reason().trim()).param("id", billId).update();
        audit.record(AuditEntry.success(actor, "billing.bill.cancel", "bill", billId, scope,
                Map.of("billNo", bill.billNo(), "reason", cmd.reason())));
        return detailRow(billId);
    }

    // ---------------------------------------------------------------- bill queries

    @Transactional(readOnly = true)
    public List<BillView> bills(Actor actor, UUID centreId, LocalDate from, LocalDate to, String status) {
        Scope scope = Scope.centre(centreId);
        access.require(actor, Permission.BILLING_READ, scope, "billing.bill.list", "bill", null);
        LocalDate f = from == null ? BusinessTime.today() : from;
        LocalDate t = to == null ? f : to;
        return jdbc.sql(BILL_SELECT + """
                 where b.centre_id = :c and b.bill_date between :f and :t and (cast(:s as text) is null or b.status = :s)
                 order by b.created_at desc limit 500
                """).param("c", centreId).param("f", f).param("t", t).param("s", status).query(BillView.class).list();
    }

    @Transactional(readOnly = true)
    public List<BillView> pendingDiscounts(Actor actor, UUID centreId) {
        access.require(actor, Permission.BILLING_DISCOUNT_APPROVE, Scope.centre(centreId), "billing.discount.list", "bill", null);
        return jdbc.sql(BILL_SELECT + " where b.centre_id = :c and b.discount_status = 'PENDING' and b.status = 'OPEN' order by b.created_at")
                .param("c", centreId).query(BillView.class).list();
    }

    @Transactional(readOnly = true)
    public BillDetail bill(Actor actor, UUID billId) {
        BillView bill = billRow(billId);
        access.require(actor, Permission.BILLING_READ, Scope.centre(bill.centreId()), "billing.bill.view", "bill", billId.toString());
        return detailRow(billId);
    }

    @Transactional(readOnly = true)
    public List<BillView> billsForPatient(Actor actor, UUID patientId) {
        Set<UUID> centres = access.centresWhere(actor, Permission.BILLING_READ);
        if (centres.isEmpty()) {
            access.requireAnywhere(actor, Permission.BILLING_READ, "billing.bill.list", "bill");
        }
        return jdbc.sql(BILL_SELECT + " where b.patient_id = :p order by b.created_at desc").param("p", patientId)
                .query(BillView.class).list().stream().filter(b -> centres.contains(b.centreId())).toList();
    }

    BillView billRow(UUID id) {
        return jdbc.sql(BILL_SELECT + " where b.id = :id").param("id", id).query(BillView.class).optional()
                .orElseThrow(() -> new NotFoundException("Bill not found"));
    }

    private BillDetail detailRow(UUID id) {
        List<BillLineView> lines = jdbc.sql("""
                select id, tariff_item_id, description, category, quantity, unit_price, gst_rate, amount, tax_amount
                from billing.bill_line where bill_id = :b order by added_at, id
                """).param("b", id).query(BillLineView.class).list();
        List<PaymentView> payments = jdbc.sql("""
                select id, mode, amount, reference, business_date, %s as received_by_name, received_at
                from billing.payment where bill_id = :b order by received_at
                """.formatted(userName("received_by"))).param("b", id).query(PaymentView.class).list();
        return new BillDetail(billRow(id), lines, payments);
    }

    // ---------------------------------------------------------------- helpers

    private void insertLines(Actor actor, UUID centreId, UUID billId, List<BillingModels.LineInput> lines) {
        for (BillingModels.LineInput line : lines) {
            TariffItemView item = jdbc.sql("""
                    select id, centre_id, code, name, category, price, gst_rate, status, updated_at, version
                    from billing.tariff_item where id = :id
                    """).param("id", line.tariffItemId()).query(TariffItemView.class).optional()
                    .orElseThrow(() -> new NotFoundException("Price list item not found"));
            if (!item.centreId().equals(centreId) || !"ACTIVE".equals(item.status())) {
                throw new RuleViolationException("billing.tariff.unavailable", item.name() + " is not on this centre's active price list");
            }
            BigDecimal amount = money(item.price().multiply(BigDecimal.valueOf(line.quantity())));
            BigDecimal tax = money(amount.multiply(item.gstRate()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
            jdbc.sql("""
                    insert into billing.bill_line (tenant_id, bill_id, tariff_item_id, description, category, quantity, unit_price,
                        gst_rate, amount, tax_amount, added_by)
                    values (:t, :b, :i, :d, :c, :q, :p, :g, :a, :x, :by)
                    """).param("t", actor.tenantId()).param("b", billId).param("i", item.id()).param("d", item.name())
                    .param("c", item.category()).param("q", line.quantity()).param("p", item.price()).param("g", item.gstRate())
                    .param("a", amount).param("x", tax).param("by", actor.userId()).update();
        }
    }

    /** Totals from the lines; closes the bill when something was charged (or all charges are free) and nothing is due. */
    private void recompute(UUID billId) {
        jdbc.sql("""
                update billing.bill b set
                    gross_amount = coalesce((select sum(amount) from billing.bill_line where bill_id = b.id), 0),
                    tax_amount = coalesce((select sum(tax_amount) from billing.bill_line where bill_id = b.id), 0)
                where b.id = :id
                """).param("id", billId).update();
        jdbc.sql("update billing.bill set net_amount = gross_amount + tax_amount - discount_amount where id = :id")
                .param("id", billId).update();
        jdbc.sql("""
                update billing.bill set status = 'PAID', closed_at = now()
                where id = :id and status = 'OPEN' and paid_amount >= net_amount and discount_status <> 'PENDING'
                  and exists (select 1 from billing.bill_line where bill_id = :id)
                """).param("id", billId).update();
    }

    private void bump(UUID billId) {
        jdbc.sql("update billing.bill set version = version + 1 where id = :id").param("id", billId).update();
    }

    private static void requireOpen(BillView bill, Integer expectedVersion) {
        if (!"OPEN".equals(bill.status())) {
            throw new RuleViolationException("billing.bill.closed", "Bill " + bill.billNo() + " is " + bill.status().toLowerCase() + "; raise a new bill instead");
        }
        if (expectedVersion != null && expectedVersion != bill.version()) {
            throw new ConflictException("billing.bill.changed", "Someone else changed this bill. Reload and try again.");
        }
    }

    private Scope activeCentre(UUID centreId) {
        var c = org.centre(centreId).orElseThrow(() -> new NotFoundException("Centre not found"));
        if (!"ACTIVE".equals(c.status())) {
            throw new RuleViolationException("org.centre.retired", "The centre is retired");
        }
        return Scope.centre(centreId);
    }

    private static void checkGst(BigDecimal rate) {
        if (GST_RATES.stream().noneMatch(r -> r.compareTo(rate) == 0)) {
            throw new RuleViolationException("billing.gst-rate", "GST rate must be 0, 5, 12, 18 or 28 per cent");
        }
    }

    static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    /** Indian financial year label, April to March: 2026-11-04 is in 2026-27. */
    static String financialYear(LocalDate d) {
        int start = d.getMonthValue() >= 4 ? d.getYear() : d.getYear() - 1;
        return start + "-" + String.format("%02d", (start + 1) % 100);
    }
}
