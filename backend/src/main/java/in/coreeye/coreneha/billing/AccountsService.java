package in.coreeye.coreneha.billing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import in.coreeye.coreneha.audit.AuditEntry;
import in.coreeye.coreneha.audit.AuditLog;
import in.coreeye.coreneha.billing.BillingModels.AmountRow;
import in.coreeye.coreneha.billing.BillingModels.BillView;
import in.coreeye.coreneha.billing.BillingModels.CollectionReport;
import in.coreeye.coreneha.billing.BillingModels.DayClosePreview;
import in.coreeye.coreneha.billing.BillingModels.DayCloseView;
import in.coreeye.coreneha.billing.BillingModels.DayRow;
import in.coreeye.coreneha.kernel.AccessControl;
import in.coreeye.coreneha.kernel.Actor;
import in.coreeye.coreneha.kernel.BusinessTime;
import in.coreeye.coreneha.kernel.ConflictException;
import in.coreeye.coreneha.kernel.NotFoundException;
import in.coreeye.coreneha.kernel.Permission;
import in.coreeye.coreneha.kernel.RuleViolationException;
import in.coreeye.coreneha.kernel.Scope;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Collection reports and the daily cash reconciliation (submitted by one person, approved by another). */
@Service
public class AccountsService {

    private static final String DAY_CLOSE_SELECT = """
            select id, centre_id, business_date, system_cash, system_card, system_upi, system_other, counted_cash, variance,
                   notes, status, %s as submitted_by_name, submitted_at, %s as decided_by_name, decided_at, decision_note, version
            from billing.day_close
            """.formatted(BillingService.userName("submitted_by"), BillingService.userName("decided_by"));

    private final JdbcClient jdbc;
    private final AccessControl access;
    private final AuditLog audit;

