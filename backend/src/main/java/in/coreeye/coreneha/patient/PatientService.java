package in.coreeye.coreneha.patient;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import in.coreeye.coreneha.audit.AuditEntry;
import in.coreeye.coreneha.audit.AuditLog;
import in.coreeye.coreneha.kernel.AccessControl;
import in.coreeye.coreneha.kernel.Actor;
import in.coreeye.coreneha.kernel.BusinessTime;
import in.coreeye.coreneha.kernel.ConflictException;
import in.coreeye.coreneha.kernel.NotFoundException;
import in.coreeye.coreneha.kernel.Permission;
import in.coreeye.coreneha.kernel.RuleViolationException;
import in.coreeye.coreneha.kernel.Scope;
import in.coreeye.coreneha.org.OrgDirectory;
import in.coreeye.coreneha.patient.PatientModels.PatientView;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Patient registration and search. A patient has one UHID for the whole group:
 * registration happens at a centre (patient.register there), and staff holding
 * patient.read at any centre can find the patient wherever they were registered.
 * Opening a patient record is audited.
 */
@Service
public class PatientService implements PatientDirectory {

    private static final String SELECT = """
            select p.id, p.uhid, p.given_name, p.family_name, trim(p.given_name || ' ' || p.family_name) as full_name, p.sex,
                   p.birth_date, p.birth_date_estimated,
                   case when p.birth_date is null then null else extract(year from age(current_date, p.birth_date))::int end as age_years,
                   p.mobile, p.email, p.address_line, p.city, p.state, p.pincode, p.abha_number, p.guardian_name,
                   p.emergency_contact_name, p.emergency_contact_phone, p.registered_centre_id, c.name as registered_centre_name,
                   p.is_demo as demo, p.status, p.created_at, p.version
            from patient.patient p join org.centre c on c.id = p.registered_centre_id
            """;

    private final JdbcClient jdbc;
    private final AccessControl access;
    private final AuditLog audit;
    private final OrgDirectory org;

    PatientService(JdbcClient jdbc, AccessControl access, AuditLog audit, OrgDirectory org) {
        this.jdbc = jdbc;
        this.access = access;
        this.audit = audit;
        this.org = org;
    }

    // ---------------------------------------------------------------- directory

    @Override
    public Optional<PatientRef> patient(UUID patientId) {
        return jdbc.sql("""
                select id, tenant_id, uhid, trim(given_name || ' ' || family_name) as full_name, sex,
                       case when birth_date is null then null else extract(year from age(current_date, birth_date))::int end as age_years,
                       mobile, status
                from patient.patient where id = :id
                """).param("id", patientId).query(PatientRef.class).optional();
    }

    // ---------------------------------------------------------------- commands

    @Transactional
    public PatientView register(Actor actor, PatientModels.RegisterPatient cmd) {
        var centre = org.centre(cmd.centreId()).orElseThrow(() -> new NotFoundException("Centre not found"));
        Scope scope = Scope.centre(centre.id());
        access.require(actor, Permission.PATIENT_REGISTER, scope, "patient.register", "patient", null);
        if (!"ACTIVE".equals(centre.status())) {
            throw new RuleViolationException("org.centre.retired", "The centre is retired");
        }
        LocalDate birthDate = birthDate(cmd.birthDate(), cmd.ageYears());
        if (!cmd.confirmNotDuplicate()) {
            List<String> matches = possibleDuplicates(cmd.givenName(), cmd.familyName(), cmd.mobile(), birthDate, null);
            if (!matches.isEmpty()) {
                throw new ConflictException("patient.possible-duplicate",
                        "This may be an existing patient: " + String.join(", ", matches)
                                + ". Open the existing record, or confirm this is a different person.");
            }
        }
        var next = jdbc.sql("""
                insert into patient.uhid_counter (tenant_id, last_value) values (:t, 1)
                on conflict (tenant_id) do update set last_value = patient.uhid_counter.last_value + 1
                returning prefix, last_value
                """).param("t", actor.tenantId())
                .query((rs, i) -> rs.getString("prefix") + String.format("%08d", rs.getLong("last_value"))).single();
        boolean demo = jdbc.sql("select is_demo from org.tenant where id = :t").param("t", actor.tenantId())
                .query(Boolean.class).single();
        Map<String, Object> p = fields(cmd.givenName(), cmd.familyName(), cmd.sex(), birthDate,
                cmd.birthDate() == null && cmd.ageYears() != null, cmd.mobile(), cmd.email(), cmd.addressLine(), cmd.city(),
                cmd.state(), cmd.pincode(), cmd.abhaNumber(), cmd.guardianName(), cmd.emergencyContactName(),
                cmd.emergencyContactPhone());
        p.put("t", actor.tenantId());
        p.put("uhid", next);
        p.put("centre", centre.id());
        p.put("demo", demo);
        p.put("by", actor.userId());
        UUID id = jdbc.sql("""
                insert into patient.patient (tenant_id, uhid, given_name, family_name, sex, birth_date, birth_date_estimated,
                    mobile, email, address_line, city, state, pincode, abha_number, guardian_name, emergency_contact_name,
                    emergency_contact_phone, registered_centre_id, is_demo, created_by)
                values (:t, :uhid, :given, :family, :sex, :birth, :estimated, :mobile, :email, :address, :city, :state,
                    :pincode, :abha, :guardian, :ecName, :ecPhone, :centre, :demo, :by)
                returning id
                """).params(p).query(UUID.class).single();
        audit.record(AuditEntry.success(actor, "patient.register", "patient", id, scope, Map.of("uhid", next)));
        return row(id);
    }

