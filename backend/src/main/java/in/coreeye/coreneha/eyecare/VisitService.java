package in.coreeye.coreneha.eyecare;

import static in.coreeye.coreneha.eyecare.EyeSql.blank;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import in.coreeye.coreneha.audit.AuditEntry;
import in.coreeye.coreneha.audit.AuditLog;
import in.coreeye.coreneha.billing.BillingDirectory;
import in.coreeye.coreneha.eyecare.EyeModels.AppointmentView;
import in.coreeye.coreneha.eyecare.EyeModels.ClinicBoard;
import in.coreeye.coreneha.eyecare.EyeModels.ClinicianView;
import in.coreeye.coreneha.eyecare.EyeModels.ConsultationView;
import in.coreeye.coreneha.eyecare.EyeModels.HistoryVisit;
import in.coreeye.coreneha.eyecare.EyeModels.OptometryView;
import in.coreeye.coreneha.eyecare.EyeModels.PatientEyeHistory;
import in.coreeye.coreneha.eyecare.EyeModels.SurgeryView;
import in.coreeye.coreneha.eyecare.EyeModels.VisitChart;
import in.coreeye.coreneha.eyecare.EyeModels.VisitView;
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
import in.coreeye.coreneha.workforce.WorkforceDirectory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Appointments and the outpatient visit. A visit moves through fixed stages:
 * OPTOMETRY -> CONSULTATION -> (COUNSELLING if surgery is advised) -> CHECKOUT -> COMPLETED.
 * Each stage is completed only by the department that owns it, and checkout only
 * when nothing is left to pay.
 */
@Service
public class VisitService {

    private final JdbcClient jdbc;
    private final AccessControl access;
    private final AuditLog audit;
    private final OrgDirectory org;
    private final PatientDirectory patients;
    private final WorkforceDirectory workforce;
    private final BillingDirectory billing;
    private final SurgeryService surgeries;

    VisitService(JdbcClient jdbc, AccessControl access, AuditLog audit, OrgDirectory org, PatientDirectory patients,
                 WorkforceDirectory workforce, BillingDirectory billing, SurgeryService surgeries) {
        this.jdbc = jdbc;
        this.access = access;
        this.audit = audit;
        this.org = org;
        this.patients = patients;
        this.workforce = workforce;
        this.billing = billing;
        this.surgeries = surgeries;
    }

    // ---------------------------------------------------------------- clinicians

    @Transactional(readOnly = true)
    public List<ClinicianView> clinicians(Actor actor, UUID centreId, String departmentCode) {
        requireAnyAt(actor, centreId, "eyecare.clinicians.list", Permission.OPD_QUEUE_READ, Permission.EYE_CLINICAL_READ);
        return workforce.staffAt(centreId, blank(departmentCode)).stream()
                .map(s -> new ClinicianView(s.personId(), s.name(), s.positionTitle(), s.departmentCode(), s.departmentName()))
                .toList();
    }

    // ---------------------------------------------------------------- appointments

    @Transactional(readOnly = true)
    public List<AppointmentView> appointments(Actor actor, UUID centreId, LocalDate date) {
        requireAnyAt(actor, centreId, "eyecare.appointment.list", Permission.OPD_QUEUE_READ);
        return jdbc.sql(EyeSql.APPOINTMENT + " where a.centre_id = :c and a.appointment_date = :d order by a.slot_time, a.created_at")
                .param("c", centreId).param("d", date == null ? BusinessTime.today() : date).query(AppointmentView.class).list();
    }

