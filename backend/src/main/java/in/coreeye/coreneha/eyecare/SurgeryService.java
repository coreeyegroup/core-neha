package in.coreeye.coreneha.eyecare;

import static in.coreeye.coreneha.eyecare.EyeSql.blank;
import static in.coreeye.coreneha.eyecare.VisitService.changed;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import in.coreeye.coreneha.audit.AuditEntry;
import in.coreeye.coreneha.audit.AuditLog;
import in.coreeye.coreneha.billing.BillingDirectory;
import in.coreeye.coreneha.eyecare.EyeModels.SurgeryView;
import in.coreeye.coreneha.eyecare.EyeModels.VisitView;
import in.coreeye.coreneha.kernel.AccessControl;
import in.coreeye.coreneha.kernel.Actor;
import in.coreeye.coreneha.kernel.BusinessTime;
import in.coreeye.coreneha.kernel.ConflictException;
import in.coreeye.coreneha.kernel.NotFoundException;
import in.coreeye.coreneha.kernel.Permission;
import in.coreeye.coreneha.kernel.RuleViolationException;
import in.coreeye.coreneha.kernel.Scope;
import in.coreeye.coreneha.workforce.WorkforceDirectory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Eye surgery from the doctor's advice to the operative note:
 * ADVISED -> (counselling) ACCEPTED or DECLINED -> SCHEDULED -> READY (pre-operative
 * checklist done) -> COMPLETED. The database refuses READY or COMPLETED without
 * consent, site marking and fitness, and COMPLETED without anaesthesia and notes.
 */
@Service
public class SurgeryService {

    private final JdbcClient jdbc;
    private final AccessControl access;
    private final AuditLog audit;
    private final WorkforceDirectory workforce;
    private final BillingDirectory billing;

    SurgeryService(JdbcClient jdbc, AccessControl access, AuditLog audit, WorkforceDirectory workforce, BillingDirectory billing) {
        this.jdbc = jdbc;
        this.access = access;
        this.audit = audit;
        this.workforce = workforce;
        this.billing = billing;
    }

    void createFromAdvice(Actor actor, VisitView visit, String procedure, String eye, String urgency) {
        boolean exists = jdbc.sql("select exists(select 1 from eyecare.surgery_case where visit_id = :v and status <> 'CANCELLED')")
                .param("v", visit.id()).query(Boolean.class).single();
        if (exists) {
            return;
        }
        UUID id = jdbc.sql("""
                insert into eyecare.surgery_case (tenant_id, centre_id, patient_id, visit_id, procedure_name, eye, urgency, status,
                    advised_by_person_id)
                values (:t, :c, :p, :v, :proc, :eye, :u, 'ADVISED', :doc) returning id
                """).param("t", actor.tenantId()).param("c", visit.centreId()).param("p", visit.patientId()).param("v", visit.id())
                .param("proc", procedure).param("eye", eye).param("u", urgency).param("doc", actor.personId())
                .query(UUID.class).single();
        audit.record(AuditEntry.success(actor, "eyecare.surgery.advise", "surgery_case", id, Scope.centre(visit.centreId()),
                Map.of("patient", visit.patientUhid(), "procedure", procedure, "eye", eye)));
    }

    // ---------------------------------------------------------------- queries

    /**
     * Surgery lists by work queue: COUNSELLING (advised, waiting for counselling), SCHEDULING
     * (accepted, no date yet), OT_LIST (the theatre list of one day), UPCOMING, RECENT.
     */
    @Transactional(readOnly = true)
    public List<SurgeryView> list(Actor actor, UUID centreId, String view, LocalDate date) {
        access.require(actor, Permission.EYE_CLINICAL_READ, Scope.centre(centreId), "eyecare.surgery.list", "surgery_case", null);
        LocalDate d = date == null ? BusinessTime.today() : date;
        String where = switch (view == null ? "RECENT" : view) {
            case "COUNSELLING" -> "s.status = 'ADVISED' order by s.created_at";
            case "SCHEDULING" -> "s.status = 'ACCEPTED' order by s.preferred_date nulls last, s.counselled_at";
            case "OT_LIST" -> "s.surgery_date = :d and s.status in ('SCHEDULED', 'READY', 'COMPLETED') order by s.slot_time";
            case "UPCOMING" -> "s.surgery_date >= :d and s.status in ('SCHEDULED', 'READY') order by s.surgery_date, s.slot_time";
            case "RECENT" -> "true order by s.created_at desc limit 200";
            default -> throw new RuleViolationException("eyecare.surgery.view", "Unknown list: " + view);
        };
        return jdbc.sql(EyeSql.SURGERY + " where s.centre_id = :c and " + where).param("c", centreId).param("d", d)
                .query(SurgeryView.class).list();
    }

