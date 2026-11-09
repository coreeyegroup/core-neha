package in.coreeye.coreneha.optical;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import in.coreeye.coreneha.audit.AuditEntry;
import in.coreeye.coreneha.audit.AuditLog;
import in.coreeye.coreneha.billing.BillingDirectory;
import in.coreeye.coreneha.eyecare.GlassesPrescriptions;
import in.coreeye.coreneha.inventory.StockLedger;
import in.coreeye.coreneha.kernel.AccessControl;
import in.coreeye.coreneha.kernel.AccessDeniedException;
import in.coreeye.coreneha.kernel.Actor;
import in.coreeye.coreneha.kernel.ConflictException;
import in.coreeye.coreneha.kernel.NotFoundException;
import in.coreeye.coreneha.kernel.Permission;
import in.coreeye.coreneha.kernel.RuleViolationException;
import in.coreeye.coreneha.kernel.Scope;
import in.coreeye.coreneha.optical.OpticalModels.JobEvent;
import in.coreeye.coreneha.optical.OpticalModels.JobView;
import in.coreeye.coreneha.optical.OpticalModels.OrderView;
import in.coreeye.coreneha.patient.PatientDirectory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
public class OpticalService {

    /** The manufacturing route; COATING may be skipped for uncoated lenses. */
    static final List<String> ROUTE = List.of("RECEIVED", "SURFACING", "COATING", "EDGING", "FITTING", "QC", "READY", "DISPATCHED");

    static String userName(String column) {
        return "(select trim(wp.given_name || ' ' || wp.family_name) from iam.user_account ua "
                + "join workforce.person wp on wp.id = ua.person_id where ua.id = " + column + ")";
    }

    private static final String ORDER_SELECT = """
            select o.id, o.centre_id, o.store_id, o.order_no, o.patient_id, p.uhid as patient_uhid,
                   trim(p.given_name || ' ' || p.family_name) as patient_name, o.visit_ref, o.sph_re, o.cyl_re, o.axis_re, o.add_re,
                   o.sph_le, o.cyl_le, o.axis_le, o.add_le, o.pd_re, o.pd_le, o.fitting_height, o.lens_type, o.lens_index, o.coating,
                   o.tint, o.frame_source, fi.name as frame_name, o.lens_source, li.name as lens_name, o.frame_price, o.lens_price,
                   o.total_amount, o.bill_id, b.bill_no, b.status as bill_status, b.net_amount - b.paid_amount as bill_balance,
                   o.promised_date, o.status, o.notes, %s as ordered_by_name, o.ordered_at, %s as delivered_by_name, o.delivered_at,
                   o.cancel_reason, j.id as lab_job_id, j.job_no as lab_job_no, j.status as lab_job_status, o.version
            from optical.spectacle_order o join patient.patient p on p.id = o.patient_id
            left join inventory.item fi on fi.id = o.frame_item_id left join inventory.item li on li.id = o.lens_item_id
            left join billing.bill b on b.id = o.bill_id
            left join optical.lab_job j on j.order_id = o.id and j.status <> 'CANCELLED'"""
            .formatted(userName("o.ordered_by"), userName("o.delivered_by"));

    record JobRow(UUID id, UUID labCentreId, String labCentreName, UUID labStoreId, String jobNo, UUID orderId, String orderNo,
                  String shopCentreName, String patientName, String specification, String status, String blankItemName, int remakeCount,
                  String fittedByName, String qcByName, String qcResult, String qcMeasured, String qcNote, Instant receivedAt,
                  Instant dispatchedAt, int version) {
    }

    private static final String JOB_SELECT = """
            select j.id, j.lab_centre_id, lc.name as lab_centre_name, j.lab_store_id, j.job_no, j.order_id, o.order_no,
                   sc.name as shop_centre_name, trim(p.given_name || ' ' || p.family_name) as patient_name, j.specification::text as specification,
                   j.status, bi.name as blank_item_name, j.remake_count, %s as fitted_by_name, %s as qc_by_name, j.qc_result,
                   j.qc_measured::text as qc_measured, j.qc_note, j.received_at, j.dispatched_at, j.version
            from optical.lab_job j join optical.spectacle_order o on o.id = j.order_id join patient.patient p on p.id = o.patient_id
            join org.centre lc on lc.id = j.lab_centre_id join org.centre sc on sc.id = o.centre_id
            left join inventory.item bi on bi.id = j.blank_item_id""".formatted(userName("j.fitted_by"), userName("j.qc_by"));

