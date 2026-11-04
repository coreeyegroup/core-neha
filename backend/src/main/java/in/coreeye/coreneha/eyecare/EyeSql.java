package in.coreeye.coreneha.eyecare;

/** Read-model SQL shared by the eye-care services. Names of staff and patients are joined for display only. */
final class EyeSql {

    private EyeSql() {
    }

    static String personName(String column) {
        return "(select trim(wp.given_name || ' ' || wp.family_name) from workforce.person wp where wp.id = " + column + ")";
    }

    static String userName(String column) {
        return "(select trim(wp.given_name || ' ' || wp.family_name) from iam.user_account ua "
                + "join workforce.person wp on wp.id = ua.person_id where ua.id = " + column + ")";
    }

    static final String AGE = "case when p.birth_date is null then null else extract(year from age(current_date, p.birth_date))::int end";

    static final String PATIENT_COLUMNS = "p.uhid as patient_uhid, trim(p.given_name || ' ' || p.family_name) as patient_name, "
            + "p.sex as patient_sex, " + AGE + " as patient_age, p.mobile as patient_mobile";

    static final String VISIT = """
            select v.id, v.centre_id, v.patient_id, %s, v.visit_date, v.token_no, v.visit_type, v.chief_complaint,
                   v.consultant_person_id, %s as consultant_name, v.stage, v.checked_in_at, v.optometry_done_at,
                   v.consultation_done_at, v.counselling_done_at, v.completed_at,
                   case when v.stage in ('COMPLETED', 'CANCELLED') then null
                        else (extract(epoch from (now() - greatest(v.checked_in_at, v.optometry_done_at, v.consultation_done_at,
                              v.counselling_done_at))) / 60)::int end as waiting_minutes,
                   coalesce((select cn.surgery_advised from eyecare.consultation cn where cn.visit_id = v.id), false) as surgery_advised,
                   v.no_charge_reason, v.cancel_reason, c.name as centre_name, v.version
            from eyecare.visit v join patient.patient p on p.id = v.patient_id join org.centre c on c.id = v.centre_id
            """.formatted(PATIENT_COLUMNS, personName("v.consultant_person_id"));

    static final String APPOINTMENT = """
            select a.id, a.centre_id, a.patient_id, %s, a.appointment_date, a.slot_time, a.visit_type, a.source,
                   a.consultant_person_id, %s as consultant_name, a.reason, a.status, a.cancel_reason, a.visit_id,
                   %s as created_by_name, a.created_at, a.version
            from eyecare.appointment a join patient.patient p on p.id = a.patient_id
            """.formatted(PATIENT_COLUMNS, personName("a.consultant_person_id"), userName("a.created_by"));

    static final String OPTOMETRY = """
            select visit_id, ucva_re, ucva_le, pgva_re, pgva_le, near_re, near_le, sph_re, cyl_re, axis_re, add_re, bcva_re,
                   sph_le, cyl_le, axis_le, add_le, bcva_le, iop_re, iop_le, iop_method, colour_vision, notes,
                   %s as recorded_by_name, recorded_at, version
            from eyecare.optometry_record
            """.formatted(userName("recorded_by"));

    static final String CONSULTATION = """
            select visit_id, history, anterior_segment_re, anterior_segment_le, posterior_segment_re, posterior_segment_le,
                   diagnosis, diagnosis_code, diagnosis_eye, medications, glasses_prescribed, advice, follow_up_days,
                   surgery_advised, surgery_procedure, surgery_eye, surgery_urgency, consultant_person_id,
                   %s as consultant_name, %s as recorded_by_name, recorded_at, version
            from eyecare.consultation
            """.formatted(personName("consultant_person_id"), userName("recorded_by"));

    static final String SURGERY = """
            select s.id, s.centre_id, s.patient_id, %s, s.visit_id, s.procedure_name, s.eye, s.urgency, s.status,
                   %s as advised_by_name,
                   (select cn.diagnosis from eyecare.consultation cn where cn.visit_id = s.visit_id) as diagnosis,
                   s.package_item_id, s.package_name, s.estimated_cost, s.payment_plan, s.insurer_name, s.counselling_notes,
                   s.preferred_date, %s as counselled_by_name, s.counselled_at, s.surgery_date, s.slot_time, s.ot_room,
                   s.surgeon_person_id, %s as surgeon_name, s.consent_signed, s.site_marked, s.fitness_cleared,
                   s.biometry_done, s.iol_power, s.pupil_dilated, s.pre_op_bp, s.pre_op_blood_sugar, s.pre_op_notes,
                   %s as prepared_by_name, s.prepared_at, s.anaesthesia, s.iol_implanted, s.operative_notes, s.complications,
                   s.post_op_instructions, %s as operated_by_name, s.started_at, s.completed_at, s.cancel_reason,
                   s.created_at, s.version
            from eyecare.surgery_case s join patient.patient p on p.id = s.patient_id
            """.formatted(PATIENT_COLUMNS, personName("s.advised_by_person_id"), userName("s.counselled_by"),
            personName("s.surgeon_person_id"), userName("s.prepared_by"), personName("s.operated_by_person_id"));

    static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
