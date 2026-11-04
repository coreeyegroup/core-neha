package in.coreeye.coreneha.operations;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import in.coreeye.coreneha.iam.AssignmentService;
import in.coreeye.coreneha.iam.IamModels;
import in.coreeye.coreneha.iam.RoleService;
import in.coreeye.coreneha.iam.UserService;
import in.coreeye.coreneha.kernel.Actor;
import in.coreeye.coreneha.kernel.NotFoundException;
import in.coreeye.coreneha.kernel.TenantContext;
import in.coreeye.coreneha.org.OrgDirectory;
import in.coreeye.coreneha.org.OrgModels;
import in.coreeye.coreneha.org.OrgService;
import in.coreeye.coreneha.workforce.WorkforceModels;
import in.coreeye.coreneha.workforce.WorkforceService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * The full demonstration staff of Vijaya Eye Clinic: every position an eye hospital of
 * this size runs in its six departments, a role for each that fits the work, and two
 * synthetic people per position, each with a login. Safe to run again: positions,
 * roles and people that already exist are kept, and only what is missing is added.
 * Administration is done by the two bootstrap administrators (one requests, the other
 * approves), through the same services and rules as the web application.
 */
@Component
class ClinicStaff {

    record Summary(int positions, int added, List<CredentialsFile.Entry> logins) {
    }