    private final JdbcClient jdbc;
    private final AccessControl access;
    private final AuditLog audit;
    private final StockLedger stock;
    private final BillingDirectory billing;
    private final PatientDirectory patients;
    private final GlassesPrescriptions rx;
    private final JsonMapper json;

    OpticalService(JdbcClient jdbc, AccessControl access, AuditLog audit, StockLedger stock, BillingDirectory billing,
                   PatientDirectory patients, GlassesPrescriptions rx, JsonMapper json) {
        this.jdbc = jdbc;
        this.access = access;
        this.audit = audit;
        this.stock = stock;
        this.billing = billing;
        this.patients = patients;
        this.rx = rx;
        this.json = json;
    }

    // ---------------------------------------------------------------- spectacle orders (shop)

    @Transactional
    public OrderView createOrder(Actor actor, OpticalModels.CreateOrder cmd) {
        var store = stock.store(cmd.storeId()).orElseThrow(() -> new NotFoundException("Store not found"));
        if (!"OPTICAL".equals(store.storeType()) || !"ACTIVE".equals(store.status())) {
            throw new RuleViolationException("optical.not-a-shop", store.name() + " is not an active optical store");
        }
        Scope scope = Scope.centre(store.centreId());
        access.require(actor, Permission.OPTICAL_ORDER, scope, "optical.order.create", "patient", cmd.patientId().toString());
        var patient = patients.patient(cmd.patientId()).filter(p -> "ACTIVE".equals(p.status()))
                .orElseThrow(() -> new NotFoundException("Patient not found"));

        // The prescription comes from the optometry record of the visit when there is one; otherwise as entered (outside prescription).
        var r = cmd.visitRef() == null ? null : rx.forVisit(cmd.visitRef())
                .orElseThrow(() -> new RuleViolationException("optical.no-refraction", "No refraction is recorded at that visit"));
        if (r != null && !r.patientId().equals(patient.id())) {
            throw new RuleViolationException("optical.other-patient", "That visit belongs to another patient");
        }
        BigDecimal sphRe = r != null ? r.sphRe() : cmd.sphRe();
        BigDecimal sphLe = r != null ? r.sphLe() : cmd.sphLe();
        if (sphRe == null && sphLe == null) {
            throw new RuleViolationException("optical.rx-required", "Enter the prescription for at least one eye");
        }
        var lens = stock.item(cmd.lensItemId()).filter(i -> "SPECTACLE_LENS".equals(i.category()) && "ACTIVE".equals(i.status()))
                .orElseThrow(() -> new RuleViolationException("optical.lens", "Choose an active spectacle lens"));
        boolean multifocal = !"SINGLE_VISION".equals(lens.lensType());
        BigDecimal addRe = r != null ? r.addRe() : cmd.addRe();
        BigDecimal addLe = r != null ? r.addLe() : cmd.addLe();
        if (multifocal && addRe == null && addLe == null) {
            throw new RuleViolationException("optical.add-required", "A " + lens.lensType().toLowerCase().replace('_', ' ') + " lens needs the reading addition");
        }
        if ("PROGRESSIVE".equals(lens.lensType()) && cmd.fittingHeight() == null) {
            throw new RuleViolationException("optical.fitting-height", "Measure the fitting height for a progressive lens");
        }
        if ("STOCK".equals(cmd.frameSource()) == (cmd.frameBatchId() == null)) {
            throw new RuleViolationException("optical.frame", "Choose a frame from stock, or mark the frame as the patient's own");
        }
        if ("STOCK".equals(cmd.lensSource()) == (cmd.lensBatchId() == null)) {
            throw new RuleViolationException("optical.lens-batch", "Choose the lens pair from stock, or send the lenses to the lab");
        }

        String orderNo = stock.nextNumber(actor, store.centreId(), "OPT");
        UUID id = UUID.randomUUID();
        List<BillingDirectory.SourcedLine> lines = new ArrayList<>();
        BigDecimal framePrice = BigDecimal.ZERO;
        UUID frameItemId = null;
        UUID frameBatchId = null;
        if ("STOCK".equals(cmd.frameSource())) {
            StockLedger.Taken frame = stock.takeBatch(actor, store.id(), cmd.frameBatchId(), 1, "SALE", "SPECTACLE_ORDER", id, orderNo);
            var frameItem = stock.item(frame.itemId()).orElseThrow();
            if (!"FRAME".equals(frameItem.category())) {
                throw new RuleViolationException("optical.frame", frameItem.name() + " is not a frame");
            }
            framePrice = frame.mrp();
            frameItemId = frameItem.id();
            frameBatchId = frame.batchId();
            lines.add(new BillingDirectory.SourcedLine("Frame: " + frameItem.name(), "OPTICAL", 1, exclusive(framePrice, frameItem.gstRate()),
                    frameItem.gstRate()));
        }
        BigDecimal lensPrice;
        UUID lensBatchId = null;
        if ("STOCK".equals(cmd.lensSource())) {
            StockLedger.Taken pair = stock.takeBatch(actor, store.id(), cmd.lensBatchId(), 1, "SALE", "SPECTACLE_ORDER", id, orderNo);
            if (!pair.itemId().equals(lens.id())) {
                throw new RuleViolationException("optical.lens-batch", "That stock batch is not the chosen lens");
            }
            lensPrice = pair.mrp();
            lensBatchId = pair.batchId();
        } else {
            if (lens.mrp() == null) {
                throw new RuleViolationException("optical.lens-price", lens.name() + " has no price in the item master");
            }
            lensPrice = lens.mrp();
        }
        lines.add(new BillingDirectory.SourcedLine("Lenses: " + lens.name(), "OPTICAL", 1, exclusive(lensPrice, lens.gstRate()), lens.gstRate()));
        jdbc.sql("""
                insert into optical.spectacle_order (id, tenant_id, centre_id, store_id, order_no, patient_id, visit_ref, sph_re, cyl_re, axis_re,
                    add_re, sph_le, cyl_le, axis_le, add_le, pd_re, pd_le, fitting_height, lens_type, lens_index, coating, tint, frame_source,
                    frame_item_id, frame_batch_id, lens_source, lens_item_id, lens_batch_id, frame_price, lens_price, total_amount,
                    promised_date, status, notes, ordered_by)
                values (:id, :t, :c, :s, :no, :p, :v, :sre, :cre, :are, :addre, :sle, :cle, :ale, :addle, :pdr, :pdl, :fh, :lt, :li, :coat, :tint,
                    :fs, :fi, :fb, :ls, :lens, :lb, :fp, :lp, 0, :prom, :status, :notes, :by)""")
                .param("id", id).param("t", actor.tenantId()).param("c", store.centreId()).param("s", store.id()).param("no", orderNo)
                .param("p", patient.id()).param("v", cmd.visitRef()).param("sre", sphRe).param("cre", r != null ? r.cylRe() : cmd.cylRe())
                .param("are", r != null ? r.axisRe() : cmd.axisRe()).param("addre", addRe).param("sle", sphLe)
                .param("cle", r != null ? r.cylLe() : cmd.cylLe()).param("ale", r != null ? r.axisLe() : cmd.axisLe()).param("addle", addLe)
                .param("pdr", cmd.pdRe()).param("pdl", cmd.pdLe()).param("fh", cmd.fittingHeight()).param("lt", lens.lensType())
                .param("li", lens.lensIndex() == null ? new BigDecimal("1.50") : lens.lensIndex()).param("coat", lensCoating(lens.id()))
                .param("tint", cmd.tint()).param("fs", cmd.frameSource()).param("fi", frameItemId).param("fb", frameBatchId)
                .param("ls", cmd.lensSource()).param("lens", lens.id()).param("lb", lensBatchId).param("fp", framePrice).param("lp", lensPrice)
                .param("prom", cmd.promisedDate()).param("status", "LAB".equals(cmd.lensSource()) ? "IN_LAB" : "ORDERED")
                .param("notes", cmd.notes()).param("by", actor.userId()).update();
        var bill = billing.raiseSourcedBill(actor, store.centreId(), patient.id(), cmd.visitRef(), "SPECTACLE_ORDER", id, lines);
        jdbc.sql("update optical.spectacle_order set total_amount = :t, bill_id = :b where id = :id")
                .param("t", bill.netAmount()).param("b", bill.id()).param("id", id).update();
        if ("LAB".equals(cmd.lensSource())) {
            createJob(actor, id);
        }
        audit.record(AuditEntry.success(actor, "optical.order.create", "spectacle-order", id, scope,
                Map.of("orderNo", orderNo, "patient", patient.uhid(), "lens", lens.name(), "lensSource", cmd.lensSource(),
                        "frameSource", cmd.frameSource(), "bill", bill.billNo())));
        return order(id);
    }