    @Transactional
    public AppointmentView book(Actor actor, UUID centreId, EyeModels.BookAppointment cmd) {
        Scope scope = activeCentre(centreId);
        access.require(actor, Permission.OPD_APPOINTMENT_MANAGE, scope, "eyecare.appointment.book", "appointment", null);
        var patient = activePatient(cmd.patientId());
        if (cmd.appointmentDate().isBefore(BusinessTime.today())) {
            throw new RuleViolationException("eyecare.appointment.past", "An appointment cannot be booked in the past");
        }
        checkClinician(centreId, cmd.consultantPersonId());
        boolean already = jdbc.sql("""
                select exists(select 1 from eyecare.appointment where centre_id = :c and patient_id = :p
                    and appointment_date = :d and status = 'BOOKED')
                """).param("c", centreId).param("p", cmd.patientId()).param("d", cmd.appointmentDate()).query(Boolean.class).single();
        if (already) {
            throw new ConflictException("eyecare.appointment.duplicate", patient.fullName() + " already has an appointment on this day");
        }
        UUID id = jdbc.sql("""
                insert into eyecare.appointment (tenant_id, centre_id, patient_id, appointment_date, slot_time, visit_type, source,
                    consultant_person_id, reason, created_by)
                values (:t, :c, :p, :d, :s, :vt, :src, :doc, :r, :by) returning id
                """).param("t", actor.tenantId()).param("c", centreId).param("p", cmd.patientId()).param("d", cmd.appointmentDate())
                .param("s", cmd.slotTime()).param("vt", cmd.visitType()).param("src", cmd.source())
                .param("doc", cmd.consultantPersonId()).param("r", blank(cmd.reason())).param("by", actor.userId())
                .query(UUID.class).single();
        audit.record(AuditEntry.success(actor, "eyecare.appointment.book", "appointment", id, scope,
                Map.of("patient", patient.uhid(), "date", cmd.appointmentDate().toString(), "time", cmd.slotTime().toString())));
        return appointmentRow(id);
    }

    @Transactional
    public AppointmentView cancelAppointment(Actor actor, UUID appointmentId, EyeModels.CancelAppointment cmd) {
        AppointmentView a = appointmentRow(appointmentId);
        Scope scope = Scope.centre(a.centreId());
        access.require(actor, Permission.OPD_APPOINTMENT_MANAGE, scope, "eyecare.appointment.cancel", "appointment", appointmentId.toString());
        if (!"BOOKED".equals(a.status())) {
            throw new RuleViolationException("eyecare.appointment.not-booked", "Only a booked appointment can be cancelled");
        }
        int n = jdbc.sql("""
                update eyecare.appointment set status = :s, cancel_reason = :r, version = version + 1 where id = :id and version = :v
                """).param("s", cmd.noShow() ? "NO_SHOW" : "CANCELLED").param("r", cmd.reason().trim()).param("id", appointmentId)
                .param("v", cmd.expectedVersion()).update();
        changed(n);
        audit.record(AuditEntry.success(actor, cmd.noShow() ? "eyecare.appointment.no-show" : "eyecare.appointment.cancel",
                "appointment", appointmentId, scope, Map.of("reason", cmd.reason())));
        return appointmentRow(appointmentId);
    }

    @Transactional
    public VisitView checkIn(Actor actor, UUID appointmentId, EyeModels.CheckInAppointment cmd) {
        AppointmentView a = appointmentRow(appointmentId);
        Scope scope = activeCentre(a.centreId());
        access.require(actor, Permission.OPD_CHECKIN, scope, "eyecare.visit.check-in", "appointment", appointmentId.toString());
        if (!"BOOKED".equals(a.status())) {
            throw new RuleViolationException("eyecare.appointment.not-booked", "This appointment is " + a.status().toLowerCase().replace('_', ' '));
        }
        if (!a.appointmentDate().equals(BusinessTime.today())) {
            throw new RuleViolationException("eyecare.appointment.not-today", "Only today's appointments can be checked in; reschedule others");
        }
        if (a.version() != cmd.expectedVersion()) {
            throw new ConflictException("eyecare.appointment.changed", "Someone else changed this appointment. Reload and try again.");
        }
        UUID consultant = cmd.consultantPersonId() != null ? cmd.consultantPersonId() : a.consultantPersonId();
        UUID visitId = openVisit(actor, a.centreId(), a.patientId(), a.visitType(),
                cmd.chiefComplaint() != null ? cmd.chiefComplaint() : a.reason(), consultant, appointmentId);
        jdbc.sql("update eyecare.appointment set status = 'CHECKED_IN', visit_id = :v, version = version + 1 where id = :id")
                .param("v", visitId).param("id", appointmentId).update();
        return visitRow(visitId);
    }

    // ---------------------------------------------------------------- visits

    @Transactional
    public VisitView startWalkIn(Actor actor, UUID centreId, EyeModels.StartVisit cmd) {
        Scope scope = activeCentre(centreId);
        access.require(actor, Permission.OPD_CHECKIN, scope, "eyecare.visit.check-in", "visit", null);
        UUID visitId = openVisit(actor, centreId, cmd.patientId(), cmd.visitType(), cmd.chiefComplaint(), cmd.consultantPersonId(), null);
        return visitRow(visitId);
    }