    AccountsService(JdbcClient jdbc, AccessControl access, AuditLog audit) {
        this.jdbc = jdbc;
        this.access = access;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public CollectionReport collections(Actor actor, UUID centreId, LocalDate from, LocalDate to) {
        access.require(actor, Permission.BILLING_REPORT_READ, Scope.centre(centreId), "billing.report.collections", "centre",
                centreId.toString());
        LocalDate f = from == null ? BusinessTime.today() : from;
        LocalDate t = to == null ? f : to;
        if (t.isBefore(f) || f.plusDays(366).isBefore(t)) {
            throw new RuleViolationException("billing.report.range", "Choose a date range of up to one year");
        }
        var totals = jdbc.sql("""
                select coalesce(sum(gross_amount + tax_amount), 0) as billed, coalesce(sum(discount_amount), 0) as discounts,
                       count(*) as bills
                from billing.bill where centre_id = :c and bill_date between :f and :t and status <> 'CANCELLED'
                """).param("c", centreId).param("f", f).param("t", t)
                .query((rs, i) -> new Object[] {rs.getBigDecimal("billed"), rs.getBigDecimal("discounts"), rs.getInt("bills")}).single();
        BigDecimal collected = jdbc.sql("""
                select coalesce(sum(amount), 0) from billing.payment where centre_id = :c and business_date between :f and :t
                """).param("c", centreId).param("f", f).param("t", t).query(BigDecimal.class).single();
        BigDecimal outstanding = jdbc.sql("""
                select coalesce(sum(net_amount - paid_amount), 0) from billing.bill where centre_id = :c and status = 'OPEN'
                """).param("c", centreId).query(BigDecimal.class).single();
        int pending = jdbc.sql("select count(*) from billing.bill where centre_id = :c and status = 'OPEN' and discount_status = 'PENDING'")
                .param("c", centreId).query(Integer.class).single();
        List<AmountRow> byMode = jdbc.sql("""
                select mode as label, sum(amount) as amount, count(*)::int as count from billing.payment
                where centre_id = :c and business_date between :f and :t group by mode order by sum(amount) desc
                """).param("c", centreId).param("f", f).param("t", t).query(AmountRow.class).list();
        List<AmountRow> byCategory = jdbc.sql("""
                select l.category as label, sum(l.amount + l.tax_amount) as amount, count(*)::int as count
                from billing.bill_line l join billing.bill b on b.id = l.bill_id
                where b.centre_id = :c and b.bill_date between :f and :t and b.status <> 'CANCELLED'
                group by l.category order by sum(l.amount + l.tax_amount) desc
                """).param("c", centreId).param("f", f).param("t", t).query(AmountRow.class).list();
        List<DayRow> byDay = jdbc.sql("""
                select d::date as date,
                       coalesce((select sum(gross_amount + tax_amount - discount_amount) from billing.bill
                                 where centre_id = :c and bill_date = d::date and status <> 'CANCELLED'), 0) as billed,
                       coalesce((select sum(amount) from billing.payment where centre_id = :c and business_date = d::date), 0) as collected,
                       (select count(*)::int from billing.bill where centre_id = :c and bill_date = d::date and status <> 'CANCELLED') as bills
                from generate_series(cast(:f as date), cast(:t as date), interval '1 day') d order by d
                """).param("c", centreId).param("f", f).param("t", t).query(DayRow.class).list();
        List<AmountRow> byCollector = jdbc.sql("""
                select %s as label, sum(amount) as amount, count(*)::int as count from billing.payment
                where centre_id = :c and business_date between :f and :t group by received_by order by sum(amount) desc
                """.formatted(BillingService.userName("received_by"))).param("c", centreId).param("f", f).param("t", t)
                .query(AmountRow.class).list();
        List<BillView> outstandingBills = jdbc.sql(BillingService.BILL_SELECT
                        + " where b.centre_id = :c and b.status = 'OPEN' and b.net_amount > b.paid_amount order by b.created_at limit 200")
                .param("c", centreId).query(BillView.class).list();
        BigDecimal billed = (BigDecimal) totals[0];
        BigDecimal discounts = (BigDecimal) totals[1];
        return new CollectionReport(centreId, f, t, billed, discounts, collected, outstanding, (Integer) totals[2], pending,
                byMode, byCategory, byDay, byCollector, outstandingBills);
    }

    @Transactional(readOnly = true)
    public DayClosePreview preview(Actor actor, UUID centreId, LocalDate date) {
        Scope scope = Scope.centre(centreId);
        if (!access.holds(actor, Permission.BILLING_DAYCLOSE_SUBMIT, scope)) {
            access.require(actor, Permission.BILLING_DAYCLOSE_APPROVE, scope, "billing.dayclose.preview", "day_close", null);
        }
        LocalDate d = date == null ? BusinessTime.today() : date;
        BigDecimal[] sums = systemTotals(centreId, d);
        int count = jdbc.sql("select count(*) from billing.payment where centre_id = :c and business_date = :d")
                .param("c", centreId).param("d", d).query(Integer.class).single();
        DayCloseView existing = jdbc.sql(DAY_CLOSE_SELECT + " where centre_id = :c and business_date = :d and status <> 'REJECTED'")
                .param("c", centreId).param("d", d).query(DayCloseView.class).optional().orElse(null);
        return new DayClosePreview(d, sums[0], sums[1], sums[2], sums[3], count, existing);
    }

    @Transactional(readOnly = true)
    public List<DayCloseView> dayCloses(Actor actor, UUID centreId) {
        Scope scope = Scope.centre(centreId);
        if (!access.holds(actor, Permission.BILLING_DAYCLOSE_SUBMIT, scope) && !access.holds(actor, Permission.BILLING_DAYCLOSE_APPROVE, scope)) {
            access.require(actor, Permission.BILLING_REPORT_READ, scope, "billing.dayclose.list", "day_close", null);
        }
        return jdbc.sql(DAY_CLOSE_SELECT + " where centre_id = :c order by business_date desc, submitted_at desc limit 90")
                .param("c", centreId).query(DayCloseView.class).list();
    }

    @Transactional
    public DayCloseView submit(Actor actor, UUID centreId, BillingModels.SubmitDayClose cmd) {
        Scope scope = Scope.centre(centreId);
        access.require(actor, Permission.BILLING_DAYCLOSE_SUBMIT, scope, "billing.dayclose.submit", "day_close", null);
        if (cmd.businessDate().isAfter(BusinessTime.today())) {
            throw new RuleViolationException("billing.dayclose.future", "A day can be closed only on or after that day");
        }
        BigDecimal[] sums = systemTotals(centreId, cmd.businessDate());
        BigDecimal counted = BillingService.money(cmd.countedCash());
        BigDecimal variance = counted.subtract(sums[0]);
        if (variance.signum() != 0 && (cmd.notes() == null || cmd.notes().isBlank())) {
            throw new RuleViolationException("billing.dayclose.variance-note", "Explain the cash difference of ₹" + variance + " in the notes");
        }
        UUID id;
        try {
            id = jdbc.sql("""
                    insert into billing.day_close (tenant_id, centre_id, business_date, system_cash, system_card, system_upi,
                        system_other, counted_cash, variance, notes, submitted_by)
                    values (:t, :c, :d, :cash, :card, :upi, :other, :counted, :var, :notes, :by) returning id
                    """).param("t", actor.tenantId()).param("c", centreId).param("d", cmd.businessDate()).param("cash", sums[0])
                    .param("card", sums[1]).param("upi", sums[2]).param("other", sums[3]).param("counted", counted)
                    .param("var", variance).param("notes", cmd.notes()).param("by", actor.userId()).query(UUID.class).single();
        } catch (DuplicateKeyException e) {
            throw new ConflictException("billing.dayclose.exists", "This day has already been submitted for closing");
        }
        audit.record(AuditEntry.success(actor, "billing.dayclose.submit", "day_close", id, scope,
                Map.of("date", cmd.businessDate().toString(), "systemCash", sums[0].toPlainString(), "countedCash", counted.toPlainString(),
                        "variance", variance.toPlainString())));
        return row(id);
    }

    @Transactional
    public DayCloseView decide(Actor actor, UUID dayCloseId, BillingModels.Decision cmd) {
        DayCloseView current = row(dayCloseId);
        Scope scope = Scope.centre(current.centreId());
        access.require(actor, Permission.BILLING_DAYCLOSE_APPROVE, scope, "billing.dayclose.decide", "day_close", dayCloseId.toString());
        if (!"SUBMITTED".equals(current.status())) {
            throw new RuleViolationException("billing.dayclose.decided", "This day close has already been decided");
        }
        UUID submittedBy = jdbc.sql("select submitted_by from billing.day_close where id = :id").param("id", dayCloseId)
                .query(UUID.class).single();
        if (actor.userId().equals(submittedBy)) {
            throw new RuleViolationException("billing.dayclose.self-approval", "You cannot approve a day close you submitted");
        }
        int n = jdbc.sql("""
                update billing.day_close set status = :s, decided_by = :by, decided_at = now(), decision_note = :n, version = version + 1
                where id = :id and version = :v
                """).param("s", cmd.approve() ? "APPROVED" : "REJECTED").param("by", actor.userId()).param("n", cmd.note())
                .param("id", dayCloseId).param("v", cmd.expectedVersion()).update();
        if (n == 0) {
            throw new ConflictException("billing.dayclose.changed", "Someone else changed this day close. Reload and try again.");
        }
        audit.record(AuditEntry.success(actor, cmd.approve() ? "billing.dayclose.approve" : "billing.dayclose.reject", "day_close",
                dayCloseId, scope, Map.of("date", current.businessDate().toString(), "variance", current.variance().toPlainString())));
        return row(dayCloseId);
    }

    private BigDecimal[] systemTotals(UUID centreId, LocalDate d) {
        return jdbc.sql("""
                select coalesce(sum(amount) filter (where mode = 'CASH'), 0) as cash,
                       coalesce(sum(amount) filter (where mode = 'CARD'), 0) as card,
                       coalesce(sum(amount) filter (where mode = 'UPI'), 0) as upi,
                       coalesce(sum(amount) filter (where mode not in ('CASH', 'CARD', 'UPI')), 0) as other
                from billing.payment where centre_id = :c and business_date = :d
                """).param("c", centreId).param("d", d)
                .query((rs, i) -> new BigDecimal[] {rs.getBigDecimal("cash"), rs.getBigDecimal("card"), rs.getBigDecimal("upi"),
                        rs.getBigDecimal("other")}).single();
    }

    private DayCloseView row(UUID id) {
        return jdbc.sql(DAY_CLOSE_SELECT + " where id = :id").param("id", id).query(DayCloseView.class).optional()
                .orElseThrow(() -> new NotFoundException("Day close not found"));
    }
}