    /** Shop: spectacles fitted in the shop from stock lenses are ready for the patient. */
    @Transactional
    public OrderView markReady(Actor actor, UUID orderId, OpticalModels.OrderAction cmd) {
        OrderView o = lockedOrder(orderId, cmd.expectedVersion());
        access.require(actor, Permission.OPTICAL_ORDER, Scope.centre(o.centreId()), "optical.order.ready", "spectacle-order", orderId.toString());
        if (!"ORDERED".equals(o.status())) {
            throw new RuleViolationException("optical.order.state", "Only an order fitted in the shop can be marked ready here");
        }
        setOrderStatus(orderId, "READY");
        audit.record(AuditEntry.success(actor, "optical.order.ready", "spectacle-order", orderId, Scope.centre(o.centreId()),
                Map.of("orderNo", o.orderNo())));
        return order(orderId);
    }

    @Transactional
    public OrderView deliver(Actor actor, UUID orderId, OpticalModels.OrderAction cmd) {
        OrderView o = lockedOrder(orderId, cmd.expectedVersion());
        Scope scope = Scope.centre(o.centreId());
        access.require(actor, Permission.OPTICAL_DELIVER, scope, "optical.order.deliver", "spectacle-order", orderId.toString());
        if (!"READY".equals(o.status())) {
            throw new RuleViolationException("optical.order.not-ready", "Order " + o.orderNo() + " is not ready for delivery");
        }
        var bill = billing.bill(o.billId()).orElseThrow();
        if (bill.balance().signum() > 0) {
            throw new RuleViolationException("optical.order.unpaid", "Collect the balance of " + bill.balance() + " on bill " + bill.billNo()
                    + " before delivering");
        }
        jdbc.sql("update optical.spectacle_order set status = 'DELIVERED', delivered_by = :by, delivered_at = now(), version = version + 1 where id = :id")
                .param("by", actor.userId()).param("id", orderId).update();
        audit.record(AuditEntry.success(actor, "optical.order.deliver", "spectacle-order", orderId, scope, Map.of("orderNo", o.orderNo())));
        return order(orderId);
    }