    private UUID openVisit(Actor actor, UUID centreId, UUID patientId, String visitType, String complaint, UUID consultant,
                           UUID appointmentId) {
        var patient = activePatient(patientId);
        checkClinician(centreId, consultant);
        LocalDate today = BusinessTime.today();
        boolean open = jdbc.sql("""
                select exists(select 1 from eyecare.visit where centre_id = :c and patient_id = :p and visit_date = :d
                    and stage not in ('COMPLETED', 'CANCELLED'))
                """).param("c", centreId).param("p", patientId).param("d", today).query(Boolean.class).single();
        if (open) {
            throw new ConflictException("eyecare.visit.already-open", patient.fullName() + " is already checked in today");
        }
        int token = jdbc.sql("""
                insert into eyecare.daily_counter (tenant_id, centre_id, business_date, kind, last_value) values (:t, :c, :d, 'TOKEN', 1)
                on conflict (centre_id, business_date, kind) do update set last_value = eyecare.daily_counter.last_value + 1
                returning last_value
                """).param("t", actor.tenantId()).param("c", centreId).param("d", today).query(Integer.class).single();
        UUID id = jdbc.sql("""
                insert into eyecare.visit (tenant_id, centre_id, patient_id, appointment_id, visit_date, token_no, visit_type,
                    chief_complaint, consultant_person_id, stage, checked_in_by)
                values (:t, :c, :p, :a, :d, :tok, :vt, :cc, :doc, 'OPTOMETRY', :by) returning id
                """).param("t", actor.tenantId()).param("c", centreId).param("p", patientId).param("a", appointmentId)
                .param("d", today).param("tok", token).param("vt", visitType).param("cc", blank(complaint)).param("doc", consultant)
                .param("by", actor.userId()).query(UUID.class).single();
        audit.record(AuditEntry.success(actor, "eyecare.visit.check-in", "visit", id, Scope.centre(centreId),
                Map.of("patient", patient.uhid(), "token", token, "visitType", visitType)));
        return id;
    }

    @Transactional(readOnly = true)
    public List<VisitView> visits(Actor actor, UUID centreId, LocalDate date, String stage) {
        requireAnyAt(actor, centreId, "eyecare.visit.list", Permission.OPD_QUEUE_READ, Permission.EYE_CLINICAL_READ);
        return jdbc.sql(EyeSql.VISIT + """
                 where v.centre_id = :c and v.visit_date = :d and (cast(:s as text) is null or v.stage = :s) order by v.token_no
                """).param("c", centreId).param("d", date == null ? BusinessTime.today() : date).param("s", blank(stage))
                .query(VisitView.class).list();
    }

    @Transactional(readOnly = true)
    public VisitChart chart(Actor actor, UUID visitId) {
        VisitView v = visitRow(visitId);
        Scope scope = Scope.centre(v.centreId());
        requireAnyAt(actor, v.centreId(), "eyecare.visit.view", Permission.OPD_QUEUE_READ, Permission.EYE_CLINICAL_READ);
        boolean clinical = access.holds(actor, Permission.EYE_CLINICAL_READ, scope);
        OptometryView optometry = clinical ? optometryRow(visitId) : null;
        ConsultationView consultation = clinical ? consultationRow(visitId) : null;
        SurgeryView surgery = clinical ? surgeries.forVisit(visitId) : null;
        Set<UUID> visibleCentres = visibleCentres(actor);
        List<VisitView> previous = jdbc.sql(EyeSql.VISIT + " where v.patient_id = :p and v.id <> :id order by v.visit_date desc, v.checked_in_at desc limit 10")
                .param("p", v.patientId()).param("id", visitId).query(VisitView.class).list().stream()
                .filter(x -> visibleCentres.contains(x.centreId())).toList();
        if (clinical) {
            audit.recordIndependently(AuditEntry.success(actor, "eyecare.visit.view", "visit", visitId, scope,
                    Map.of("patient", v.patientUhid())));
        }
        // Financial details are gated separately: clinical access does not reveal bills.
        boolean money = access.holds(actor, Permission.BILLING_READ, scope) || access.holds(actor, Permission.OPD_CHECKIN, scope);
        return new VisitChart(v, clinical, optometry, consultation, surgery, money, money ? billing.billsForVisit(visitId) : List.of(),
                previous);
    }