    @Transactional(readOnly = true)
    public SurgeryView surgery(Actor actor, UUID surgeryId) {
        SurgeryView s = row(surgeryId);
        access.require(actor, Permission.EYE_CLINICAL_READ, Scope.centre(s.centreId()), "eyecare.surgery.view", "surgery_case",
                surgeryId.toString());
        return s;
    }

    SurgeryView forVisit(UUID visitId) {
        return jdbc.sql(EyeSql.SURGERY + " where s.visit_id = :v order by s.created_at desc limit 1").param("v", visitId)
                .query(SurgeryView.class).optional().orElse(null);
    }

    List<SurgeryView> forPatient(UUID patientId) {
        return jdbc.sql(EyeSql.SURGERY + " where s.patient_id = :p order by s.created_at desc").param("p", patientId)
                .query(SurgeryView.class).list();
    }

    @Transactional(readOnly = true)
    public List<BillingDirectory.BillSummary> bills(Actor actor, UUID surgeryId) {
        SurgeryView s = surgery(actor, surgeryId);
        return billing.billsForSurgery(s.id());
    }

    // ---------------------------------------------------------------- counselling and scheduling

    @Transactional
    public SurgeryView counsel(Actor actor, UUID surgeryId, EyeModels.Counsel cmd) {
        SurgeryView s = row(surgeryId);
        Scope scope = Scope.centre(s.centreId());
        access.require(actor, Permission.EYE_COUNSELLING_RECORD, scope, "eyecare.surgery.counsel", "surgery_case", surgeryId.toString());
        if (!Set.of("ADVISED", "ACCEPTED", "DECLINED").contains(s.status())) {
            throw new RuleViolationException("eyecare.surgery.status", "Counselling is closed once the surgery is scheduled");
        }
        String packageName = null;
        BigDecimal cost = cmd.estimatedCost();
        if (cmd.packageItemId() != null) {
            var item = billing.tariffRef(cmd.packageItemId()).filter(t -> t.centreId().equals(s.centreId()) && "ACTIVE".equals(t.status()))
                    .orElseThrow(() -> new RuleViolationException("eyecare.surgery.package", "Choose a package from this centre's price list"));
            packageName = item.name();
            if (cost == null) {
                cost = item.price();
            }
        }
        if ("ACCEPTED".equals(cmd.decision()) && cmd.packageItemId() == null) {
            throw new RuleViolationException("eyecare.surgery.package-required", "Choose the surgery package the patient accepted");
        }
        if ("INSURANCE".equals(cmd.paymentPlan()) && blank(cmd.insurerName()) == null) {
            throw new RuleViolationException("eyecare.surgery.insurer", "Enter the insurer or TPA name");
        }
        String status = switch (cmd.decision()) {
            case "ACCEPTED" -> "ACCEPTED";
            case "DECLINED" -> "DECLINED";
            default -> "ADVISED";
        };
        int n = jdbc.sql("""
                update eyecare.surgery_case set status = :st, package_item_id = :pkg, package_name = :pkgName, estimated_cost = :cost,
                    payment_plan = :plan, insurer_name = :ins, counselling_notes = :notes, preferred_date = :pref,
                    counselled_by = :by, counselled_at = now(), version = version + 1
                where id = :id and version = :v
                """).param("st", status).param("pkg", cmd.packageItemId()).param("pkgName", packageName).param("cost", cost)
                .param("plan", blank(cmd.paymentPlan())).param("ins", blank(cmd.insurerName())).param("notes", blank(cmd.notes()))
                .param("pref", cmd.preferredDate()).param("by", actor.userId()).param("id", surgeryId).param("v", cmd.expectedVersion())
                .update();
        changed(n);
        // The visit that led to the advice moves on to checkout once counselling is done.
        jdbc.sql("""
                update eyecare.visit set stage = 'CHECKOUT', counselling_done_at = now(), version = version + 1
                where id = :v and stage = 'COUNSELLING'
                """).param("v", s.visitId()).update();
        audit.record(AuditEntry.success(actor, "eyecare.surgery.counsel", "surgery_case", surgeryId, scope,
                Map.of("patient", s.patientUhid(), "decision", cmd.decision(), "package", String.valueOf(packageName))));
        return row(surgeryId);
    }