    @Transactional
    public OrderView cancel(Actor actor, UUID orderId, OpticalModels.OrderAction cmd) {
        OrderView o = lockedOrder(orderId, cmd.expectedVersion());
        Scope scope = Scope.centre(o.centreId());
        access.require(actor, Permission.OPTICAL_ORDER, scope, "optical.order.cancel", "spectacle-order", orderId.toString());
        if (cmd.reason() == null || cmd.reason().isBlank()) {
            throw new RuleViolationException("optical.reason-required", "Give a reason for cancelling");
        }
        if ("DELIVERED".equals(o.status()) || "CANCELLED".equals(o.status())) {
            throw new RuleViolationException("optical.order.closed", "Order " + o.orderNo() + " is " + o.status().toLowerCase());
        }
        billing.cancelSourcedBill(actor, o.billId(), "Spectacle order " + o.orderNo() + " cancelled: " + cmd.reason());
        Batches b = jdbc.sql("select frame_batch_id, lens_batch_id from optical.spectacle_order where id = :id").param("id", orderId)
                .query(Batches.class).single();
        if (b.frameBatchId() != null) {
            stock.putBack(actor, b.frameBatchId(), 1, "SALE_RETURN", "SPECTACLE_ORDER", orderId, o.orderNo(), "Order cancelled");
        }
        if (b.lensBatchId() != null) {
            stock.putBack(actor, b.lensBatchId(), 1, "SALE_RETURN", "SPECTACLE_ORDER", orderId, o.orderNo(), "Order cancelled");
        }
        jdbc.sql("update optical.lab_job set status = 'CANCELLED', version = version + 1 where order_id = :o and status not in ('DISPATCHED', 'CANCELLED')")
                .param("o", orderId).update();
        jdbc.sql("update optical.spectacle_order set status = 'CANCELLED', cancel_reason = :r, version = version + 1 where id = :id")
                .param("r", cmd.reason()).param("id", orderId).update();
        audit.record(AuditEntry.success(actor, "optical.order.cancel", "spectacle-order", orderId, scope,
                Map.of("orderNo", o.orderNo(), "reason", cmd.reason())));
        return order(orderId);
    }