    @Transactional
    public VisitChart saveOptometry(Actor actor, UUID visitId, EyeModels.SaveOptometry cmd) {
        VisitView v = visitRow(visitId);
        Scope scope = Scope.centre(v.centreId());
        access.require(actor, Permission.EYE_OPTOMETRY_RECORD, scope, "eyecare.optometry.save", "visit", visitId.toString());
        if (!Set.of("OPTOMETRY", "CONSULTATION").contains(v.stage())) {
            throw new RuleViolationException("eyecare.visit.stage", "Optometry can be recorded only before the consultation is complete");
        }
        if (cmd.complete() && !"OPTOMETRY".equals(v.stage())) {
            throw new RuleViolationException("eyecare.visit.stage", "Optometry for this visit is already complete");
        }
        if (cmd.complete() && blank(cmd.ucvaRe()) == null && blank(cmd.ucvaLe()) == null && blank(cmd.bcvaRe()) == null
                && blank(cmd.bcvaLe()) == null) {
            throw new RuleViolationException("eyecare.optometry.incomplete", "Record at least the vision of each eye before sending to the doctor");
        }
        Map<String, Object> p = new HashMap<>();
        p.put("visit", visitId);
        p.put("t", actor.tenantId());
        p.put("ucvaRe", blank(cmd.ucvaRe()));
        p.put("ucvaLe", blank(cmd.ucvaLe()));
        p.put("pgvaRe", blank(cmd.pgvaRe()));
        p.put("pgvaLe", blank(cmd.pgvaLe()));
        p.put("nearRe", blank(cmd.nearRe()));
        p.put("nearLe", blank(cmd.nearLe()));
        p.put("sphRe", cmd.sphRe());
        p.put("cylRe", cmd.cylRe());
        p.put("axisRe", cmd.axisRe());
        p.put("addRe", cmd.addRe());
        p.put("bcvaRe", blank(cmd.bcvaRe()));
        p.put("sphLe", cmd.sphLe());
        p.put("cylLe", cmd.cylLe());
        p.put("axisLe", cmd.axisLe());
        p.put("addLe", cmd.addLe());
        p.put("bcvaLe", blank(cmd.bcvaLe()));
        p.put("iopRe", cmd.iopRe());
        p.put("iopLe", cmd.iopLe());
        p.put("iopMethod", blank(cmd.iopMethod()));
        p.put("colour", blank(cmd.colourVision()));
        p.put("notes", blank(cmd.notes()));
        p.put("by", actor.userId());
        p.put("v", cmd.expectedVersion());
        int n = jdbc.sql("""
                insert into eyecare.optometry_record as o (visit_id, tenant_id, ucva_re, ucva_le, pgva_re, pgva_le, near_re, near_le,
                    sph_re, cyl_re, axis_re, add_re, bcva_re, sph_le, cyl_le, axis_le, add_le, bcva_le, iop_re, iop_le, iop_method,
                    colour_vision, notes, recorded_by)
                values (:visit, :t, :ucvaRe, :ucvaLe, :pgvaRe, :pgvaLe, :nearRe, :nearLe, :sphRe, :cylRe, :axisRe, :addRe, :bcvaRe,
                    :sphLe, :cylLe, :axisLe, :addLe, :bcvaLe, :iopRe, :iopLe, :iopMethod, :colour, :notes, :by)
                on conflict (visit_id) do update set ucva_re = excluded.ucva_re, ucva_le = excluded.ucva_le,
                    pgva_re = excluded.pgva_re, pgva_le = excluded.pgva_le, near_re = excluded.near_re, near_le = excluded.near_le,
                    sph_re = excluded.sph_re, cyl_re = excluded.cyl_re, axis_re = excluded.axis_re, add_re = excluded.add_re,
                    bcva_re = excluded.bcva_re, sph_le = excluded.sph_le, cyl_le = excluded.cyl_le, axis_le = excluded.axis_le,
                    add_le = excluded.add_le, bcva_le = excluded.bcva_le, iop_re = excluded.iop_re, iop_le = excluded.iop_le,
                    iop_method = excluded.iop_method, colour_vision = excluded.colour_vision, notes = excluded.notes,
                    recorded_by = excluded.recorded_by, recorded_at = now(), version = o.version + 1
                where o.version = cast(:v as int)
                """).params(p).update();
        changed(n);
        if (cmd.complete()) {
            jdbc.sql("update eyecare.visit set stage = 'CONSULTATION', optometry_done_at = now(), version = version + 1 where id = :id")
                    .param("id", visitId).update();
        }
        audit.record(AuditEntry.success(actor, cmd.complete() ? "eyecare.optometry.complete" : "eyecare.optometry.save", "visit",
                visitId, scope, Map.of("patient", v.patientUhid())));
        return chart(actor, visitId);
    }