    /** Position code, title, category, department code, role code, then two people (given, family). */
    static final String[][] POSITIONS = {
            // Reception and front office
            {"RECEPTIONIST", "Receptionist", "ADMINISTRATIVE", "RECEPTION", "EYE_FRONT_OFFICE", "Priya", "Sharma", "Divya", "Hegde"},
            {"FRONT_OFFICE_SUPERVISOR", "Front office supervisor", "ADMINISTRATIVE", "RECEPTION", "EYE_FRONT_OFFICE_SUPERVISOR", "Ramesh", "Kumar", "Asha", "Kamath"},
            {"REGISTRATION_EXECUTIVE", "Patient registration executive", "ADMINISTRATIVE", "RECEPTION", "EYE_FRONT_OFFICE", "Manjunath", "Gowda", "Rashmi", "Bhat"},
            {"APPOINTMENT_EXECUTIVE", "Appointment and call-centre executive", "ADMINISTRATIVE", "RECEPTION", "EYE_APPOINTMENTS", "Sneha", "Kulkarni", "Abdul", "Rahman"},
            {"PATIENT_RELATIONS_EXECUTIVE", "Patient relations executive", "ADMINISTRATIVE", "RECEPTION", "EYE_PATIENT_RELATIONS", "Nandini", "Rao", "Joseph", "Pinto"},
            {"CENTRE_OPERATIONS_MANAGER", "Centre operations manager", "MANAGEMENT", "RECEPTION", "EYE_CENTRE_MANAGER", "Vinay", "Deshpande", "Sujatha", "Murthy"},
            // Optometry
            {"OPTOMETRIST", "Optometrist", "ALLIED_HEALTH", "OPTOMETRY", "EYE_OPTOMETRY", "Sunita", "Rao", "Harsha", "Naik"},
            {"SENIOR_OPTOMETRIST", "Senior optometrist", "ALLIED_HEALTH", "OPTOMETRY", "EYE_OPTOMETRY", "Karthik", "Iyer", "Pallavi", "Joshi"},
            {"CHIEF_OPTOMETRIST", "Chief optometrist", "ALLIED_HEALTH", "OPTOMETRY", "EYE_OPTOMETRY", "Srinivas", "Murthy", "Geetha", "Krishnan"},
            {"OPHTHALMIC_ASSISTANT", "Ophthalmic assistant (vision and eye pressure)", "ALLIED_HEALTH", "OPTOMETRY", "EYE_OPTOMETRY", "Kiran", "Shetty", "Roopa", "Gowda"},
            {"IMAGING_TECHNICIAN", "Ophthalmic imaging technician (OCT, fundus, fields)", "TECHNICAL", "OPTOMETRY", "EYE_OPTOMETRY", "Prakash", "Hiremath", "Swathi", "Nayak"},
            {"DISPENSING_OPTICIAN", "Dispensing optician", "ALLIED_HEALTH", "OPTOMETRY", "EYE_OPTICAL", "Mahesh", "Pai", "Anitha", "Reddy"},
            // Consultation (ophthalmology)
            {"MEDICAL_DIRECTOR", "Medical director", "CLINICAL", "OPHTHALMOLOGY", "EYE_MEDICAL_DIRECTOR", "Ravindra", "Kamath", "Usha", "Raman"},
            {"CONSULTANT_OPHTHALMOLOGIST", "Consultant ophthalmologist", "CLINICAL", "OPHTHALMOLOGY", "EYE_CONSULTANT", "Vijay", "Kumar", "Shalini", "Menon"},
            {"ASSOCIATE_OPHTHALMOLOGIST", "Associate ophthalmologist", "CLINICAL", "OPHTHALMOLOGY", "EYE_CONSULTANT", "Meera", "Nair", "Rahul", "Bhandari"},
            {"RETINA_CONSULTANT", "Vitreo-retina consultant", "CLINICAL", "OPHTHALMOLOGY", "EYE_CONSULTANT", "Anand", "Kulkarni", "Padma", "Srinivasan"},
            {"GLAUCOMA_CONSULTANT", "Glaucoma consultant", "CLINICAL", "OPHTHALMOLOGY", "EYE_CONSULTANT", "Sanjay", "Hegde", "Lakshmi", "Prasad"},
            {"CORNEA_CONSULTANT", "Cornea and refractive consultant", "CLINICAL", "OPHTHALMOLOGY", "EYE_CONSULTANT", "Nikhil", "Rao", "Deepa", "Shenoy"},
            {"PAEDIATRIC_OPHTHALMOLOGIST", "Paediatric ophthalmologist and squint specialist", "CLINICAL", "OPHTHALMOLOGY", "EYE_CONSULTANT", "Aparna", "Iyer", "Vivek", "Menon"},
            {"RESIDENT_MEDICAL_OFFICER", "Resident medical officer (duty doctor)", "CLINICAL", "OPHTHALMOLOGY", "EYE_CONSULTANT", "Arun", "Prakash", "Sahana", "Gowda"},
            {"OPD_NURSE", "OPD nurse", "NURSING", "OPHTHALMOLOGY", "EYE_OPD_NURSE", "Jessy", "Thomas", "Bindu", "Varghese"},
            // Surgery counselling
            {"SURGERY_COUNSELLOR", "Surgery counsellor", "ALLIED_HEALTH", "COUNSELLING", "EYE_COUNSELLING", "Lata", "Menon", "Vandana", "Joshi"},
            {"PATIENT_CARE_COORDINATOR", "Patient care coordinator", "ADMINISTRATIVE", "COUNSELLING", "EYE_COUNSELLING", "Naveen", "Reddy", "Shruti", "Patil"},
            {"SENIOR_COUNSELLOR", "Senior counsellor (head of counselling)", "ALLIED_HEALTH", "COUNSELLING", "EYE_COUNSELLING", "Rekha", "Subramanian", "Girish", "Kamath"},
            {"SURGERY_SCHEDULER", "Surgery scheduler", "ADMINISTRATIVE", "COUNSELLING", "EYE_COUNSELLING", "Ajay", "Nayak", "Meghana", "Rao"},
            {"MEDICLAIM_COORDINATOR", "Insurance (Mediclaim) coordinator", "ADMINISTRATIVE", "COUNSELLING", "EYE_MEDICLAIM", "Sunil", "Hegde", "Kavya", "Shetty"},
            // Operation theatre (surgery and day-care)
            {"OPHTHALMIC_SURGEON", "Ophthalmic surgeon", "CLINICAL", "SURGERY_DAYCARE", "EYE_SURGEON", "Arjun", "Rao", "Prashanth", "Shetty"},
            {"ANAESTHESIOLOGIST", "Anaesthesiologist", "CLINICAL", "SURGERY_DAYCARE", "EYE_ANAESTHESIA", "Ramya", "Krishnan", "Mohan", "Das"},
            {"OT_IN_CHARGE", "OT nursing in-charge", "NURSING", "SURGERY_DAYCARE", "EYE_OT_NURSE", "Elizabeth", "George", "Sarala", "Devi"},
            {"OT_NURSE", "OT nurse", "NURSING", "SURGERY_DAYCARE", "EYE_OT_NURSE", "Mary", "Joseph", "Reena", "Mathew"},
            {"SCRUB_NURSE", "Scrub nurse", "NURSING", "SURGERY_DAYCARE", "EYE_OT_NURSE", "Lincy", "Paul", "Ashwini", "Kumar"},
            {"OT_TECHNICIAN", "OT technician", "TECHNICAL", "SURGERY_DAYCARE", "EYE_OT_NURSE", "Santosh", "Patil", "Ravi", "Kiran"},
            {"DAYCARE_NURSE", "Day-care ward nurse (pre-operative and recovery)", "NURSING", "SURGERY_DAYCARE", "EYE_OT_NURSE", "Shilpa", "Rao", "Annamma", "Varghese"},
            {"CSSD_TECHNICIAN", "CSSD technician (sterilisation)", "SUPPORT", "SURGERY_DAYCARE", "EYE_OT_SUPPORT", "Basavaraj", "Hosamani", "Lokesh", "Gowda"},
            // Accounts
            {"ACCOUNTANT", "Accountant", "ADMINISTRATIVE", "BILLING", "EYE_ACCOUNTANT", "Ganesh", "Patil", "Sowmya", "Rao"},
            {"ACCOUNTS_MANAGER", "Accounts manager", "MANAGEMENT", "BILLING", "EYE_ACCOUNTS_MANAGER", "Shobha", "Kulkarni", "Raghavendra", "Bhat"},
            {"BILLING_EXECUTIVE", "Billing executive", "ADMINISTRATIVE", "BILLING", "EYE_BILLING", "Chaitra", "Naik", "Imran", "Khan"},
            {"CASHIER", "Cashier", "ADMINISTRATIVE", "BILLING", "EYE_CASHIER", "Yashodha", "Gowda", "Feroz", "Ahmed"},
            {"ACCOUNTS_ASSISTANT", "Accounts assistant", "ADMINISTRATIVE", "BILLING", "EYE_ACCOUNTANT", "Pavan", "Kumar", "Nisha", "Shenoy"},
            {"INTERNAL_AUDITOR", "Internal auditor", "MANAGEMENT", "BILLING", "EYE_AUDITOR", "Subramanya", "Bhat", "Latha", "Ramesh"},
    };