    public List<OrderView> orders(Actor actor, UUID centreId, String status) {
        requireAnyAt(actor, Scope.centre(centreId), Permission.OPTICAL_READ, Permission.OPTICAL_ORDER, Permission.OPTICAL_DELIVER);
        Map<String, Object> p = new HashMap<>();
        p.put("c", centreId);
        p.put("s", status);
        return jdbc.sql(ORDER_SELECT + " " + """
                 where o.centre_id = :c and (cast(:s as text) is null or o.status = :s)
                   and (o.status not in ('DELIVERED', 'CANCELLED') or o.ordered_at > now() - interval '30 days')
                 order by o.ordered_at desc limit 300""").params(p).query(OrderView.class).list();
    }

    public List<OrderView> ordersForPatient(Actor actor, UUID patientId) {
        return jdbc.sql(ORDER_SELECT + " where o.patient_id = :p order by o.ordered_at desc limit 50").param("p", patientId)
                .query(OrderView.class).list().stream()
                .filter(o -> access.holds(actor, Permission.OPTICAL_READ, Scope.centre(o.centreId()))
                        || access.holds(actor, Permission.OPTICAL_ORDER, Scope.centre(o.centreId())))
                .toList();
    }

    public OrderView getOrder(Actor actor, UUID orderId) {
        OrderView o = order(orderId);
        requireAnyAt(actor, Scope.centre(o.centreId()), Permission.OPTICAL_READ, Permission.OPTICAL_ORDER, Permission.OPTICAL_DELIVER);
        return o;
    }

    private OrderView order(UUID id) {
        return jdbc.sql(ORDER_SELECT + " where o.id = :id").param("id", id).query(OrderView.class).optional()
                .orElseThrow(() -> new NotFoundException("Spectacle order not found"));
    }

    private OrderView lockedOrder(UUID id, Integer expectedVersion) {
        jdbc.sql("select id from optical.spectacle_order where id = :id for update").param("id", id).query(UUID.class).optional()
                .orElseThrow(() -> new NotFoundException("Spectacle order not found"));
        OrderView o = order(id);
        if (expectedVersion != null && expectedVersion != o.version()) {
            throw new ConflictException("optical.order.changed", "Someone else changed this order. Reload and try again.");
        }
        return o;
    }

    private record Batches(UUID frameBatchId, UUID lensBatchId) {
    }

    private void setOrderStatus(UUID id, String status) {
        jdbc.sql("update optical.spectacle_order set status = :s, version = version + 1 where id = :id").param("s", status).param("id", id).update();
    }

    // ---------------------------------------------------------------- manufacturing jobs (lab)