    @Transactional
    public VisitChart saveConsultation(Actor actor, UUID visitId, EyeModels.SaveConsultation cmd) {
        VisitView v = visitRow(visitId);
        Scope scope = Scope.centre(v.centreId());
        access.require(actor, Permission.EYE_CONSULTATION_RECORD, scope, "eyecare.consultation.save", "visit", visitId.toString());
        if (!Set.of("OPTOMETRY", "CONSULTATION").contains(v.stage())) {
            throw new RuleViolationException("eyecare.visit.stage", "The consultation for this visit is already complete");
        }
        if (cmd.surgeryAdvised() && (blank(cmd.surgeryProcedure()) == null || blank(cmd.surgeryEye()) == null)) {
            throw new RuleViolationException("eyecare.consultation.surgery", "Name the procedure and the eye when advising surgery");
        }
        if (cmd.complete() && blank(cmd.diagnosis()) == null) {
            throw new RuleViolationException("eyecare.consultation.diagnosis", "Enter the diagnosis before completing the consultation");
        }
        Map<String, Object> p = new HashMap<>();
        p.put("visit", visitId);
        p.put("t", actor.tenantId());
        p.put("history", blank(cmd.history()));
        p.put("asRe", blank(cmd.anteriorSegmentRe()));
        p.put("asLe", blank(cmd.anteriorSegmentLe()));
        p.put("psRe", blank(cmd.posteriorSegmentRe()));
        p.put("psLe", blank(cmd.posteriorSegmentLe()));
        p.put("dx", blank(cmd.diagnosis()));
        p.put("dxCode", blank(cmd.diagnosisCode()));
        p.put("dxEye", blank(cmd.diagnosisEye()));
        p.put("meds", blank(cmd.medications()));
        p.put("glasses", cmd.glassesPrescribed());
        p.put("advice", blank(cmd.advice()));
        p.put("fu", cmd.followUpDays());
        p.put("surgery", cmd.surgeryAdvised());
        p.put("proc", cmd.surgeryAdvised() ? blank(cmd.surgeryProcedure()) : null);
        p.put("eye", cmd.surgeryAdvised() ? blank(cmd.surgeryEye()) : null);
        p.put("urgency", cmd.surgeryAdvised() ? (blank(cmd.surgeryUrgency()) == null ? "ROUTINE" : cmd.surgeryUrgency()) : null);
        p.put("doc", actor.personId());
        p.put("by", actor.userId());
        p.put("v", cmd.expectedVersion());
        int n = jdbc.sql("""
                insert into eyecare.consultation as c (visit_id, tenant_id, history, anterior_segment_re, anterior_segment_le,
                    posterior_segment_re, posterior_segment_le, diagnosis, diagnosis_code, diagnosis_eye, medications,
                    glasses_prescribed, advice, follow_up_days, surgery_advised, surgery_procedure, surgery_eye, surgery_urgency,
                    consultant_person_id, recorded_by)
                values (:visit, :t, :history, :asRe, :asLe, :psRe, :psLe, :dx, :dxCode, :dxEye, :meds, :glasses, :advice, :fu,
                    :surgery, :proc, :eye, :urgency, :doc, :by)
                on conflict (visit_id) do update set history = excluded.history,
                    anterior_segment_re = excluded.anterior_segment_re, anterior_segment_le = excluded.anterior_segment_le,
                    posterior_segment_re = excluded.posterior_segment_re, posterior_segment_le = excluded.posterior_segment_le,
                    diagnosis = excluded.diagnosis, diagnosis_code = excluded.diagnosis_code, diagnosis_eye = excluded.diagnosis_eye,
                    medications = excluded.medications, glasses_prescribed = excluded.glasses_prescribed, advice = excluded.advice,
                    follow_up_days = excluded.follow_up_days, surgery_advised = excluded.surgery_advised,
                    surgery_procedure = excluded.surgery_procedure, surgery_eye = excluded.surgery_eye,
                    surgery_urgency = excluded.surgery_urgency, consultant_person_id = excluded.consultant_person_id,
                    recorded_by = excluded.recorded_by, recorded_at = now(), version = c.version + 1
                where c.version = cast(:v as int)
                """).params(p).update();
        changed(n);
        if (cmd.complete()) {
            String next = cmd.surgeryAdvised() ? "COUNSELLING" : "CHECKOUT";
            jdbc.sql("""
                    update eyecare.visit set stage = :s, consultation_done_at = now(),
                        consultant_person_id = coalesce(consultant_person_id, :doc), version = version + 1 where id = :id
                    """).param("s", next).param("doc", actor.personId()).param("id", visitId).update();
            if (cmd.surgeryAdvised()) {
                surgeries.createFromAdvice(actor, v, blank(cmd.surgeryProcedure()), cmd.surgeryEye(), (String) p.get("urgency"));
            }
        }
        audit.record(AuditEntry.success(actor, cmd.complete() ? "eyecare.consultation.complete" : "eyecare.consultation.save",
                "visit", visitId, scope, Map.of("patient", v.patientUhid(), "surgeryAdvised", cmd.surgeryAdvised())));
        return chart(actor, visitId);
    }