    /** Roles added for the full staff (the clinic seed already has the core ones). Code, name, then permissions. */
    static final Map<String, String[]> ROLES = new LinkedHashMap<>();

    static {
        ROLES.put("EYE_APPOINTMENTS", new String[] {"Eye clinic appointments and call centre",
                "org.centre.read", "patient.read", "patient.register", "opd.queue.read", "opd.appointment.manage"});
        ROLES.put("EYE_PATIENT_RELATIONS", new String[] {"Eye clinic patient relations",
                "org.centre.read", "patient.read", "opd.queue.read", "opd.appointment.manage"});
        ROLES.put("EYE_CENTRE_MANAGER", new String[] {"Eye clinic centre operations manager",
                "org.centre.read", "patient.read", "opd.queue.read", "opd.appointment.manage", "billing.read", "billing.report.read", "audit.read"});
        ROLES.put("EYE_OPTICAL", new String[] {"Eye clinic optical dispensing",
                "org.centre.read", "patient.read", "opd.queue.read", "eye.clinical.read", "billing.read", "billing.charge"});
        ROLES.put("EYE_MEDICAL_DIRECTOR", new String[] {"Eye clinic medical director",
                "org.centre.read", "patient.read", "opd.queue.read", "eye.clinical.read", "eye.optometry.record", "eye.consultation.record",
                "eye.surgery.schedule", "billing.report.read", "audit.read"});
        ROLES.put("EYE_OPD_NURSE", new String[] {"Eye clinic OPD nurse",
                "org.centre.read", "patient.read", "opd.queue.read", "eye.clinical.read", "eye.optometry.record"});
        ROLES.put("EYE_MEDICLAIM", new String[] {"Eye clinic insurance (Mediclaim) desk",
                "org.centre.read", "patient.read", "opd.queue.read", "eye.clinical.read", "billing.read", "billing.charge"});
        ROLES.put("EYE_ANAESTHESIA", new String[] {"Eye clinic anaesthesia",
                "org.centre.read", "patient.read", "opd.queue.read", "eye.clinical.read", "eye.surgery.prepare"});
        ROLES.put("EYE_OT_SUPPORT", new String[] {"Eye clinic OT support (no patient records)",
                "org.centre.read"});
        ROLES.put("EYE_BILLING", new String[] {"Eye clinic billing",
                "org.centre.read", "patient.read", "opd.queue.read", "billing.read", "billing.charge", "billing.collect"});
        ROLES.put("EYE_CASHIER", new String[] {"Eye clinic cashier",
                "org.centre.read", "patient.read", "billing.read", "billing.collect"});
        ROLES.put("EYE_AUDITOR", new String[] {"Eye clinic internal audit (read only)",
                "org.centre.read", "billing.read", "billing.report.read", "audit.read"});
    }