    private void createJob(Actor actor, UUID orderId) {
        OrderView o = order(orderId);
        var labs = stock.stores("OPTICAL_LAB");
        var lab = labs.stream().filter(s -> s.centreId().equals(o.centreId())).findFirst().or(() -> labs.stream().findFirst())
                .orElseThrow(() -> new RuleViolationException("optical.no-lab", "No optical lab store is set up in this organisation"));
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("orderNo", o.orderNo());
        spec.put("patient", o.patientName() + " (" + o.patientUhid() + ")");
        spec.put("right", eye(o.sphRe(), o.cylRe(), o.axisRe(), o.addRe()));
        spec.put("left", eye(o.sphLe(), o.cylLe(), o.axisLe(), o.addLe()));
        spec.put("pdRight", o.pdRe());
        spec.put("pdLeft", o.pdLe());
        spec.put("fittingHeight", o.fittingHeight());
        spec.put("lens", o.lensName());
        spec.put("lensType", o.lensType());
        spec.put("lensIndex", o.lensIndex());
        spec.put("coating", o.coating());
        spec.put("tint", o.tint());
        spec.put("frame", "PATIENT_OWN".equals(o.frameSource()) ? "Patient's own frame (sent with the job)" : o.frameName());
        spec.put("promisedDate", o.promisedDate() == null ? null : o.promisedDate().toString());
        String jobNo = stock.nextNumber(actor, lab.centreId(), "LAB");
        UUID jobId = jdbc.sql("""
                insert into optical.lab_job (tenant_id, lab_centre_id, lab_store_id, job_no, order_id, specification)
                values (:t, :c, :s, :no, :o, cast(:spec as jsonb)) returning id""")
                .param("t", actor.tenantId()).param("c", lab.centreId()).param("s", lab.id()).param("no", jobNo).param("o", orderId)
                .param("spec", json.writeValueAsString(spec)).query(UUID.class).single();
        event(actor, jobId, null, "RECEIVED", "From order " + o.orderNo());
    }

    @Transactional
    public JobView advance(Actor actor, UUID jobId, OpticalModels.AdvanceJob cmd) {
        JobView j = lockedJob(jobId, cmd.expectedVersion());
        Scope scope = Scope.centre(j.labCentreId());
        boolean dispatch = "DISPATCHED".equals(cmd.toStatus());
        access.require(actor, dispatch ? Permission.OPTICALLAB_QC : Permission.OPTICALLAB_PROCESS, scope, "opticallab.job.advance", "lab-job",
                jobId.toString());
        int from = ROUTE.indexOf(j.status());
        int to = ROUTE.indexOf(cmd.toStatus());
        boolean skipCoating = "EDGING".equals(cmd.toStatus()) && "SURFACING".equals(j.status());
        if (from < 0 || (to != from + 1 && !skipCoating) || "QC".equals(j.status()) || ("DISPATCHED".equals(cmd.toStatus()) && !"READY".equals(j.status()))) {
            throw new RuleViolationException("opticallab.route", "A job at " + j.status().toLowerCase() + " cannot move to " + cmd.toStatus().toLowerCase());
        }
        if ("SURFACING".equals(cmd.toStatus())) {
            if (cmd.blankItemId() == null) {
                throw new RuleViolationException("opticallab.blank", "Choose the lens blank used for surfacing");
            }
            var blank = stock.item(cmd.blankItemId()).filter(i -> "LENS_BLANK".equals(i.category()))
                    .orElseThrow(() -> new RuleViolationException("opticallab.blank", "Choose a lens blank"));
            // A pair of blanks (right and left) for every surfacing, including remakes.
            stock.takeFirstExpiry(actor, j.labStoreId(), blank.id(), 2, "CONSUMPTION", "LAB_JOB", jobId, j.jobNo());
            jdbc.sql("update optical.lab_job set blank_item_id = :b where id = :id").param("b", blank.id()).param("id", jobId).update();
        }
        if ("QC".equals(cmd.toStatus())) {
            jdbc.sql("update optical.lab_job set fitted_by = :u where id = :id").param("u", actor.userId()).param("id", jobId).update();
        }
        jdbc.sql("update optical.lab_job set status = :s, version = version + 1, dispatched_at = case when :s = 'DISPATCHED' then now() end where id = :id")
                .param("s", cmd.toStatus()).param("id", jobId).update();
        event(actor, jobId, j.status(), cmd.toStatus(), cmd.note());
        if (dispatch) {
            setOrderStatus(j.orderId(), "READY");
        }
        audit.record(AuditEntry.success(actor, "opticallab.job." + cmd.toStatus().toLowerCase(), "lab-job", jobId, scope,
                Map.of("jobNo", j.jobNo(), "from", j.status(), "to", cmd.toStatus())));
        return job(jobId);
    }