    @Transactional
    public VisitView checkout(Actor actor, UUID visitId, EyeModels.Checkout cmd) {
        VisitView v = visitRow(visitId);
        Scope scope = Scope.centre(v.centreId());
        access.require(actor, Permission.OPD_CHECKIN, scope, "eyecare.visit.checkout", "visit", visitId.toString());
        if (!"CHECKOUT".equals(v.stage())) {
            throw new RuleViolationException("eyecare.visit.stage", "The patient is not at checkout yet (now: " + v.stage().toLowerCase() + ")");
        }
        List<BillingDirectory.BillSummary> bills = billing.billsForVisit(visitId).stream()
                .filter(b -> !"CANCELLED".equals(b.status())).toList();
        for (BillingDirectory.BillSummary b : bills) {
            if ("PENDING".equals(b.discountStatus())) {
                throw new RuleViolationException("eyecare.checkout.discount-pending", "Bill " + b.billNo() + " has a discount waiting for approval");
            }
            if (b.balance().compareTo(BigDecimal.ZERO) > 0) {
                throw new RuleViolationException("eyecare.checkout.balance", "Collect ₹" + b.balance() + " on bill " + b.billNo() + " first");
            }
        }
        String noCharge = blank(cmd.noChargeReason());
        if (bills.isEmpty() && noCharge == null) {
            throw new RuleViolationException("eyecare.checkout.no-bill", "Raise the bill, or give the reason this visit is free of charge");
        }
        int n = jdbc.sql("""
                update eyecare.visit set stage = 'COMPLETED', completed_at = now(), completed_by = :by, no_charge_reason = :r,
                    version = version + 1 where id = :id and version = :v
                """).param("by", actor.userId()).param("r", bills.isEmpty() ? noCharge : null).param("id", visitId)
                .param("v", cmd.expectedVersion()).update();
        changed(n);
        audit.record(AuditEntry.success(actor, "eyecare.visit.checkout", "visit", visitId, scope,
                Map.of("patient", v.patientUhid(), "bills", bills.size())));
        return visitRow(visitId);
    }

    @Transactional
    public VisitView cancelVisit(Actor actor, UUID visitId, EyeModels.CancelVisit cmd) {
        VisitView v = visitRow(visitId);
        Scope scope = Scope.centre(v.centreId());
        access.require(actor, Permission.OPD_CHECKIN, scope, "eyecare.visit.cancel", "visit", visitId.toString());
        if (Set.of("COMPLETED", "CANCELLED").contains(v.stage())) {
            throw new RuleViolationException("eyecare.visit.stage", "This visit is already closed");
        }
        boolean paid = billing.billsForVisit(visitId).stream().anyMatch(b -> b.paidAmount().signum() > 0);
        if (paid) {
            throw new RuleViolationException("eyecare.visit.has-payments", "Payments were taken for this visit; complete it at checkout instead");
        }
        int n = jdbc.sql("""
                update eyecare.visit set stage = 'CANCELLED', cancel_reason = :r, completed_at = now(), completed_by = :by,
                    version = version + 1 where id = :id and version = :v
                """).param("r", cmd.reason().trim()).param("by", actor.userId()).param("id", visitId).param("v", cmd.expectedVersion())
                .update();
        changed(n);
        audit.record(AuditEntry.success(actor, "eyecare.visit.cancel", "visit", visitId, scope,
                Map.of("patient", v.patientUhid(), "reason", cmd.reason())));
        return visitRow(visitId);
    }