    static String email(String given, String family) {
        return (given + "." + family + "@vijaya-eye.demo.core-neha.invalid").toLowerCase(Locale.ROOT);
    }

    private final JdbcClient jdbc;
    private final OrgDirectory directory;
    private final OrgService org;
    private final WorkforceService workforce;
    private final RoleService roles;
    private final AssignmentService assignments;
    private final UserService users;

    ClinicStaff(JdbcClient jdbc, OrgDirectory directory, OrgService org, WorkforceService workforce, RoleService roles,
                AssignmentService assignments, UserService users) {
        this.jdbc = jdbc;
        this.directory = directory;
        this.org = org;
        this.workforce = workforce;
        this.roles = roles;
        this.assignments = assignments;
        this.users = users;
    }

    Summary extend(String tenantCode) {
        UUID tenant = directory.tenantIdByCode(tenantCode)
                .orElseThrow(() -> new NotFoundException("Tenant '" + tenantCode + "' not found; load the demonstration tenant first (ops/demo-seed.sh)"));
        return TenantContext.callAs(tenant, () -> build(tenant));
    }

    private Summary build(UUID tenant) {
        boolean demo = jdbc.sql("select is_demo from org.tenant where id = :t").param("t", tenant).query(Boolean.class).single();
        if (!demo) {
            throw new IllegalStateException("The clinic staff demonstration is loaded only into a demonstration tenant");
        }
        List<UUID> admins = jdbc.sql("""
                select user_id from iam.role_assignment where via_bootstrap and kind = 'ACCESS' order by requested_at, id
                """).query(UUID.class).list();
        if (admins.size() < 2) {
            throw new IllegalStateException("The tenant has no bootstrap administrators");
        }
        Actor a = new Actor(admins.get(0), tenant, null, "clinic-staff (as first administrator)", 0);
        Actor b = new Actor(admins.get(1), tenant, null, "clinic-staff (as second administrator)", 0);

        UUID centre = org.centres(a).stream().filter(c -> c.code().equals(ClinicSeed.CENTRE_CODE)).map(OrgModels.CentreView::id)
                .findFirst().orElseThrow(() -> new NotFoundException(ClinicSeed.CENTRE_NAME + " not found; run ops/clinic-seed.sh first"));
        Map<String, OrgModels.CentreDepartmentView> departments = new HashMap<>();
        org.centreDepartments(a, centre, false).forEach(d -> departments.put(d.departmentCode(), d));

        // Roles: drafted by the first administrator, approved by the second.
        Map<String, UUID> roleIds = new HashMap<>();
        roles.roles(a).forEach(r -> roleIds.put(r.code(), r.id()));
        for (Map.Entry<String, String[]> r : ROLES.entrySet()) {
            if (!roleIds.containsKey(r.getKey())) {
                String[] def = r.getValue();
                roleIds.put(r.getKey(), publishRole(a, b, r.getKey(), def[0], Set.of(Arrays.copyOfRange(def, 1, def.length))));
            }
        }

        Map<String, UUID> positionLibrary = new HashMap<>();
        workforce.positions(a).forEach(p -> positionLibrary.put(p.code(), p.id()));
        Map<String, WorkforceModels.PersonView> people = new HashMap<>();
        workforce.people(a).stream().filter(p -> p.email() != null).forEach(p -> people.put(p.email().toLowerCase(Locale.ROOT), p));

        List<CredentialsFile.Entry> logins = new ArrayList<>();
        for (String[] s : POSITIONS) {
            var department = departments.get(s[3]);
            if (department == null) {
                throw new IllegalStateException("Department " + s[3] + " is not enabled at " + ClinicSeed.CENTRE_NAME);
            }
            UUID roleId = roleIds.get(s[4]);
            if (roleId == null) {
                throw new IllegalStateException("Role " + s[4] + " is missing; run ops/clinic-seed.sh first");
            }
            UUID position = positionLibrary.computeIfAbsent(s[0],
                    k -> workforce.definePosition(a, new WorkforceModels.DefinePosition(s[0], s[1], s[2], null)).id());
            List<String[]> missing = new ArrayList<>();
            for (int i = 5; i + 1 < s.length; i += 2) {
                if (!people.containsKey(email(s[i], s[i + 1]))) {
                    missing.add(new String[] {s[i], s[i + 1]});
                }
            }
            if (missing.isEmpty()) {
                continue;
            }
            var establishment = workforce.establishments(a, centre).stream()
                    .filter(e -> e.positionId().equals(position) && department.id().equals(e.centreDepartmentId()) && e.validTo() == null)
                    .findFirst()
                    .orElseGet(() -> workforce.establish(a, centre, new WorkforceModels.Establish(position, department.id(), 2, null)));
            int needed = establishment.activePostings() + missing.size();
            if (establishment.approvedHeadcount() < needed) {
                workforce.changeHeadcount(a, establishment.id(), needed, "Two people per position for the clinic demonstration");
            }
            for (String[] person : missing) {
                String given = person[0];
                String family = person[1];
                UUID personId = workforce.createPerson(a, new WorkforceModels.CreatePerson(given, family, email(given, family)), true).id();
                UUID posting = workforce.addPosting(a, personId,
                        new WorkforceModels.AddPosting(establishment.id(), BigDecimal.ONE, null, null)).id();
                IamModels.UserCreated created = users.create(a, new IamModels.CreateUser(personId, null));
                IamModels.AssignmentView requested = assignments.request(a, new IamModels.RequestAssignment("ACCESS", created.user().id(),
                        roleId, "CENTRE", centre, posting, null, null, s[1] + ", " + ClinicSeed.CENTRE_NAME));
                assignments.approve(b, requested.id(), new IamModels.Decision("Approved for the clinic demonstration", requested.version()));
                logins.add(new CredentialsFile.Entry(created.user().username(), created.temporaryPassword(),
                        given + " " + family + ", " + s[1] + ", " + (department.localName() == null ? department.departmentName() : department.localName())
                                + ", " + ClinicSeed.CENTRE_NAME));
            }
        }
        return new Summary(POSITIONS.length, logins.size(), logins);
    }

    private UUID publishRole(Actor a, Actor b, String code, String name, Set<String> permissions) {
        IamModels.RoleDetail draft = roles.create(a, new IamModels.CreateRole(code, name, "Vijaya Eye Clinic demonstration role", permissions, "Initial version"));
        IamModels.RoleVersionView v = draft.versions().getFirst();
        IamModels.RoleDetail submitted = roles.submit(a, v.id(), new IamModels.Decision("Ready for review", v.version()));
        roles.approve(b, v.id(), new IamModels.Decision("Reviewed", submitted.versions().getFirst().version()));
        return draft.role().id();
    }
}