    /** Quality check by someone other than the fitter; measured powers must be within tolerance to pass. */
    @Transactional
    public JobView qualityCheck(Actor actor, UUID jobId, OpticalModels.QualityCheck cmd) {
        JobView j = lockedJob(jobId, cmd.expectedVersion());
        Scope scope = Scope.centre(j.labCentreId());
        access.require(actor, Permission.OPTICALLAB_QC, scope, "opticallab.job.qc", "lab-job", jobId.toString());
        if (!"QC".equals(j.status())) {
            throw new RuleViolationException("opticallab.not-at-qc", "Job " + j.jobNo() + " is not waiting for quality check");
        }
        UUID fitter = jdbc.sql("select fitted_by from optical.lab_job where id = :id").param("id", jobId).query(UUID.class).single();
        if (actor.userId().equals(fitter)) {
            throw new AccessDeniedException("opticallab.qc.self", "You fitted these lenses; another person must check them");
        }
        Map<String, Object> measured = new LinkedHashMap<>();
        measured.put("right", eye(cmd.measuredSphRe(), cmd.measuredCylRe(), cmd.measuredAxisRe(), null));
        measured.put("left", eye(cmd.measuredSphLe(), cmd.measuredCylLe(), cmd.measuredAxisLe(), null));
        List<String> outside = new ArrayList<>();
        OrderView o = order(j.orderId());
        check(outside, "right sphere", o.sphRe(), cmd.measuredSphRe());
        check(outside, "right cylinder", o.cylRe(), cmd.measuredCylRe());
        checkAxis(outside, "right axis", o.cylRe(), o.axisRe(), cmd.measuredAxisRe());
        check(outside, "left sphere", o.sphLe(), cmd.measuredSphLe());
        check(outside, "left cylinder", o.cylLe(), cmd.measuredCylLe());
        checkAxis(outside, "left axis", o.cylLe(), o.axisLe(), cmd.measuredAxisLe());
        if (cmd.pass() && !outside.isEmpty()) {
            throw new RuleViolationException("opticallab.qc.tolerance", "Outside tolerance: " + String.join(", ", outside) + ". Record a fail.");
        }
        String next = cmd.pass() ? "READY" : "RECEIVED";
        jdbc.sql("""
                update optical.lab_job set qc_by = case when :pass then cast(:u as uuid) end, qc_result = :r, qc_measured = cast(:m as jsonb), qc_note = :n, status = :s,
                    remake_count = remake_count + case when :pass then 0 else 1 end, fitted_by = case when :pass then fitted_by end,
                    version = version + 1
                where id = :id""")
                .param("u", actor.userId()).param("r", cmd.pass() ? "PASS" : "FAIL").param("m", json.writeValueAsString(measured))
                .param("n", cmd.note()).param("s", next).param("pass", cmd.pass()).param("id", jobId).update();
        event(actor, jobId, "QC", next, (cmd.pass() ? "Passed: " : "Failed, remake: ") + cmd.note());
        audit.record(AuditEntry.success(actor, "opticallab.job.qc", "lab-job", jobId, scope,
                Map.of("jobNo", j.jobNo(), "result", cmd.pass() ? "PASS" : "FAIL", "note", cmd.note())));
        return job(jobId);
    }

    public List<JobView> jobs(Actor actor, UUID labCentreId, String status) {
        requireAnyAt(actor, Scope.centre(labCentreId), Permission.OPTICALLAB_READ, Permission.OPTICALLAB_PROCESS, Permission.OPTICALLAB_QC);
        Map<String, Object> p = new HashMap<>();
        p.put("c", labCentreId);
        p.put("s", status);
        return jdbc.sql(JOB_SELECT + " " + """
                 where j.lab_centre_id = :c and (cast(:s as text) is null or j.status = :s)
                   and (j.status not in ('DISPATCHED', 'CANCELLED') or j.received_at > now() - interval '14 days')
                 order by j.received_at limit 300""").params(p).query(JobRow.class).list().stream().map(r -> toView(r, false)).toList();
    }

    public JobView getJob(Actor actor, UUID jobId) {
        JobView j = job(jobId);
        requireAnyAt(actor, Scope.centre(j.labCentreId()), Permission.OPTICALLAB_READ, Permission.OPTICALLAB_PROCESS, Permission.OPTICALLAB_QC);
        return j;
    }

    private JobView job(UUID id) {
        return jdbc.sql(JOB_SELECT + " where j.id = :id").param("id", id).query(JobRow.class).optional().map(r -> toView(r, true))
                .orElseThrow(() -> new NotFoundException("Lab job not found"));
    }