    // ---------------------------------------------------------------- board and history

    @Transactional(readOnly = true)
    public ClinicBoard board(Actor actor, UUID centreId, LocalDate date) {
        requireAnyAt(actor, centreId, "eyecare.board.view", Permission.OPD_QUEUE_READ, Permission.EYE_CLINICAL_READ);
        LocalDate d = date == null ? BusinessTime.today() : date;
        Map<String, Integer> a = counts("select status as k, count(*)::int as n from eyecare.appointment where centre_id = :c and appointment_date = :d group by status", centreId, d);
        Map<String, Integer> v = counts("select stage as k, count(*)::int as n from eyecare.visit where centre_id = :c and visit_date = :d group by stage", centreId, d);
        var waits = jdbc.sql("""
                select avg(w)::int as avg_wait, max(w)::int as max_wait from (
                  select extract(epoch from (now() - greatest(checked_in_at, optometry_done_at, consultation_done_at,
                         counselling_done_at))) / 60 as w
                  from eyecare.visit where centre_id = :c and visit_date = :d and stage not in ('COMPLETED', 'CANCELLED')) x
                """).param("c", centreId).param("d", d)
                .query((rs, i) -> new Integer[] {(Integer) rs.getObject("avg_wait"), (Integer) rs.getObject("max_wait")}).single();
        int newPatients = jdbc.sql("""
                select count(*)::int from patient.patient where registered_centre_id = :c
                and (created_at at time zone 'Asia/Kolkata')::date = :d
                """).param("c", centreId).param("d", d).query(Integer.class).single();
        Map<String, Integer> s = counts("select status as k, count(*)::int as n from eyecare.surgery_case where centre_id = :c and status in ('ADVISED', 'ACCEPTED') group by status", centreId, d);
        Map<String, Integer> today = counts("select status as k, count(*)::int as n from eyecare.surgery_case where centre_id = :c and surgery_date = :d group by status", centreId, d);
        int upcoming = jdbc.sql("""
                select count(*)::int from eyecare.surgery_case where centre_id = :c and surgery_date > :d and status in ('SCHEDULED', 'READY')
                """).param("c", centreId).param("d", d).query(Integer.class).single();
        int visits = v.values().stream().mapToInt(Integer::intValue).sum();
        return new ClinicBoard(centreId, d, a.getOrDefault("BOOKED", 0), a.getOrDefault("CHECKED_IN", 0),
                a.getOrDefault("CANCELLED", 0) + a.getOrDefault("NO_SHOW", 0), visits, v.getOrDefault("OPTOMETRY", 0),
                v.getOrDefault("CONSULTATION", 0), v.getOrDefault("COUNSELLING", 0), v.getOrDefault("CHECKOUT", 0),
                v.getOrDefault("COMPLETED", 0), v.getOrDefault("CANCELLED", 0), waits[0], waits[1], newPatients,
                s.getOrDefault("ADVISED", 0), s.getOrDefault("ACCEPTED", 0),
                today.getOrDefault("SCHEDULED", 0) + today.getOrDefault("READY", 0) + today.getOrDefault("COMPLETED", 0),
                today.getOrDefault("READY", 0), today.getOrDefault("COMPLETED", 0), upcoming);
    }

