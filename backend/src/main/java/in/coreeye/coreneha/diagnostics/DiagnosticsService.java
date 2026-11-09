package in.coreeye.coreneha.diagnostics;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import in.coreeye.coreneha.audit.AuditEntry;
import in.coreeye.coreneha.audit.AuditLog;
import in.coreeye.coreneha.billing.BillingDirectory;
import in.coreeye.coreneha.diagnostics.DiagnosticsModels.Catalogue;
import in.coreeye.coreneha.diagnostics.DiagnosticsModels.LabTestView;
import in.coreeye.coreneha.diagnostics.DiagnosticsModels.OrderView;
import in.coreeye.coreneha.diagnostics.DiagnosticsModels.ParameterView;
import in.coreeye.coreneha.diagnostics.DiagnosticsModels.ProcedureView;
import in.coreeye.coreneha.diagnostics.DiagnosticsModels.ResultView;
import in.coreeye.coreneha.kernel.AccessControl;
import in.coreeye.coreneha.kernel.AccessDeniedException;
import in.coreeye.coreneha.kernel.Actor;
import in.coreeye.coreneha.kernel.BusinessTime;
import in.coreeye.coreneha.kernel.ConflictException;
import in.coreeye.coreneha.kernel.CoreNehaProperties;
import in.coreeye.coreneha.kernel.NotFoundException;
import in.coreeye.coreneha.kernel.Permission;
import in.coreeye.coreneha.kernel.RuleViolationException;
import in.coreeye.coreneha.kernel.Scope;
import in.coreeye.coreneha.org.OrgDirectory;
import in.coreeye.coreneha.patient.PatientDirectory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiagnosticsService {

    static String userName(String column) {
        return "(select trim(wp.given_name || ' ' || wp.family_name) from iam.user_account ua "
                + "join workforce.person wp on wp.id = ua.person_id where ua.id = " + column + ")";
    }

    record OrderRow(UUID id, UUID centreId, String requestNo, String accessionNo, UUID patientId, String patientUhid, String patientName,
                    String patientSex, Integer patientAge, UUID visitRef, String kind, UUID labTestId, String testCode, String testName,
                    String section, String sampleType, String modality, String bodyPart, String priority, String clinicalNotes, String status,
                    UUID billId, String billNo, String billStatus, UUID resultBy, String orderedByName, Instant orderedAt, String collectedByName,
                    Instant collectedAt, Instant receivedAt, String rejectionNote, String resultByName, Instant resultAt, String validatedByName,
                    Instant validatedAt, boolean hasCritical, Instant scheduledAt, String performedByName, Instant performedAt,
                    String studyInstanceUid, Integer imageCount, String findings, String impression, String reportedByName, Instant reportedAt,
                    String verifiedByName, Instant verifiedAt, String cancelReason, int version) {
    }

    private static final String ORDER_SELECT = """
            select o.id, o.centre_id, o.request_no, o.accession_no, o.patient_id, p.uhid as patient_uhid,
                   trim(p.given_name || ' ' || p.family_name) as patient_name, p.sex as patient_sex,
                   case when p.birth_date is null then null else extract(year from age(p.birth_date))::int end as patient_age,
                   o.visit_ref, o.kind, o.lab_test_id, coalesce(t.code, ip.code) as test_code, coalesce(t.name, ip.name) as test_name,
                   t.section, t.sample_type, ip.modality, ip.body_part, o.priority, o.clinical_notes, o.status, o.bill_id, b.bill_no,
                   b.status as bill_status, o.result_by, %s as ordered_by_name, o.ordered_at, %s as collected_by_name, o.collected_at,
                   o.received_at, o.rejection_note, %s as result_by_name, o.result_at, %s as validated_by_name, o.validated_at,
                   o.has_critical, o.scheduled_at, %s as performed_by_name, o.performed_at, o.study_instance_uid, o.image_count,
                   o.findings, o.impression, %s as reported_by_name, o.reported_at, %s as verified_by_name, o.verified_at,
                   o.cancel_reason, o.version
            from diagnostics.diagnostic_order o join patient.patient p on p.id = o.patient_id
            left join diagnostics.lab_test t on t.id = o.lab_test_id
            left join diagnostics.imaging_procedure ip on ip.id = o.procedure_id
            left join billing.bill b on b.id = o.bill_id
            """.formatted(userName("o.ordered_by"), userName("o.collected_by"), userName("o.result_by"), userName("o.validated_by"),
            userName("o.performed_by"), userName("o.reported_by"), userName("o.verified_by"));

    private final JdbcClient jdbc;
    private final AccessControl access;
    private final AuditLog audit;
    private final BillingDirectory billing;
    private final PatientDirectory patients;
    private final OrgDirectory org;
    private final ImagingArchive archive;
    private final CoreNehaProperties props;

    DiagnosticsService(JdbcClient jdbc, AccessControl access, AuditLog audit, BillingDirectory billing, PatientDirectory patients,
                       OrgDirectory org, ImagingArchive archive, CoreNehaProperties props) {
        this.jdbc = jdbc;
        this.access = access;
        this.audit = audit;
        this.billing = billing;
        this.patients = patients;
        this.org = org;
        this.archive = archive;
        this.props = props;
    }

    // ---------------------------------------------------------------- catalogue

    public Catalogue catalogue(Actor actor) {
        boolean any = false;
        for (Permission p : List.of(Permission.LAB_ORDER, Permission.LAB_READ, Permission.LAB_PROCESS, Permission.RADIOLOGY_ORDER,
                Permission.RADIOLOGY_READ, Permission.RADIOLOGY_PERFORM, Permission.LAB_CATALOGUE_MANAGE, Permission.RADIOLOGY_CATALOGUE_MANAGE)) {
            any = any || access.holds(actor, p, null) || !access.centresWhere(actor, p).isEmpty();
        }
        if (!any) {
            access.requireAnywhere(actor, Permission.LAB_READ, "diagnostics.catalogue.read", "catalogue");
        }
        List<LabTestView> tests = jdbc.sql("""
                select id, code, name, section, sample_type, container, loinc_code, price, tat_hours, status
                from diagnostics.lab_test where status = 'ACTIVE' order by section, name""")
                .query((rs, i) -> new LabTestView(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"),
                        rs.getString("section"), rs.getString("sample_type"), rs.getString("container"), rs.getString("loinc_code"),
                        rs.getBigDecimal("price"), rs.getInt("tat_hours"), rs.getString("status"), List.of())).list();
        List<LabTestView> withParams = tests.stream().map(t -> new LabTestView(t.id(), t.code(), t.name(), t.section(), t.sampleType(),
                t.container(), t.loincCode(), t.price(), t.tatHours(), t.status(), parameters(t.id()))).toList();
        List<ProcedureView> procedures = jdbc.sql("""
                select id, code, name, modality, body_part, preparation, price, status from diagnostics.imaging_procedure
                where status = 'ACTIVE' order by modality, name""").query(ProcedureView.class).list();
        return new Catalogue(withParams, procedures);
    }

    private List<ParameterView> parameters(UUID testId) {
        return jdbc.sql("""
                select id, code, name, unit, loinc_code, value_type, ref_low, ref_high, critical_low, critical_high, ref_text, sort_order
                from diagnostics.lab_parameter where test_id = :t order by sort_order, name""").param("t", testId).query(ParameterView.class).list();
    }

    /** Adds a laboratory test with its parameters to the chain's catalogue (seed and catalogue screen). */
    @Transactional
    public UUID addLabTest(Actor actor, String code, String name, String section, String sampleType, String container, String loinc,
                           BigDecimal price, int tatHours, List<ParameterView> parameters) {
        access.require(actor, Permission.LAB_CATALOGUE_MANAGE, Scope.tenant(actor.tenantId()), "lab.catalogue.add", "lab-test", null);
        UUID id = jdbc.sql("""
                insert into diagnostics.lab_test (tenant_id, code, name, section, sample_type, container, loinc_code, price, tat_hours)
                values (:t, :code, :name, :sec, :st, :c, :l, :p, :tat) returning id""")
                .param("t", actor.tenantId()).param("code", code).param("name", name).param("sec", section).param("st", sampleType)
                .param("c", container).param("l", loinc).param("p", price).param("tat", tatHours).query(UUID.class).single();
        int sort = 0;
        for (ParameterView p : parameters) {
            jdbc.sql("""
                    insert into diagnostics.lab_parameter (tenant_id, test_id, code, name, unit, loinc_code, value_type, ref_low, ref_high,
                        critical_low, critical_high, ref_text, sort_order)
                    values (:t, :test, :code, :name, :unit, :loinc, :vt, :rl, :rh, :cl, :ch, :rt, :s)""")
                    .param("t", actor.tenantId()).param("test", id).param("code", p.code()).param("name", p.name()).param("unit", p.unit())
                    .param("loinc", p.loincCode()).param("vt", p.valueType()).param("rl", p.refLow()).param("rh", p.refHigh())
                    .param("cl", p.criticalLow()).param("ch", p.criticalHigh()).param("rt", p.refText()).param("s", sort++).update();
        }
        audit.record(AuditEntry.success(actor, "lab.catalogue.add", "lab-test", id, Scope.tenant(actor.tenantId()),
                Map.of("code", code, "name", name, "parameters", parameters.size())));
        return id;
    }

    @Transactional
    public UUID addProcedure(Actor actor, String code, String name, String modality, String bodyPart, String preparation, BigDecimal price) {
        access.require(actor, Permission.RADIOLOGY_CATALOGUE_MANAGE, Scope.tenant(actor.tenantId()), "radiology.catalogue.add", "procedure", null);
        UUID id = jdbc.sql("""
                insert into diagnostics.imaging_procedure (tenant_id, code, name, modality, body_part, preparation, price)
                values (:t, :code, :name, :m, :b, :prep, :p) returning id""")
                .param("t", actor.tenantId()).param("code", code).param("name", name).param("m", modality).param("b", bodyPart)
                .param("prep", preparation).param("p", price).query(UUID.class).single();
        audit.record(AuditEntry.success(actor, "radiology.catalogue.add", "procedure", id, Scope.tenant(actor.tenantId()),
                Map.of("code", code, "name", name, "modality", modality)));
        return id;
    }

    // ---------------------------------------------------------------- ordering

    /** Orders tests and imaging for a patient in one request: one accession number each, one bill for the request. */
    @Transactional
    public List<OrderView> order(Actor actor, UUID centreId, DiagnosticsModels.CreateOrders cmd) {
        Scope scope = Scope.centre(centreId);
        List<UUID> tests = cmd.labTestIds() == null ? List.of() : cmd.labTestIds();
        List<UUID> procedures = cmd.procedureIds() == null ? List.of() : cmd.procedureIds();
        if (tests.isEmpty() && procedures.isEmpty()) {
            throw new RuleViolationException("diagnostics.nothing-ordered", "Choose at least one test or imaging procedure");
        }
        if (!tests.isEmpty()) {
            access.require(actor, Permission.LAB_ORDER, scope, "lab.order", "patient", cmd.patientId().toString());
        }
        if (!procedures.isEmpty()) {
            access.require(actor, Permission.RADIOLOGY_ORDER, scope, "radiology.order", "patient", cmd.patientId().toString());
        }
        var patient = patients.patient(cmd.patientId()).filter(p -> "ACTIVE".equals(p.status()))
                .orElseThrow(() -> new NotFoundException("Patient not found"));
        String centreCode = org.centre(centreId).orElseThrow(() -> new NotFoundException("Centre not found")).code();
        String requestNo = number(actor, centreId, centreCode, "DX");
        List<UUID> ids = new ArrayList<>();
        List<BillingDirectory.SourcedLine> lines = new ArrayList<>();
        String priority = cmd.priority() == null ? "ROUTINE" : cmd.priority();
        for (UUID t : tests) {
            record Test(String name, BigDecimal price) {
            }
            Test test = jdbc.sql("select name, price from diagnostics.lab_test where id = :id and status = 'ACTIVE'").param("id", t)
                    .query((rs, i) -> new Test(rs.getString("name"), rs.getBigDecimal("price"))).optional()
                    .orElseThrow(() -> new NotFoundException("Laboratory test not found"));
            ids.add(insertOrder(actor, centreId, requestNo, number(actor, centreId, centreCode, "LB"), patient.id(), cmd, "LAB", t, null, priority));
            lines.add(new BillingDirectory.SourcedLine("Lab: " + test.name(), "INVESTIGATION", 1, test.price(), BigDecimal.ZERO));
        }
        for (UUID pr : procedures) {
            record Proc(String name, BigDecimal price) {
            }
            Proc proc = jdbc.sql("select name, price from diagnostics.imaging_procedure where id = :id and status = 'ACTIVE'").param("id", pr)
                    .query((rs, i) -> new Proc(rs.getString("name"), rs.getBigDecimal("price"))).optional()
                    .orElseThrow(() -> new NotFoundException("Imaging procedure not found"));
            ids.add(insertOrder(actor, centreId, requestNo, number(actor, centreId, centreCode, "RD"), patient.id(), cmd, "IMAGING", null, pr, priority));
            lines.add(new BillingDirectory.SourcedLine("Imaging: " + proc.name(), "INVESTIGATION", 1, proc.price(), BigDecimal.ZERO));
        }
        // Diagnostic services carry no GST here (healthcare services); the chain can price them per test.
        var bill = billing.raiseSourcedBill(actor, centreId, patient.id(), cmd.visitRef(), "DIAGNOSTIC_ORDER", ids.getFirst(), lines);
        for (UUID id : ids) {
            jdbc.sql("update diagnostics.diagnostic_order set bill_id = :b where id = :id").param("b", bill.id()).param("id", id).update();
        }
        audit.record(AuditEntry.success(actor, "diagnostics.order", "diagnostic-request", ids.getFirst(), scope,
                Map.of("requestNo", requestNo, "patient", patient.uhid(), "labTests", tests.size(), "imaging", procedures.size(),
                        "bill", bill.billNo())));
        return ids.stream().map(id -> view(actor, row(id))).toList();
    }

    private UUID insertOrder(Actor actor, UUID centreId, String requestNo, String accession, UUID patientId, DiagnosticsModels.CreateOrders cmd,
                             String kind, UUID test, UUID procedure, String priority) {
        return jdbc.sql("""
                insert into diagnostics.diagnostic_order (tenant_id, centre_id, request_no, accession_no, patient_id, visit_ref, kind, lab_test_id,
                    procedure_id, priority, clinical_notes, ordered_by)
                values (:t, :c, :r, :a, :p, :v, :k, :lt, :pr, :prio, :notes, :by) returning id""")
                .param("t", actor.tenantId()).param("c", centreId).param("r", requestNo).param("a", accession).param("p", patientId)
                .param("v", cmd.visitRef()).param("k", kind).param("lt", test).param("pr", procedure).param("prio", priority)
                .param("notes", cmd.clinicalNotes()).param("by", actor.userId()).query(UUID.class).single();
    }

    @Transactional
    public OrderView cancel(Actor actor, UUID orderId, DiagnosticsModels.Step cmd) {
        OrderRow o = locked(orderId, cmd.expectedVersion());
        Permission p = "LAB".equals(o.kind()) ? Permission.LAB_ORDER : Permission.RADIOLOGY_ORDER;
        access.require(actor, p, Scope.centre(o.centreId()), "diagnostics.cancel", "diagnostic-order", orderId.toString());
        if (!List.of("ORDERED", "SCHEDULED").contains(o.status())) {
            throw new RuleViolationException("diagnostics.started", "Work has started on " + o.accessionNo() + "; it can no longer be cancelled");
        }
        if (cmd.note() == null || cmd.note().isBlank()) {
            throw new RuleViolationException("diagnostics.reason-required", "Give a reason for cancelling");
        }
        set(orderId, "status = 'CANCELLED', cancel_reason = :n", Map.of("n", cmd.note()));
        audit(actor, o, "diagnostics.cancel", Map.of("reason", cmd.note()));
        return view(actor, row(orderId));
    }

    // ---------------------------------------------------------------- laboratory

    @Transactional
    public OrderView collect(Actor actor, UUID orderId, DiagnosticsModels.Step cmd) {
        OrderRow o = lab(locked(orderId, cmd.expectedVersion()));
        access.require(actor, Permission.LAB_COLLECT, Scope.centre(o.centreId()), "lab.collect", "diagnostic-order", orderId.toString());
        requireStatus(o, "ORDERED");
        set(orderId, "status = 'COLLECTED', collected_by = :u, collected_at = now(), rejection_note = null", Map.of("u", actor.userId()));
        audit(actor, o, "lab.collect", Map.of());
        return view(actor, row(orderId));
    }

    @Transactional
    public OrderView receive(Actor actor, UUID orderId, DiagnosticsModels.Step cmd) {
        OrderRow o = lab(locked(orderId, cmd.expectedVersion()));
        access.require(actor, Permission.LAB_PROCESS, Scope.centre(o.centreId()), "lab.receive", "diagnostic-order", orderId.toString());
        requireStatus(o, "COLLECTED");
        set(orderId, "status = 'IN_LAB', received_by = :u, received_at = now()", Map.of("u", actor.userId()));
        audit(actor, o, "lab.receive", Map.of());
        return view(actor, row(orderId));
    }

    /** Rejects an unsuitable sample (haemolysed, clotted, mislabelled): back to collection, with the reason. */
    @Transactional
    public OrderView reject(Actor actor, UUID orderId, DiagnosticsModels.Step cmd) {
        OrderRow o = lab(locked(orderId, cmd.expectedVersion()));
        access.require(actor, Permission.LAB_PROCESS, Scope.centre(o.centreId()), "lab.reject", "diagnostic-order", orderId.toString());
        requireStatus(o, "COLLECTED", "IN_LAB");
        if (cmd.note() == null || cmd.note().isBlank()) {
            throw new RuleViolationException("diagnostics.reason-required", "Say why the sample is rejected");
        }
        set(orderId, "status = 'ORDERED', rejection_note = :n, collected_by = null, collected_at = null, received_by = null, received_at = null",
                Map.of("n", cmd.note()));
        audit(actor, o, "lab.reject", Map.of("reason", cmd.note()));
        return view(actor, row(orderId));
    }

    @Transactional
    public OrderView enterResults(Actor actor, UUID orderId, DiagnosticsModels.EnterResults cmd) {
        OrderRow o = lab(locked(orderId, cmd.expectedVersion()));
        access.require(actor, Permission.LAB_PROCESS, Scope.centre(o.centreId()), "lab.result", "diagnostic-order", orderId.toString());
        requireStatus(o, "IN_LAB", "RESULTED");
        Map<UUID, ParameterView> params = new HashMap<>();
        parameters(o.labTestId()).forEach(p -> params.put(p.id(), p));
        boolean critical = false;
        for (DiagnosticsModels.ResultInput r : cmd.results()) {
            ParameterView p = params.get(r.parameterId());
            if (p == null) {
                throw new RuleViolationException("lab.parameter", "That result does not belong to " + o.testName());
            }
            if ("NUMERIC".equals(p.valueType()) && r.valueNumeric() == null) {
                throw new RuleViolationException("lab.value-required", "Enter a number for " + p.name());
            }
            String flag = flag(p, r.valueNumeric(), r.valueText());
            critical = critical || "LL".equals(flag) || "HH".equals(flag);
            jdbc.sql("""
                    insert into diagnostics.lab_result (tenant_id, order_id, parameter_id, value_numeric, value_text, flag, entered_by)
                    values (:t, :o, :p, :n, :x, :f, :u)
                    on conflict (order_id, parameter_id) do update set value_numeric = excluded.value_numeric, value_text = excluded.value_text,
                        flag = excluded.flag, entered_by = excluded.entered_by, entered_at = now()""")
                    .param("t", actor.tenantId()).param("o", orderId).param("p", p.id()).param("n", r.valueNumeric()).param("x", r.valueText())
                    .param("f", flag).param("u", actor.userId()).update();
        }
        int missing = jdbc.sql("""
                select count(*) from diagnostics.lab_parameter p where p.test_id = :t
                  and not exists (select 1 from diagnostics.lab_result r where r.order_id = :o and r.parameter_id = p.id)""")
                .param("t", o.labTestId()).param("o", orderId).query(Integer.class).single();
        if (missing > 0) {
            throw new RuleViolationException("lab.incomplete", missing + " result(s) of " + o.testName() + " are still missing");
        }
        boolean anyCritical = critical || jdbc.sql("select exists(select 1 from diagnostics.lab_result where order_id = :o and flag in ('LL', 'HH'))")
                .param("o", orderId).query(Boolean.class).single();
        set(orderId, "status = 'RESULTED', result_by = :u, result_at = now(), has_critical = :c", Map.of("u", actor.userId(), "c", anyCritical));
        audit(actor, o, "lab.result", Map.of("critical", anyCritical));
        return view(actor, row(orderId));
    }

    /** Validation by a second person (pathologist) releases the report to the doctor. */
    @Transactional
    public OrderView validate(Actor actor, UUID orderId, DiagnosticsModels.Step cmd) {
        OrderRow o = lab(locked(orderId, cmd.expectedVersion()));
        access.require(actor, Permission.LAB_VALIDATE, Scope.centre(o.centreId()), "lab.validate", "diagnostic-order", orderId.toString());
        requireStatus(o, "RESULTED");
        if (actor.userId().equals(o.resultBy())) {
            throw new AccessDeniedException("lab.validate.self", "You entered these results; another person must validate them");
        }
        set(orderId, "status = 'VALIDATED', validated_by = :u, validated_at = now()", Map.of("u", actor.userId()));
        audit(actor, o, "lab.validate", Map.of("critical", o.hasCritical()));
        return view(actor, row(orderId));
    }

    static String flag(ParameterView p, BigDecimal v, String text) {
        if ("TEXT".equals(p.valueType())) {
            if (p.refText() == null || text == null) {
                return null;
            }
            return p.refText().trim().equalsIgnoreCase(text.trim()) ? "N" : "A";
        }
        if (v == null) {
            return null;
        }
        if (p.criticalLow() != null && v.compareTo(p.criticalLow()) < 0) {
            return "LL";
        }
        if (p.criticalHigh() != null && v.compareTo(p.criticalHigh()) > 0) {
            return "HH";
        }
        if (p.refLow() != null && v.compareTo(p.refLow()) < 0) {
            return "L";
        }
        if (p.refHigh() != null && v.compareTo(p.refHigh()) > 0) {
            return "H";
        }
        return "N";
    }

    // ---------------------------------------------------------------- imaging

    @Transactional
    public OrderView schedule(Actor actor, UUID orderId, DiagnosticsModels.Schedule cmd) {
        OrderRow o = imaging(locked(orderId, cmd.expectedVersion()));
        access.require(actor, Permission.RADIOLOGY_PERFORM, Scope.centre(o.centreId()), "radiology.schedule", "diagnostic-order", orderId.toString());
        requireStatus(o, "ORDERED", "SCHEDULED");
        set(orderId, "status = 'SCHEDULED', scheduled_at = :at", Map.of("at", java.sql.Timestamp.from(cmd.scheduledAt())));
        audit(actor, o, "radiology.schedule", Map.of("at", cmd.scheduledAt().toString()));
        return view(actor, row(orderId));
    }

    /**
     * The study is done. The images are found in the archive by the accession number the technician
     * entered on the machine; without an archive, the technician gives the Study Instance UID.
     */
    @Transactional
    public OrderView acquire(Actor actor, UUID orderId, DiagnosticsModels.Acquire cmd) {
        OrderRow o = imaging(locked(orderId, cmd.expectedVersion()));
        access.require(actor, Permission.RADIOLOGY_PERFORM, Scope.centre(o.centreId()), "radiology.acquire", "diagnostic-order", orderId.toString());
        requireStatus(o, "ORDERED", "SCHEDULED", "ACQUIRED");
        String uid = cmd.studyInstanceUid();
        Integer count = cmd.imageCount();
        if (uid == null) {
            var found = archive.findByAccession(o.accessionNo()).orElseThrow(() -> new RuleViolationException("radiology.no-images",
                    archive.enabled() ? "No images with accession number " + o.accessionNo() + " have reached the image archive yet"
                            : "Enter the Study Instance UID (no image archive is connected)"));
            uid = found.studyInstanceUid();
            count = found.instances();
        }
        Map<String, Object> p = new HashMap<>();
        p.put("u", actor.userId());
        p.put("uid", uid);
        p.put("n", count);
        set(orderId, "status = 'ACQUIRED', performed_by = :u, performed_at = now(), study_instance_uid = :uid, image_count = :n", p);
        audit(actor, o, "radiology.acquire", Map.of("study", uid));
        return view(actor, row(orderId));
    }

    @Transactional
    public OrderView report(Actor actor, UUID orderId, DiagnosticsModels.Report cmd) {
        OrderRow o = imaging(locked(orderId, cmd.expectedVersion()));
        access.require(actor, Permission.RADIOLOGY_REPORT, Scope.centre(o.centreId()), "radiology.report", "diagnostic-order", orderId.toString());
        requireStatus(o, "ACQUIRED", "REPORTED");
        set(orderId, "status = 'REPORTED', findings = :f, impression = :i, reported_by = :u, reported_at = now()",
                Map.of("f", cmd.findings(), "i", cmd.impression(), "u", actor.userId()));
        audit(actor, o, "radiology.report", Map.of());
        return view(actor, row(orderId));
    }

    @Transactional
    public OrderView verify(Actor actor, UUID orderId, DiagnosticsModels.Step cmd) {
        OrderRow o = imaging(locked(orderId, cmd.expectedVersion()));
        access.require(actor, Permission.RADIOLOGY_VERIFY, Scope.centre(o.centreId()), "radiology.verify", "diagnostic-order", orderId.toString());
        requireStatus(o, "REPORTED");
        set(orderId, "status = 'VERIFIED', verified_by = :u, verified_at = now()", Map.of("u", actor.userId()));
        audit(actor, o, "radiology.verify", Map.of());
        return view(actor, row(orderId));
    }

    // ---------------------------------------------------------------- reading

    public List<OrderView> worklist(Actor actor, UUID centreId, String kind, String status) {
        Scope scope = Scope.centre(centreId);
        boolean lab = "LAB".equals(kind);
        List<Permission> perms = lab ? List.of(Permission.LAB_READ, Permission.LAB_COLLECT, Permission.LAB_PROCESS, Permission.LAB_VALIDATE)
                : List.of(Permission.RADIOLOGY_READ, Permission.RADIOLOGY_PERFORM, Permission.RADIOLOGY_REPORT, Permission.RADIOLOGY_VERIFY);
        if (perms.stream().noneMatch(p -> access.holds(actor, p, scope))) {
            access.require(actor, perms.getFirst(), scope, lab ? "lab.read" : "radiology.read", "centre", centreId.toString());
        }
        Map<String, Object> p = new HashMap<>();
        p.put("c", centreId);
        p.put("k", lab ? "LAB" : "IMAGING");
        p.put("s", status);
        return jdbc.sql(ORDER_SELECT + """
                 where o.centre_id = :c and o.kind = :k and (cast(:s as text) is null or o.status = :s)
                   and (o.status not in ('VALIDATED', 'VERIFIED', 'CANCELLED') or o.ordered_at > now() - interval '7 days')
                 order by case o.priority when 'STAT' then 0 when 'URGENT' then 1 else 2 end, o.ordered_at limit 500""")
                .params(p).query(OrderRow.class).list().stream().map(r -> view(actor, r)).toList();
    }

    /** A patient's orders that the actor may see (by centre); unreleased results only to the department's own staff. */
    public List<OrderView> forPatient(Actor actor, UUID patientId) {
        return jdbc.sql(ORDER_SELECT + " where o.patient_id = :p order by o.ordered_at desc limit 200").param("p", patientId)
                .query(OrderRow.class).list().stream().filter(r -> canRead(actor, r)).map(r -> view(actor, r)).toList();
    }

    public OrderView get(Actor actor, UUID orderId) {
        OrderRow r = row(orderId);
        if (!canRead(actor, r)) {
            access.require(actor, "LAB".equals(r.kind()) ? Permission.LAB_READ : Permission.RADIOLOGY_READ, Scope.centre(r.centreId()),
                    "diagnostics.read", "diagnostic-order", orderId.toString());
        }
        return view(actor, r);
    }

    boolean canRead(Actor actor, OrderRow r) {
        Scope s = Scope.centre(r.centreId());
        List<Permission> perms = "LAB".equals(r.kind())
                ? List.of(Permission.LAB_READ, Permission.LAB_COLLECT, Permission.LAB_PROCESS, Permission.LAB_VALIDATE)
                : List.of(Permission.RADIOLOGY_READ, Permission.RADIOLOGY_PERFORM, Permission.RADIOLOGY_REPORT, Permission.RADIOLOGY_VERIFY);
        return perms.stream().anyMatch(p -> access.holds(actor, p, s));
    }

    private OrderView view(Actor actor, OrderRow r) {
        boolean lab = "LAB".equals(r.kind());
        Scope s = Scope.centre(r.centreId());
        boolean released = lab ? "VALIDATED".equals(r.status()) : "VERIFIED".equals(r.status());
        boolean department = lab ? access.holds(actor, Permission.LAB_PROCESS, s) || access.holds(actor, Permission.LAB_VALIDATE, s)
                : access.holds(actor, Permission.RADIOLOGY_REPORT, s) || access.holds(actor, Permission.RADIOLOGY_VERIFY, s)
                        || access.holds(actor, Permission.RADIOLOGY_PERFORM, s);
        boolean showResults = released || department;
        List<ResultView> results = lab && showResults ? jdbc.sql("""
                select p.id as parameter_id, p.code, p.name, p.unit, p.loinc_code, r.value_numeric, r.value_text, r.flag,
                       case when p.ref_text is not null then p.ref_text
                            when p.ref_low is not null and p.ref_high is not null then p.ref_low::float8 || ' – ' || p.ref_high::float8
                            when p.ref_low is not null then '≥ ' || p.ref_low::float8 when p.ref_high is not null then '≤ ' || p.ref_high::float8
                       end as reference
                from diagnostics.lab_parameter p left join diagnostics.lab_result r on r.parameter_id = p.id and r.order_id = :o
                where p.test_id = :t order by p.sort_order""").param("o", r.id()).param("t", r.labTestId()).query(ResultView.class).list()
                : List.of();
        boolean images = !lab && r.studyInstanceUid() != null && (department || access.holds(actor, Permission.RADIOLOGY_READ, s));
        String viewer = images ? props.basePath() + "/api/pacs/ohif/viewer?StudyInstanceUIDs=" + r.studyInstanceUid() : null;
        return new OrderView(r.id(), r.centreId(), r.requestNo(), r.accessionNo(), r.patientId(), r.patientUhid(), r.patientName(),
                r.patientSex(), r.patientAge(), r.visitRef(), r.kind(), r.testCode(), r.testName(), r.section(), r.sampleType(), r.modality(),
                r.bodyPart(), r.priority(), r.clinicalNotes(), r.status(), r.billId(), r.billNo(), r.billStatus(), r.orderedByName(),
                r.orderedAt(), r.collectedByName(), r.collectedAt(), r.receivedAt(), r.rejectionNote(), r.resultByName(), r.resultAt(),
                r.validatedByName(), r.validatedAt(), showResults && r.hasCritical(), r.scheduledAt(), r.performedByName(), r.performedAt(),
                r.studyInstanceUid(), r.imageCount(), showResults ? r.findings() : null, showResults ? r.impression() : null,
                r.reportedByName(), r.reportedAt(), r.verifiedByName(), r.verifiedAt(), r.cancelReason(), r.version(), released, viewer,
                results);
    }

    // ---------------------------------------------------------------- helpers

    private OrderRow row(UUID id) {
        return jdbc.sql(ORDER_SELECT + " where o.id = :id").param("id", id).query(OrderRow.class).optional()
                .orElseThrow(() -> new NotFoundException("Order not found"));
    }

    private OrderRow locked(UUID id, Integer expectedVersion) {
        jdbc.sql("select id from diagnostics.diagnostic_order where id = :id for update").param("id", id).query(UUID.class).optional()
                .orElseThrow(() -> new NotFoundException("Order not found"));
        OrderRow r = row(id);
        if (expectedVersion != null && expectedVersion != r.version()) {
            throw new ConflictException("diagnostics.changed", "Someone else changed this order. Reload and try again.");
        }
        if ("CANCELLED".equals(r.status())) {
            throw new RuleViolationException("diagnostics.cancelled", "Order " + r.accessionNo() + " was cancelled");
        }
        return r;
    }

    private static OrderRow lab(OrderRow r) {
        if (!"LAB".equals(r.kind())) {
            throw new RuleViolationException("diagnostics.kind", r.accessionNo() + " is not a laboratory order");
        }
        return r;
    }

    private static OrderRow imaging(OrderRow r) {
        if (!"IMAGING".equals(r.kind())) {
            throw new RuleViolationException("diagnostics.kind", r.accessionNo() + " is not an imaging order");
        }
        return r;
    }

    private static void requireStatus(OrderRow r, String... allowed) {
        if (!List.of(allowed).contains(r.status())) {
            throw new RuleViolationException("diagnostics.state", r.accessionNo() + " is " + r.status().toLowerCase().replace('_', ' ')
                    + "; that step is not possible now");
        }
    }

    private void set(UUID id, String assignments, Map<String, Object> params) {
        Map<String, Object> p = new HashMap<>(params);
        p.put("id", id);
        jdbc.sql("update diagnostics.diagnostic_order set " + assignments + ", version = version + 1 where id = :id").params(p).update();
    }

    private void audit(Actor actor, OrderRow o, String action, Map<String, Object> extra) {
        Map<String, Object> d = new HashMap<>(extra);
        d.put("accessionNo", o.accessionNo());
        d.put("patient", o.patientUhid());
        d.put("test", o.testName());
        audit.record(AuditEntry.success(actor, action, "diagnostic-order", o.id(), Scope.centre(o.centreId()), d));
    }

    private String number(Actor actor, UUID centreId, String centreCode, String type) {
        String period = financialYear();
        int seq = jdbc.sql("""
                insert into inventory.doc_counter (tenant_id, centre_id, doc_type, period, last_value) values (:t, :c, :d, :p, 1)
                on conflict (centre_id, doc_type, period) do update set last_value = inventory.doc_counter.last_value + 1
                returning last_value""").param("t", actor.tenantId()).param("c", centreId).param("d", type).param("p", period)
                .query(Integer.class).single();
        return centreCode + type + period.replace("-", "").substring(2) + String.format("%06d", seq);
    }

    private static String financialYear() {
        var d = BusinessTime.today();
        int start = d.getMonthValue() >= 4 ? d.getYear() : d.getYear() - 1;
        return start + "-" + String.format("%02d", (start + 1) % 100);
    }
}