    private JobView lockedJob(UUID id, Integer expectedVersion) {
        jdbc.sql("select id from optical.lab_job where id = :id for update").param("id", id).query(UUID.class).optional()
                .orElseThrow(() -> new NotFoundException("Lab job not found"));
        JobView j = job(id);
        if ("CANCELLED".equals(j.status())) {
            throw new RuleViolationException("opticallab.cancelled", "The order was cancelled");
        }
        if (expectedVersion != null && expectedVersion != j.version()) {
            throw new ConflictException("opticallab.changed", "Someone else moved this job. Reload and try again.");
        }
        return j;
    }

    private JobView toView(JobRow r, boolean withEvents) {
        List<JobEvent> events = withEvents ? jdbc.sql("""
                select from_status, to_status, note, %s as actor_name, at from optical.lab_job_event where job_id = :j order by at"""
                .formatted(userName("actor_id"))).param("j", r.id()).query(JobEvent.class).list() : List.of();
        return new JobView(r.id(), r.labCentreId(), r.labCentreName(), r.labStoreId(), r.jobNo(), r.orderId(), r.orderNo(), r.shopCentreName(),
                r.patientName(), parse(r.specification()), r.status(), r.blankItemName(), r.remakeCount(), r.fittedByName(), r.qcByName(),
                r.qcResult(), parse(r.qcMeasured()), r.qcNote(), r.receivedAt(), r.dispatchedAt(), r.version(), events);
    }

    private void event(Actor actor, UUID jobId, String from, String to, String note) {
        jdbc.sql("""
                insert into optical.lab_job_event (tenant_id, job_id, from_status, to_status, note, actor_id) values (:t, :j, :f, :to, :n, :a)""")
                .param("t", actor.tenantId()).param("j", jobId).param("f", from).param("to", to).param("n", note).param("a", actor.userId())
                .update();
    }

    // ---------------------------------------------------------------- helpers

    private String lensCoating(UUID itemId) {
        return jdbc.sql("select coating from inventory.item where id = :i").param("i", itemId).query(String.class).optional().orElse(null);
    }

    private Map<String, Object> parse(String s) {
        return s == null ? null : json.readValue(s, new TypeReference<Map<String, Object>>() {
        });
    }

    private static Map<String, Object> eye(BigDecimal sph, BigDecimal cyl, Integer axis, BigDecimal add) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sph", sph);
        m.put("cyl", cyl);
        m.put("axis", axis);
        m.put("add", add);
        return m;
    }

    /** Power tolerance (simplified from ISO 8980-1): 0.13 D up to 6 D, 0.25 D above. */
    private static void check(List<String> outside, String what, BigDecimal ordered, BigDecimal measured) {
        if (ordered == null || measured == null) {
            return;
        }
        BigDecimal tolerance = ordered.abs().compareTo(new BigDecimal("6")) <= 0 ? new BigDecimal("0.13") : new BigDecimal("0.25");
        if (ordered.subtract(measured).abs().compareTo(tolerance) > 0) {
            outside.add(what + " " + measured + " vs " + ordered);
        }
    }

    /** Axis tolerance: 3 degrees for cylinders of 0.50 D or more, 7 degrees below. */
    private static void checkAxis(List<String> outside, String what, BigDecimal cyl, Integer ordered, Integer measured) {
        if (cyl == null || cyl.signum() == 0 || ordered == null || measured == null) {
            return;
        }
        int diff = Math.abs(ordered - measured);
        diff = Math.min(diff, 180 - diff);
        int tolerance = cyl.abs().compareTo(new BigDecimal("0.50")) >= 0 ? 3 : 7;
        if (diff > tolerance) {
            outside.add(what + " " + measured + " vs " + ordered);
        }
    }

    private void requireAnyAt(Actor actor, Scope scope, Permission... permissions) {
        for (Permission p : permissions) {
            if (access.holds(actor, p, scope)) {
                return;
            }
        }
        access.require(actor, permissions[0], scope, "optical.read", "centre", scope.id().toString());
    }

    private static BigDecimal exclusive(BigDecimal inclusive, BigDecimal gst) {
        return inclusive.multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(100).add(gst), 2, java.math.RoundingMode.HALF_UP);
    }
}