    @Transactional
    public SurgeryView schedule(Actor actor, UUID surgeryId, EyeModels.ScheduleSurgery cmd) {
        SurgeryView s = row(surgeryId);
        Scope scope = Scope.centre(s.centreId());
        access.require(actor, Permission.EYE_SURGERY_SCHEDULE, scope, "eyecare.surgery.schedule", "surgery_case", surgeryId.toString());
        if (!Set.of("ACCEPTED", "SCHEDULED").contains(s.status())) {
            throw new RuleViolationException("eyecare.surgery.status", "Only an accepted surgery can be scheduled (now: " + s.status().toLowerCase() + ")");
        }
        if (cmd.surgeryDate().isBefore(BusinessTime.today())) {
            throw new RuleViolationException("eyecare.surgery.past", "A surgery cannot be scheduled in the past");
        }
        boolean surgeonHere = workforce.staffAt(s.centreId(), null).stream().anyMatch(x -> x.personId().equals(cmd.surgeonPersonId()));
        if (!surgeonHere) {
            throw new RuleViolationException("eyecare.clinician.not-posted", "The chosen surgeon does not work at this centre");
        }
        boolean clash = jdbc.sql("""
                select exists(select 1 from eyecare.surgery_case where id <> :id and surgeon_person_id = :doc
                    and surgery_date = :d and slot_time = :t and status in ('SCHEDULED', 'READY'))
                """).param("id", surgeryId).param("doc", cmd.surgeonPersonId()).param("d", cmd.surgeryDate()).param("t", cmd.slotTime())
                .query(Boolean.class).single();
        if (clash) {
            throw new ConflictException("eyecare.surgery.slot-taken", "The surgeon already has a surgery at this time");
        }
        int n = jdbc.sql("""
                update eyecare.surgery_case set status = 'SCHEDULED', surgery_date = :d, slot_time = :t, ot_room = :room,
                    surgeon_person_id = :doc, scheduled_by = :by, version = version + 1 where id = :id and version = :v
                """).param("d", cmd.surgeryDate()).param("t", cmd.slotTime()).param("room", blank(cmd.otRoom()))
                .param("doc", cmd.surgeonPersonId()).param("by", actor.userId()).param("id", surgeryId)
                .param("v", cmd.expectedVersion()).update();
        changed(n);
        audit.record(AuditEntry.success(actor, "eyecare.surgery.schedule", "surgery_case", surgeryId, scope,
                Map.of("patient", s.patientUhid(), "date", cmd.surgeryDate().toString(), "time", cmd.slotTime().toString())));
        return row(surgeryId);
    }

    @Transactional
    public SurgeryView cancel(Actor actor, UUID surgeryId, EyeModels.CancelSurgery cmd) {
        SurgeryView s = row(surgeryId);
        Scope scope = Scope.centre(s.centreId());
        access.require(actor, Permission.EYE_SURGERY_SCHEDULE, scope, "eyecare.surgery.cancel", "surgery_case", surgeryId.toString());
        if (Set.of("COMPLETED", "CANCELLED").contains(s.status())) {
            throw new RuleViolationException("eyecare.surgery.status", "This surgery is already " + s.status().toLowerCase());
        }
        int n = jdbc.sql("""
                update eyecare.surgery_case set status = 'CANCELLED', cancel_reason = :r, version = version + 1
                where id = :id and version = :v
                """).param("r", cmd.reason().trim()).param("id", surgeryId).param("v", cmd.expectedVersion()).update();
        changed(n);
        audit.record(AuditEntry.success(actor, "eyecare.surgery.cancel", "surgery_case", surgeryId, scope,
                Map.of("patient", s.patientUhid(), "reason", cmd.reason())));
        return row(surgeryId);
    }

    // ---------------------------------------------------------------- theatre