    @Transactional
    public PatientView update(Actor actor, UUID patientId, PatientModels.UpdatePatient cmd) {
        PatientView current = row(patientId);
        Scope scope = Scope.centre(current.registeredCentreId());
        // One identity for the group: registration staff at any centre may correct demographics.
        access.requireAnywhere(actor, Permission.PATIENT_REGISTER, "patient.update", "patient");
        LocalDate birthDate = birthDate(cmd.birthDate(), cmd.ageYears());
        Map<String, Object> p = fields(cmd.givenName(), cmd.familyName(), cmd.sex(), birthDate,
                cmd.birthDate() == null && cmd.ageYears() != null, cmd.mobile(), cmd.email(), cmd.addressLine(), cmd.city(),
                cmd.state(), cmd.pincode(), cmd.abhaNumber(), cmd.guardianName(), cmd.emergencyContactName(),
                cmd.emergencyContactPhone());
        p.put("id", patientId);
        p.put("v", cmd.expectedVersion());
        int n = jdbc.sql("""
                update patient.patient set given_name = :given, family_name = :family, sex = :sex, birth_date = :birth,
                    birth_date_estimated = :estimated, mobile = :mobile, email = :email, address_line = :address, city = :city,
                    state = :state, pincode = :pincode, abha_number = :abha, guardian_name = :guardian,
                    emergency_contact_name = :ecName, emergency_contact_phone = :ecPhone, version = version + 1
                where id = :id and version = :v
                """).params(p).update();
        if (n == 0) {
            throw new ConflictException("patient.changed", "Someone else changed this patient. Reload and try again.");
        }
        Map<String, Object> details = new HashMap<>();
        details.put("uhid", current.uhid());
        details.put("before", Map.of("name", current.fullName(), "mobile", String.valueOf(current.mobile())));
        audit.record(AuditEntry.success(actor, "patient.update", "patient", patientId, scope, details));
        return row(patientId);
    }

    // ---------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public List<PatientView> search(Actor actor, String query, Integer limit) {
        access.requireAnywhere(actor, Permission.PATIENT_READ, "patient.search", "patient");
        int max = limit == null ? 50 : Math.max(1, Math.min(limit, 200));
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            return jdbc.sql(SELECT + " order by p.created_at desc limit :max").param("max", max).query(PatientView.class).list();
        }
        String digits = q.replaceAll("[^0-9]", "");
        return jdbc.sql(SELECT + """
                 where p.uhid = upper(:q)
                    or lower(p.given_name || ' ' || p.family_name) like :name
                    or lower(p.family_name) like :name
                    or (length(:digits) >= 4 and regexp_replace(coalesce(p.mobile, ''), '[^0-9]', '', 'g') like :mobile)
                    or (length(:digits) >= 4 and p.uhid like :uhidSuffix)
                 order by (p.uhid = upper(:q)) desc, p.given_name, p.family_name limit :max
                """).param("q", q).param("name", q.toLowerCase() + "%").param("digits", digits)
                .param("mobile", "%" + digits + "%").param("uhidSuffix", "%" + digits).param("max", max)
                .query(PatientView.class).list();
    }

    @Transactional(readOnly = true)
    public PatientView view(Actor actor, UUID patientId) {
        access.requireAnywhere(actor, Permission.PATIENT_READ, "patient.view", "patient");
        PatientView p = row(patientId);
        audit.recordIndependently(AuditEntry.success(actor, "patient.view", "patient", patientId, null, Map.of("uhid", p.uhid())));
        return p;
    }

    private PatientView row(UUID id) {
        return jdbc.sql(SELECT + " where p.id = :id").param("id", id).query(PatientView.class).optional()
                .orElseThrow(() -> new NotFoundException("Patient not found"));
    }

    private List<String> possibleDuplicates(String given, String family, String mobile, LocalDate birthDate, UUID except) {
        String digits = mobile == null ? "" : mobile.replaceAll("[^0-9]", "");
        return jdbc.sql("""
                select uhid || ' ' || trim(given_name || ' ' || family_name) from patient.patient
                where (cast(:except as uuid) is null or id <> cast(:except as uuid)) and (
                    (length(:digits) >= 6 and regexp_replace(coalesce(mobile, ''), '[^0-9]', '', 'g') = :digits
                        and lower(given_name) = lower(:given))
                    or (cast(:birth as date) is not null and birth_date = cast(:birth as date) and lower(given_name) = lower(:given)
                        and lower(family_name) = lower(:family)))
                limit 5
                """).param("except", except).param("digits", digits).param("given", given.trim())
                .param("family", family == null ? "" : family.trim()).param("birth", birthDate)
                .query(String.class).list();
    }

    private static LocalDate birthDate(LocalDate birthDate, Integer ageYears) {
        if (birthDate != null) {
            if (birthDate.isAfter(BusinessTime.today())) {
                throw new RuleViolationException("patient.birth-date-future", "Date of birth cannot be in the future");
            }
            return birthDate;
        }
        return ageYears == null ? null : BusinessTime.today().minusYears(ageYears);
    }

    private static Map<String, Object> fields(String given, String family, String sex, LocalDate birth, boolean estimated,
                                              String mobile, String email, String address, String city, String state,
                                              String pincode, String abha, String guardian, String ecName, String ecPhone) {
        Map<String, Object> p = new HashMap<>();
        p.put("given", given.trim());
        p.put("family", family == null ? "" : family.trim());
        p.put("sex", sex);
        p.put("birth", birth);
        p.put("estimated", estimated);
        p.put("mobile", blank(mobile));
        p.put("email", blank(email));
        p.put("address", blank(address));
        p.put("city", blank(city));
        p.put("state", blank(state));
        p.put("pincode", blank(pincode));
        p.put("abha", blank(abha));
        p.put("guardian", blank(guardian));
        p.put("ecName", blank(ecName));
        p.put("ecPhone", blank(ecPhone));
        return p;
    }

    static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