    @Transactional(readOnly = true)
    public PatientEyeHistory history(Actor actor, UUID patientId) {
        patients.patient(patientId).orElseThrow(() -> new NotFoundException("Patient not found"));
        Set<UUID> centres = visibleCentres(actor);
        if (centres.isEmpty()) {
            access.requireAnywhere(actor, Permission.OPD_QUEUE_READ, "eyecare.history.view", "patient");
        }
        Set<UUID> clinicalCentres = access.centresWhere(actor, Permission.EYE_CLINICAL_READ);
        List<HistoryVisit> visits = jdbc.sql(EyeSql.VISIT + " where v.patient_id = :p order by v.visit_date desc, v.checked_in_at desc limit 100")
                .param("p", patientId).query(VisitView.class).list().stream()
                .filter(x -> centres.contains(x.centreId()))
                .map(x -> {
                    if (!clinicalCentres.contains(x.centreId())) {
                        return new HistoryVisit(x, null, null, null, null, null, null);
                    }
                    ConsultationView c = consultationRow(x.id());
                    OptometryView o = optometryRow(x.id());
                    return new HistoryVisit(x, c == null ? null : c.diagnosis(), c == null ? null : c.diagnosisCode(),
                            o == null ? null : o.bcvaRe(), o == null ? null : o.bcvaLe(), o == null ? null : o.iopRe(),
                            o == null ? null : o.iopLe());
                }).toList();
        List<SurgeryView> surgeryList = surgeries.forPatient(patientId).stream()
                .filter(x -> clinicalCentres.contains(x.centreId())).toList();
        List<AppointmentView> upcoming = jdbc.sql(EyeSql.APPOINTMENT
                        + " where a.patient_id = :p and a.status = 'BOOKED' and a.appointment_date >= :d order by a.appointment_date, a.slot_time")
                .param("p", patientId).param("d", BusinessTime.today()).query(AppointmentView.class).list().stream()
                .filter(x -> centres.contains(x.centreId())).toList();
        if (!clinicalCentres.isEmpty()) {
            audit.recordIndependently(AuditEntry.success(actor, "eyecare.history.view", "patient", patientId, null, Map.of()));
        }
        return new PatientEyeHistory(patientId, !clinicalCentres.isEmpty(), visits, surgeryList, upcoming);
    }

    // ---------------------------------------------------------------- helpers

    VisitView visitRow(UUID id) {
        return jdbc.sql(EyeSql.VISIT + " where v.id = :id").param("id", id).query(VisitView.class).optional()
                .orElseThrow(() -> new NotFoundException("Visit not found"));
    }

    private AppointmentView appointmentRow(UUID id) {
        return jdbc.sql(EyeSql.APPOINTMENT + " where a.id = :id").param("id", id).query(AppointmentView.class).optional()
                .orElseThrow(() -> new NotFoundException("Appointment not found"));
    }

    private OptometryView optometryRow(UUID visitId) {
        return jdbc.sql(EyeSql.OPTOMETRY + " where visit_id = :v").param("v", visitId).query(OptometryView.class).optional().orElse(null);
    }

    private ConsultationView consultationRow(UUID visitId) {
        return jdbc.sql(EyeSql.CONSULTATION + " where visit_id = :v").param("v", visitId).query(ConsultationView.class).optional().orElse(null);
    }

    private Map<String, Integer> counts(String sql, UUID centreId, LocalDate d) {
        Map<String, Integer> m = new HashMap<>();
        jdbc.sql(sql).param("c", centreId).param("d", d).query((rs, i) -> m.put(rs.getString("k"), rs.getInt("n"))).list();
        return m;
    }

    private Set<UUID> visibleCentres(Actor actor) {
        Set<UUID> s = new java.util.HashSet<>(access.centresWhere(actor, Permission.OPD_QUEUE_READ));
        s.addAll(access.centresWhere(actor, Permission.EYE_CLINICAL_READ));
        return s;
    }

    private void requireAnyAt(Actor actor, UUID centreId, String action, Permission... permissions) {
        Scope scope = Scope.centre(centreId);
        for (Permission p : permissions) {
            if (access.holds(actor, p, scope)) {
                return;
            }
        }
        access.require(actor, permissions[0], scope, action, "centre", centreId.toString());
    }

    private PatientDirectory.PatientRef activePatient(UUID patientId) {
        var patient = patients.patient(patientId).orElseThrow(() -> new NotFoundException("Patient not found"));
        if (!"ACTIVE".equals(patient.status())) {
            throw new RuleViolationException("patient.inactive", "The patient record is inactive");
        }
        return patient;
    }

    private void checkClinician(UUID centreId, UUID personId) {
        if (personId != null && workforce.staffAt(centreId, null).stream().noneMatch(s -> s.personId().equals(personId))) {
            throw new RuleViolationException("eyecare.clinician.not-posted", "The chosen doctor does not work at this centre");
        }
    }

    private Scope activeCentre(UUID centreId) {
        var c = org.centre(centreId).orElseThrow(() -> new NotFoundException("Centre not found"));
        if (!"ACTIVE".equals(c.status())) {
            throw new RuleViolationException("org.centre.retired", "The centre is retired");
        }
        return Scope.centre(centreId);
    }

    static void changed(int rows) {
        if (rows == 0) {
            throw new ConflictException("eyecare.changed", "Someone else changed this record. Reload and try again.");
        }
    }
}