    @Transactional
    public SurgeryView preOp(Actor actor, UUID surgeryId, EyeModels.PreOp cmd) {
        SurgeryView s = row(surgeryId);
        Scope scope = Scope.centre(s.centreId());
        access.require(actor, Permission.EYE_SURGERY_PREPARE, scope, "eyecare.surgery.pre-op", "surgery_case", surgeryId.toString());
        if (!Set.of("SCHEDULED", "READY").contains(s.status())) {
            throw new RuleViolationException("eyecare.surgery.status", "The pre-operative checklist is for scheduled surgeries");
        }
        if (cmd.ready() && !(cmd.consentSigned() && cmd.siteMarked() && cmd.fitnessCleared())) {
            throw new RuleViolationException("eyecare.surgery.checklist",
                    "Consent, operative-eye marking and medical fitness must all be confirmed before the patient is ready");
        }
        Map<String, Object> p = new HashMap<>();
        p.put("st", cmd.ready() ? "READY" : "SCHEDULED");
        p.put("consent", cmd.consentSigned());
        p.put("site", cmd.siteMarked());
        p.put("fit", cmd.fitnessCleared());
        p.put("bio", cmd.biometryDone());
        p.put("iol", blank(cmd.iolPower()));
        p.put("pupil", cmd.pupilDilated());
        p.put("bp", blank(cmd.preOpBp()));
        p.put("sugar", blank(cmd.preOpBloodSugar()));
        p.put("notes", blank(cmd.preOpNotes()));
        p.put("by", actor.userId());
        p.put("id", surgeryId);
        p.put("v", cmd.expectedVersion());
        int n = jdbc.sql("""
                update eyecare.surgery_case set status = :st, consent_signed = :consent, site_marked = :site, fitness_cleared = :fit,
                    biometry_done = :bio, iol_power = :iol, pupil_dilated = :pupil, pre_op_bp = :bp, pre_op_blood_sugar = :sugar,
                    pre_op_notes = :notes, prepared_by = :by, prepared_at = now(), version = version + 1
                where id = :id and version = :v
                """).params(p).update();
        changed(n);
        audit.record(AuditEntry.success(actor, cmd.ready() ? "eyecare.surgery.ready" : "eyecare.surgery.pre-op", "surgery_case",
                surgeryId, scope, Map.of("patient", s.patientUhid())));
        return row(surgeryId);
    }

    @Transactional
    public SurgeryView operate(Actor actor, UUID surgeryId, EyeModels.Operation cmd) {
        SurgeryView s = row(surgeryId);
        Scope scope = Scope.centre(s.centreId());
        access.require(actor, Permission.EYE_SURGERY_OPERATE, scope, "eyecare.surgery.operate", "surgery_case", surgeryId.toString());
        if (!"READY".equals(s.status())) {
            throw new RuleViolationException("eyecare.surgery.not-ready", "Complete the pre-operative checklist first (now: " + s.status().toLowerCase() + ")");
        }
        if (cmd.complete() && (blank(cmd.anaesthesia()) == null || blank(cmd.operativeNotes()) == null)) {
            throw new RuleViolationException("eyecare.surgery.notes", "Record the anaesthesia and the operative notes before completing");
        }
        int n = jdbc.sql("""
                update eyecare.surgery_case set status = :st, anaesthesia = :an, iol_implanted = :iol, operative_notes = :notes,
                    complications = :comp, post_op_instructions = :post, operated_by_person_id = :doc,
                    started_at = coalesce(started_at, now()), completed_at = :done, recorded_by = :by, version = version + 1
                where id = :id and version = :v
                """).param("st", cmd.complete() ? "COMPLETED" : "READY").param("an", blank(cmd.anaesthesia()))
                .param("iol", blank(cmd.iolImplanted())).param("notes", blank(cmd.operativeNotes()))
                .param("comp", blank(cmd.complications())).param("post", blank(cmd.postOpInstructions()))
                .param("doc", actor.personId()).param("done", cmd.complete() ? java.time.OffsetDateTime.now() : null)
                .param("by", actor.userId()).param("id", surgeryId).param("v", cmd.expectedVersion()).update();
        changed(n);
        if (cmd.complete()) {
            // First post-operative review: next day, with the operating surgeon.
            LocalDate review = BusinessTime.today().plusDays(1);
            jdbc.sql("""
                    insert into eyecare.appointment (tenant_id, centre_id, patient_id, appointment_date, slot_time, visit_type, source,
                        consultant_person_id, reason, created_by)
                    values (:t, :c, :p, :d, :time, 'POST_OP', 'REFERRAL', :doc, :r, :by)
                    """).param("t", actor.tenantId()).param("c", s.centreId()).param("p", s.patientId()).param("d", review)
                    .param("time", LocalTime.of(10, 0)).param("doc", actor.personId())
                    .param("r", "Day-1 review after " + s.procedureName() + " (" + s.eye() + ")").param("by", actor.userId()).update();
        }
        audit.record(AuditEntry.success(actor, cmd.complete() ? "eyecare.surgery.complete" : "eyecare.surgery.operative-note",
                "surgery_case", surgeryId, scope, Map.of("patient", s.patientUhid(), "procedure", s.procedureName())));
        return row(surgeryId);
    }

    SurgeryView row(UUID id) {
        return jdbc.sql(EyeSql.SURGERY + " where s.id = :id").param("id", id).query(SurgeryView.class).optional()
                .orElseThrow(() -> new NotFoundException("Surgery not found"));
    }
}
