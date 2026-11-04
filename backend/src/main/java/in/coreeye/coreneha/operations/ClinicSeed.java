package in.coreeye.coreneha.operations;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import in.coreeye.coreneha.billing.BillingModels;
import in.coreeye.coreneha.billing.BillingService;
import in.coreeye.coreneha.eyecare.EyeModels;
import in.coreeye.coreneha.eyecare.SurgeryService;
import in.coreeye.coreneha.eyecare.VisitService;
import in.coreeye.coreneha.iam.AssignmentService;
import in.coreeye.coreneha.iam.IamModels;
import in.coreeye.coreneha.iam.RoleService;
import in.coreeye.coreneha.iam.UserService;
import in.coreeye.coreneha.kernel.Actor;
import in.coreeye.coreneha.kernel.BusinessTime;
import in.coreeye.coreneha.kernel.ConflictException;
import in.coreeye.coreneha.kernel.NotFoundException;
import in.coreeye.coreneha.kernel.TenantContext;
import in.coreeye.coreneha.org.OrgDirectory;
import in.coreeye.coreneha.org.OrgModels;
import in.coreeye.coreneha.org.OrgService;
import in.coreeye.coreneha.patient.PatientModels;
import in.coreeye.coreneha.patient.PatientService;
import in.coreeye.coreneha.workforce.WorkforceModels;
import in.coreeye.coreneha.workforce.WorkforceService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Synthetic eye clinic for demonstration (ADR-0011): Vijaya Eye Clinic with six
 * departments, two positions in each, one login per position with a role that
 * fits the department, a price list, and a working day of synthetic patients at
 * every stage. Administration (centre, positions, roles, access) is done by the two
 * bootstrap administrators with independent approval; clinical and billing work is
 * done by the clinic's own staff, through the same services and rules as the web
 * application.
 */
@Component
class ClinicSeed {

    record Summary(UUID centreId, int departments, int positions, int patients, List<CredentialsFile.Entry> logins) {
    }

    static final String CENTRE_CODE = "VEC";
    static final String CENTRE_NAME = "Vijaya Eye Clinic";

    /** Department library code, library name, category, local name at the clinic. */
    static final String[][] DEPARTMENTS = {
            {"RECEPTION", "Reception and front office", "ADMINISTRATIVE", "Reception"},
            {"OPTOMETRY", "Optometry", "CLINICAL", "Optometry"},
            {"OPHTHALMOLOGY", "Ophthalmology", "CLINICAL", "Consultation (eye specialists)"},
            {"COUNSELLING", "Counselling", "CLINICAL", "Surgery counselling"},
            {"SURGERY_DAYCARE", "Surgery and day-care", "CLINICAL", "Operation theatre (OT)"},
            {"BILLING", "Billing and accounts", "ADMINISTRATIVE", "Accounts"},
    };

    /** Position code, title, category, department, role code, person given, family, username. */
    static final String[][] STAFF = {
            {"RECEPTIONIST", "Receptionist", "ADMINISTRATIVE", "RECEPTION", "EYE_FRONT_OFFICE", "Priya", "Sharma", "priya.reception"},
            {"FRONT_OFFICE_SUPERVISOR", "Front office supervisor", "ADMINISTRATIVE", "RECEPTION", "EYE_FRONT_OFFICE_SUPERVISOR", "Ramesh", "Kumar", "ramesh.frontoffice"},
            {"OPTOMETRIST", "Optometrist", "ALLIED_HEALTH", "OPTOMETRY", "EYE_OPTOMETRY", "Sunita", "Rao", "sunita.optometry"},
            {"SENIOR_OPTOMETRIST", "Senior optometrist", "ALLIED_HEALTH", "OPTOMETRY", "EYE_OPTOMETRY", "Karthik", "Iyer", "karthik.optometry"},
            {"CONSULTANT_OPHTHALMOLOGIST", "Consultant ophthalmologist", "CLINICAL", "OPHTHALMOLOGY", "EYE_CONSULTANT", "Vijay", "Kumar", "dr.vijay"},
            {"ASSOCIATE_OPHTHALMOLOGIST", "Associate ophthalmologist", "CLINICAL", "OPHTHALMOLOGY", "EYE_CONSULTANT", "Meera", "Nair", "dr.meera"},
            {"SURGERY_COUNSELLOR", "Surgery counsellor", "ALLIED_HEALTH", "COUNSELLING", "EYE_COUNSELLING", "Lata", "Menon", "lata.counselling"},
            {"PATIENT_CARE_COORDINATOR", "Patient care coordinator", "ADMINISTRATIVE", "COUNSELLING", "EYE_COUNSELLING", "Naveen", "Reddy", "naveen.coordinator"},
            {"OPHTHALMIC_SURGEON", "Ophthalmic surgeon", "CLINICAL", "SURGERY_DAYCARE", "EYE_SURGEON", "Arjun", "Rao", "dr.arjun"},
            {"OT_NURSE", "OT nurse", "NURSING", "SURGERY_DAYCARE", "EYE_OT_NURSE", "Mary", "Joseph", "mary.otnurse"},
            {"ACCOUNTANT", "Accountant", "ADMINISTRATIVE", "BILLING", "EYE_ACCOUNTANT", "Ganesh", "Patil", "ganesh.accounts"},
            {"ACCOUNTS_MANAGER", "Accounts manager", "MANAGEMENT", "BILLING", "EYE_ACCOUNTS_MANAGER", "Shobha", "Kulkarni", "shobha.accounts"},
    };

    static final Map<String, String[]> ROLES = new LinkedHashMap<>();

    static {
        ROLES.put("EYE_FRONT_OFFICE", new String[] {"Eye clinic front office",
                "org.centre.read", "patient.read", "patient.register", "opd.queue.read", "opd.appointment.manage", "opd.checkin",
                "billing.read", "billing.charge", "billing.collect"});
        ROLES.put("EYE_FRONT_OFFICE_SUPERVISOR", new String[] {"Eye clinic front office supervisor",
                "org.centre.read", "patient.read", "patient.register", "opd.queue.read", "opd.appointment.manage", "opd.checkin",
                "billing.read", "billing.charge", "billing.collect", "billing.discount.approve", "billing.report.read"});
        ROLES.put("EYE_OPTOMETRY", new String[] {"Eye clinic optometry",
                "org.centre.read", "patient.read", "opd.queue.read", "eye.clinical.read", "eye.optometry.record"});
        ROLES.put("EYE_CONSULTANT", new String[] {"Eye clinic consultant",
                "org.centre.read", "patient.read", "opd.queue.read", "eye.clinical.read", "eye.optometry.record", "eye.consultation.record"});
        ROLES.put("EYE_COUNSELLING", new String[] {"Eye clinic surgery counselling",
                "org.centre.read", "patient.read", "opd.queue.read", "opd.appointment.manage", "eye.clinical.read",
                "eye.counselling.record", "eye.surgery.schedule", "billing.read"});
        ROLES.put("EYE_SURGEON", new String[] {"Eye clinic surgeon (operation theatre)",
                "org.centre.read", "patient.read", "opd.queue.read", "eye.clinical.read", "eye.surgery.prepare", "eye.surgery.operate"});
        ROLES.put("EYE_OT_NURSE", new String[] {"Eye clinic OT nurse",
                "org.centre.read", "patient.read", "opd.queue.read", "eye.clinical.read", "eye.surgery.prepare"});
        ROLES.put("EYE_ACCOUNTANT", new String[] {"Eye clinic accountant",
                "org.centre.read", "billing.read", "billing.collect", "billing.report.read", "billing.dayclose.submit"});
        ROLES.put("EYE_ACCOUNTS_MANAGER", new String[] {"Eye clinic accounts manager",
                "org.centre.read", "billing.read", "billing.report.read", "billing.dayclose.approve", "billing.discount.approve",
                "billing.tariff.manage", "audit.read"});
    }

    /** Code, name, category, price (rupees). Clinical services are GST-exempt; spectacles at 12 per cent. */
    static final String[][] TARIFF = {
            {"REGISTRATION", "Registration (new patient)", "REGISTRATION", "100"},
            {"CONSULT_NEW", "Consultation, new patient", "CONSULTATION", "600"},
            {"CONSULT_REVIEW", "Consultation, review", "CONSULTATION", "300"},
            {"CONSULT_POST_OP", "Post-operative review", "CONSULTATION", "0"},
            {"REFRACTION", "Refraction and vision assessment", "INVESTIGATION", "250"},
            {"TONOMETRY", "Intraocular pressure (applanation)", "INVESTIGATION", "200"},
            {"OCT_MACULA", "OCT macula", "INVESTIGATION", "1500"},
            {"A_SCAN_BIOMETRY", "A-scan biometry and IOL power", "INVESTIGATION", "900"},
            {"FUNDUS_PHOTO", "Fundus photography", "INVESTIGATION", "700"},
            {"YAG_CAPSULOTOMY", "YAG laser capsulotomy", "PROCEDURE", "3500"},
            {"PKG_PHACO_MONOFOCAL", "Cataract surgery: phaco with monofocal IOL", "SURGERY_PACKAGE", "28000"},
            {"PKG_PHACO_PREMIUM", "Cataract surgery: phaco with premium toric IOL", "SURGERY_PACKAGE", "55000"},
            {"PKG_SICS", "Cataract surgery: SICS with rigid IOL", "SURGERY_PACKAGE", "14000"},
            {"PKG_PTERYGIUM", "Pterygium excision with conjunctival autograft", "SURGERY_PACKAGE", "16000"},
            {"PKG_GLAUCOMA_TRAB", "Trabeculectomy", "SURGERY_PACKAGE", "32000"},
            {"SPECTACLES_SV", "Spectacles, single vision", "OPTICAL", "1800"},
    };

    /** Given, family, sex, age, mobile, area. Synthetic people (invented names and numbers). */
    static final String[][] PATIENTS = {
            {"Lakshmi", "Narayan", "FEMALE", "67", "98450 10001", "Vijayanagar"},
            {"Gopal", "Krishna", "MALE", "72", "98450 10002", "Rajajinagar"},
            {"Fathima", "Begum", "FEMALE", "58", "98450 10003", "Basaveshwaranagar"},
            {"Raghav", "Shetty", "MALE", "34", "98450 10004", "Malleshwaram"},
            {"Savitri", "Devi", "FEMALE", "61", "98450 10005", "Vijayanagar"},
            {"Arjun", "Hegde", "MALE", "19", "98450 10006", "Nagarbhavi"},
            {"Bhavya", "Gowda", "FEMALE", "27", "98450 10007", "Chandra Layout"},
            {"Mohammed", "Irfan", "MALE", "45", "98450 10008", "Kamakshipalya"},
            {"Shanthamma", "Reddy", "FEMALE", "76", "98450 10009", "Vijayanagar"},
            {"Pradeep", "Joshi", "MALE", "52", "98450 10010", "Rajajinagar"},
            {"Ananya", "Murthy", "FEMALE", "8", "98450 10011", "Basaveshwaranagar"},
            {"Suresh", "Babu", "MALE", "63", "98450 10012", "Magadi Road"},
            {"Kavitha", "Srinivas", "FEMALE", "41", "98450 10013", "Vijayanagar"},
            {"Venkatesh", "Prasad", "MALE", "57", "98450 10014", "Nandini Layout"},
            {"Rukmini", "Bai", "FEMALE", "69", "98450 10015", "Malleshwaram"},
            {"Thomas", "Mathew", "MALE", "71", "98450 10016", "Rajajinagar"},
            {"Deepika", "Rao", "FEMALE", "31", "98450 10017", "Vijayanagar"},
            {"Imran", "Pasha", "MALE", "48", "98450 10018", "Chord Road"},
            {"Nirmala", "Sundaram", "FEMALE", "64", "98450 10019", "Basaveshwaranagar"},
            {"Chandrashekar", "Patil", "MALE", "66", "98450 10020", "Vijayanagar"},
            {"Rekha", "Iyengar", "FEMALE", "39", "98450 10021", "Nagarbhavi"},
            {"Harish", "Gowda", "MALE", "55", "98450 10022", "Chandra Layout"},
    };

    private final JdbcClient jdbc;
    private final OrgDirectory directory;
    private final OrgService org;
    private final WorkforceService workforce;
    private final RoleService roles;
    private final AssignmentService assignments;
    private final UserService users;
    private final PatientService patients;
    private final VisitService visits;
    private final SurgeryService surgeries;
    private final BillingService billing;

    ClinicSeed(JdbcClient jdbc, OrgDirectory directory, OrgService org, WorkforceService workforce, RoleService roles,
               AssignmentService assignments, UserService users, PatientService patients, VisitService visits,
               SurgeryService surgeries, BillingService billing) {
        this.jdbc = jdbc;
        this.directory = directory;
        this.org = org;
        this.workforce = workforce;
        this.roles = roles;
        this.assignments = assignments;
        this.users = users;
        this.patients = patients;
        this.visits = visits;
        this.surgeries = surgeries;
        this.billing = billing;
    }

    Summary load(String tenantCode) {
        UUID tenant = directory.tenantIdByCode(tenantCode)
                .orElseThrow(() -> new NotFoundException("Tenant '" + tenantCode + "' not found; load the demonstration tenant first (ops/demo-seed.sh)"));
        return TenantContext.callAs(tenant, () -> build(tenant));
    }

    private Summary build(UUID tenant) {
        boolean demo = jdbc.sql("select is_demo from org.tenant where id = :t").param("t", tenant).query(Boolean.class).single();
        if (!demo) {
            throw new IllegalStateException("The clinic demonstration is loaded only into a demonstration tenant");
        }
        List<UUID> admins = jdbc.sql("""
                select user_id from iam.role_assignment where via_bootstrap and kind = 'ACCESS' order by requested_at, id
                """).query(UUID.class).list();
        if (admins.size() < 2) {
            throw new IllegalStateException("The tenant has no bootstrap administrators");
        }
        Actor a = new Actor(admins.get(0), tenant, null, "clinic-seed (as first administrator)", 0);
        Actor b = new Actor(admins.get(1), tenant, null, "clinic-seed (as second administrator)", 0);
        if (org.centres(a).stream().anyMatch(c -> c.code().equals(CENTRE_CODE))) {
            throw new ConflictException("seed.clinic-exists", CENTRE_NAME + " (" + CENTRE_CODE + ") already exists; the clinic demonstration loads once");
        }

        // ---- structure: city node, legal entity, departments, the centre
        UUID city = cityNode(a);
        UUID legalEntity = org.legalEntities(a).stream().filter(l -> l.code().equals("CARE")).map(OrgModels.LegalEntityView::id)
                .findFirst().orElseGet(() -> org.createLegalEntity(a, new OrgModels.CreateLegalEntity("CARE", "Neha Eye and Health Care Pvt Ltd (demo)")).id());
        Map<String, UUID> library = new HashMap<>();
        org.departments(a).forEach(d -> library.put(d.code(), d.id()));
        for (String[] d : DEPARTMENTS) {
            library.computeIfAbsent(d[0], k -> org.createDepartment(a, new OrgModels.CreateDepartment(d[0], d[1], d[2])).id());
        }
        UUID centre = org.createCentre(a, new OrgModels.CreateCentre(city, legalEntity, CENTRE_CODE, CENTRE_NAME, "EYE_HOSPITAL", List.of())).id();
        Map<String, UUID> centreDepartments = new HashMap<>();
        for (String[] d : DEPARTMENTS) {
            centreDepartments.put(d[0], org.enableDepartment(a, centre, new OrgModels.EnableDepartment(library.get(d[0]), d[3], null)).id());
        }

        // ---- positions (two per department), established at the clinic
        Map<String, UUID> positionLibrary = new HashMap<>();
        workforce.positions(a).forEach(p -> positionLibrary.put(p.code(), p.id()));
        Map<String, UUID> establishments = new HashMap<>();
        for (String[] s : STAFF) {
            UUID position = positionLibrary.computeIfAbsent(s[0],
                    k -> workforce.definePosition(a, new WorkforceModels.DefinePosition(s[0], s[1], s[2], null)).id());
            establishments.put(s[0], workforce.establish(a, centre,
                    new WorkforceModels.Establish(position, centreDepartments.get(s[3]), 1, null)).id());
        }

        // ---- roles: drafted by the first administrator, approved by the second
        Map<String, UUID> roleIds = new HashMap<>();
        roles.roles(a).forEach(r -> roleIds.put(r.code(), r.id()));
        for (Map.Entry<String, String[]> r : ROLES.entrySet()) {
            if (!roleIds.containsKey(r.getKey())) {
                String[] def = r.getValue();
                Set<String> permissions = Set.of(java.util.Arrays.copyOfRange(def, 1, def.length));
                roleIds.put(r.getKey(), publishRole(a, b, r.getKey(), def[0], permissions));
            }
        }

        // ---- staff, logins and centre-scoped access
        List<CredentialsFile.Entry> logins = new ArrayList<>();
        Map<String, Actor> staff = new HashMap<>();
        for (String[] s : STAFF) {
            String given = s[5];
            String family = s[6];
            String username = s[7];
            // Email and login do not depend on the position: the login id is the employee number.
            UUID person = workforce.createPerson(a, new WorkforceModels.CreatePerson(given, family,
                    (given + "." + family + "@vijaya-eye.demo.core-neha.invalid").toLowerCase()), true).id();
            UUID posting = workforce.addPosting(a, person, new WorkforceModels.AddPosting(establishments.get(s[0]), BigDecimal.ONE, null, null)).id();
            IamModels.UserCreated created = users.create(a, new IamModels.CreateUser(person, null));
            IamModels.AssignmentView requested = assignments.request(a, new IamModels.RequestAssignment("ACCESS", created.user().id(),
                    roleIds.get(s[4]), "CENTRE", centre, posting, null, null, s[1] + ", " + CENTRE_NAME));
            assignments.approve(b, requested.id(), new IamModels.Decision("Approved for the clinic demonstration", requested.version()));
            String department = java.util.Arrays.stream(DEPARTMENTS).filter(d -> d[0].equals(s[3])).findFirst().orElseThrow()[3];
            logins.add(new CredentialsFile.Entry(created.user().username(), created.temporaryPassword(),
                    given + " " + family + ", " + s[1] + ", " + department + ", " + CENTRE_NAME));
            staff.put(username, new Actor(created.user().id(), tenant, person, created.user().username(), 0));
        }

        // ---- price list (accounts manager)
        Actor manager = staff.get("shobha.accounts");
        Map<String, UUID> tariff = new HashMap<>();
        for (String[] t : TARIFF) {
            BigDecimal gst = "OPTICAL".equals(t[2]) ? new BigDecimal("12") : BigDecimal.ZERO;
            tariff.put(t[0], billing.createTariffItem(manager, centre,
                    new BillingModels.CreateTariffItem(t[0], t[1], t[2], new BigDecimal(t[3]), gst)).id());
        }

        int patientCount = new Day(centre, staff, tariff).run();
        return new Summary(centre, DEPARTMENTS.length, STAFF.length, patientCount, logins);
    }

    /** A working day at the clinic, by the clinic's own staff. */
    private final class Day {
        final UUID centre;
        final Map<String, UUID> tariff;
        final Actor reception;
        final Actor supervisor;
        final Actor optom;
        final Actor seniorOptom;
        final Actor drVijay;
        final Actor drMeera;
        final Actor counsellor;
        final Actor coordinator;
        final Actor surgeon;
        final Actor nurse;
        final List<UUID> p = new ArrayList<>();

        Day(UUID centre, Map<String, Actor> staff, Map<String, UUID> tariff) {
            this.centre = centre;
            this.tariff = tariff;
            reception = staff.get("priya.reception");
            supervisor = staff.get("ramesh.frontoffice");
            optom = staff.get("sunita.optometry");
            seniorOptom = staff.get("karthik.optometry");
            drVijay = staff.get("dr.vijay");
            drMeera = staff.get("dr.meera");
            counsellor = staff.get("lata.counselling");
            coordinator = staff.get("naveen.coordinator");
            surgeon = staff.get("dr.arjun");
            nurse = staff.get("mary.otnurse");
        }

        int run() {
            for (String[] x : PATIENTS) {
                p.add(patients.register(reception, new PatientModels.RegisterPatient(centre, x[0], x[1], x[2], null,
                        Integer.parseInt(x[3]), x[4], null, x[5] + ", Bengaluru", "Bengaluru", "Karnataka", "560040", null, null,
                        null, null, true)).id());
            }
            var today = BusinessTime.today();
            // Appointments: today's list (some arrive, some not yet, one cancelled) and tomorrow's
            String[] slots = {"09:00", "09:15", "09:30", "09:45", "10:00", "10:15", "10:30", "10:45"};
            List<EyeModels.AppointmentView> todays = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                todays.add(visits.book(reception, centre, new EyeModels.BookAppointment(p.get(i), today, LocalTime.parse(slots[i]),
                        i == 4 ? "REVIEW" : "NEW", i % 3 == 0 ? "ONLINE" : "PHONE", doctor(i).personId(), null)));
            }
            visits.book(reception, centre, new EyeModels.BookAppointment(p.get(16), today, LocalTime.parse("15:00"), "NEW", "PHONE",
                    drVijay.personId(), "Blurred near vision"));
            visits.book(reception, centre, new EyeModels.BookAppointment(p.get(17), today, LocalTime.parse("15:30"), "NEW", "ONLINE",
                    drMeera.personId(), "Red, watering eye"));
            var cancelled = visits.book(reception, centre, new EyeModels.BookAppointment(p.get(18), today, LocalTime.parse("16:00"),
                    "REVIEW", "PHONE", drVijay.personId(), null));
            visits.cancelAppointment(reception, cancelled.id(), new EyeModels.CancelAppointment("Patient asked to come next week", false, cancelled.version()));
            for (int i = 8; i < 11; i++) {
                visits.book(reception, centre, new EyeModels.BookAppointment(p.get(i), today.plusDays(1),
                        LocalTime.parse(slots[i - 8]), "NEW", "PHONE", drVijay.personId(), null));
            }

            // 1. Myopia: glasses, paid by UPI, checked out
            UUID v1 = checkIn(todays.get(0), "Difficulty seeing the board");
            optometry(optom, v1, "6/36", "6/24", "-2.50", "-0.50", 180, "6/6", "-2.00", "-0.25", 170, "6/6", 14, 15, true);
            consult(drVijay, v1, "Myopia with astigmatism, both eyes", "H52.1", "BE", "Spectacles for constant wear", true, 365, null, null);
            bill(v1, p.get(0), "UPI", "UPI-4471", "CONSULT_NEW", "REFRACTION", "REGISTRATION");
            checkout(v1, null);

            // 2. Cataract right eye: counselled and accepted, operated on later today (ready in OT)
            UUID v2 = checkIn(todays.get(1), "Dim vision in the right eye for six months");
            optometry(optom, v2, "6/60", "6/12", "+1.00", "-0.75", 90, "6/36", "+0.75", "-0.50", 85, "6/9", 16, 15, true);
            consult(drVijay, v2, "Immature senile cataract, right eye", "H25.1", "RE", "Cataract surgery advised", false, null,
                    "Phacoemulsification with foldable IOL", "RE");
            UUID s2 = counsel(counsellor, v2, "PKG_PHACO_MONOFOCAL", "INSURANCE", "Star Health (demo)", "ACCEPTED");
            bill(v2, p.get(1), "CASH", null, "CONSULT_NEW", "REFRACTION", "A_SCAN_BIOMETRY", "REGISTRATION");
            checkout(v2, null);
            schedule(coordinator, s2, today, "14:00");
            preOp(nurse, s2, true);
            packageBill(p.get(1), s2, "PKG_PHACO_MONOFOCAL", "CASH", new BigDecimal("5000"), null);

            // 3. Cataract left eye: accepted, scheduled tomorrow; consultation bill waits for a discount approval
            UUID v3 = startWalkIn(p.get(11), "NEW", "Glare while driving at night", drMeera);
            optometry(seniorOptom, v3, "6/24", "6/18", "-0.50", "-1.00", 175, "6/12", "-0.75", "-1.25", 10, "6/9", 18, 17, true);
            consult(drMeera, v3, "Nuclear sclerosis, left eye", "H25.1", "LE", "Toric IOL suits the corneal astigmatism", false, null,
                    "Phacoemulsification with toric IOL", "LE");
            UUID s3 = counsel(coordinator, v3, "PKG_PHACO_PREMIUM", "SELF_PAY", null, "ACCEPTED");
            schedule(coordinator, s3, today.plusDays(1), "09:30");
            UUID b3 = openBill(v3, p.get(11), "CONSULT_NEW", "REFRACTION", "A_SCAN_BIOMETRY", "REGISTRATION").bill().id();
            var bill3 = billing.bill(reception, b3).bill();
            billing.requestDiscount(reception, b3, new BillingModels.RequestDiscount(new BigDecimal("300"), "Senior citizen concession", bill3.version()));

            // 4. Pterygium: doctor advised surgery, waiting for the counsellor
            UUID v4 = checkIn(todays.get(2), "Fleshy growth on the right eye");
            optometry(optom, v4, "6/9", "6/6", null, null, null, "6/6", null, null, null, "6/6", 14, 14, true);
            consult(drVijay, v4, "Primary pterygium, right eye, encroaching cornea", "H11.0", "RE", "Lubricants until surgery", false, null,
                    "Pterygium excision with conjunctival autograft", "RE");

            // 5. Glaucoma: medicines, bill raised, waiting to pay at checkout
            UUID v5 = checkIn(todays.get(3), "Family history of glaucoma");
            optometry(seniorOptom, v5, "6/9", "6/9", "+0.50", null, null, "6/6", "+0.50", null, null, "6/6", 26, 24, true);
            consult(drVijay, v5, "Primary open-angle glaucoma, both eyes", "H40.1", "BE",
                    "Timolol 0.5% eye drops twice daily, both eyes; review IOP in 6 weeks", false, 42, null, null);
            openBill(v5, p.get(3), "CONSULT_NEW", "TONOMETRY", "OCT_MACULA", "REGISTRATION");

            // 6-7. Optometry done, waiting for the doctor
            UUID v6 = checkIn(todays.get(4), "Review of diabetic eye check");
            optometry(optom, v6, "6/12", "6/12", null, null, null, "6/9", null, null, null, "6/9", 15, 16, true);
            UUID v7 = startWalkIn(p.get(12), "NEW", "Headache while reading", drMeera);
            optometry(seniorOptom, v7, "6/6", "6/6", null, null, null, "6/6", null, null, null, "6/6", 13, 13, true);

            // 8-10. Waiting for optometry (one with a draft work-up)
            checkIn(todays.get(5), "Itching in both eyes");
            startWalkIn(p.get(13), "NEW", "Floaters in the left eye", drVijay);
            UUID v10 = startWalkIn(p.get(14), "REVIEW", "Spectacle check", drMeera);
            optometry(optom, v10, "6/12", "6/9", null, null, null, null, null, null, null, null, null, null, false);

            // 11. Diabetic retinopathy: completed, paid by card
            UUID v11 = startWalkIn(p.get(15), "NEW", "Diabetic for 12 years, annual eye check", drVijay);
            optometry(seniorOptom, v11, "6/12", "6/18", null, null, null, "6/9", null, null, null, "6/12", 17, 18, true);
            consult(drVijay, v11, "Moderate non-proliferative diabetic retinopathy without macular oedema", "E11.3", "BE",
                    "Strict sugar control; repeat fundus in 6 months", false, 180, null, null);
            bill(v11, p.get(15), "CARD", "CARD-****1234", "CONSULT_NEW", "FUNDUS_PHOTO", "REGISTRATION");
            checkout(v11, null);

            // 12. Free review: checked out without a bill
            UUID v12 = checkIn(todays.get(6), "Review after spectacles");
            optometry(optom, v12, "6/6", "6/6", null, null, null, "6/6", null, null, null, "6/6", 14, 14, true);
            consult(drMeera, v12, "Refractive error, corrected", "H52.7", "BE", "Continue spectacles", false, 365, null, null);
            checkout(v12, "Review within 15 days of a paid consultation (no charge)");

            // 13. Cataract operated today: insurance-paid package, post-operative review booked for tomorrow
            UUID v13 = startWalkIn(p.get(19), "NEW", "Very poor vision in the right eye", drVijay);
            optometry(seniorOptom, v13, "CF", "6/18", null, null, null, "CF", "+1.00", null, null, "6/12", 15, 16, true);
            consult(drVijay, v13, "Mature senile cataract, right eye", "H25.2", "RE", "Early surgery advised", false, null,
                    "Phacoemulsification with foldable IOL", "RE");
            UUID s13 = counsel(counsellor, v13, "PKG_PHACO_MONOFOCAL", "INSURANCE", "Niva Bupa (demo)", "ACCEPTED");
            bill(v13, p.get(19), "CASH", null, "CONSULT_NEW", "A_SCAN_BIOMETRY", "REGISTRATION");
            checkout(v13, null);
            schedule(coordinator, s13, today, "08:30");
            preOp(nurse, s13, true);
            operate(s13);
            packageBill(p.get(19), s13, "PKG_PHACO_MONOFOCAL", "INSURANCE", null, "TPA-AUTH-NB-20931");

            // 14. Cataract accepted, waiting for a date (to schedule)
            UUID v14 = startWalkIn(p.get(20), "NEW", "Cloudy vision, left eye", drMeera);
            optometry(optom, v14, "6/18", "6/36", null, null, null, "6/12", null, null, null, "6/24", 15, 15, true);
            consult(drMeera, v14, "Immature senile cataract, left eye", "H25.1", "LE", "Surgery when convenient", false, null,
                    "Phacoemulsification with foldable IOL", "LE");
            counsel(coordinator, v14, "PKG_PHACO_MONOFOCAL", "CORPORATE", null, "ACCEPTED");
            bill(v14, p.get(20), "UPI", "UPI-8812", "CONSULT_NEW", "REFRACTION", "REGISTRATION");
            checkout(v14, null);

            // 15. Spectacles bill on a review visit, waiting at checkout
            UUID v15 = checkIn(todays.get(7), "New spectacles");
            optometry(seniorOptom, v15, "6/18", "6/18", "-1.25", null, null, "6/6", "-1.50", null, null, "6/6", 12, 13, true);
            consult(drMeera, v15, "Myopia, both eyes", "H52.1", "BE", "Spectacles", true, 365, null, null);
            openBill(v15, p.get(7), "CONSULT_NEW", "REFRACTION", "SPECTACLES_SV");
            return p.size();
        }

        Actor doctor(int i) {
            return i % 2 == 0 ? drVijay : drMeera;
        }

        UUID checkIn(EyeModels.AppointmentView appointment, String complaint) {
            return visits.checkIn(reception, appointment.id(), new EyeModels.CheckInAppointment(complaint, null, appointment.version())).id();
        }

        UUID startWalkIn(UUID patient, String type, String complaint, Actor doctor) {
            return visits.startWalkIn(reception, centre, new EyeModels.StartVisit(patient, type, complaint, doctor.personId())).id();
        }

        void optometry(Actor who, UUID visit, String ucvaRe, String ucvaLe, String sphRe, String cylRe, Integer axisRe, String bcvaRe,
                       String sphLe, String cylLe, Integer axisLe, String bcvaLe, Integer iopRe, Integer iopLe, boolean complete) {
            visits.saveOptometry(who, visit, new EyeModels.SaveOptometry(ucvaRe, ucvaLe, null, null, null, null,
                    dec(sphRe), dec(cylRe), cylRe == null ? null : axisRe, null, bcvaRe,
                    dec(sphLe), dec(cylLe), cylLe == null ? null : axisLe, null, bcvaLe,
                    iopRe, iopLe, iopRe == null ? null : "NCT", null, null, complete, null));
        }

        void consult(Actor doctor, UUID visit, String diagnosis, String code, String eye, String advice, boolean glasses, Integer followUp,
                     String procedure, String surgeryEye) {
            boolean surgery = procedure != null;
            visits.saveConsultation(doctor, visit, new EyeModels.SaveConsultation(
                    "No known drug allergies. " + (surgery ? "Vision affecting daily activities." : ""),
                    surgery ? "Lens opacity" : "Normal anterior segment", "Normal anterior segment",
                    "Disc and macula healthy", "Disc and macula healthy", diagnosis, code, eye,
                    surgery ? "Moxifloxacin 0.5% eye drops four times a day, 3 days before surgery" : null,
                    glasses, advice, followUp, surgery, procedure, surgeryEye, surgery ? "ROUTINE" : null, true, null));
        }

        UUID counsel(Actor who, UUID visit, String packageCode, String plan, String insurer, String decision) {
            var s = visits.chart(who, visit).surgery();
            return surgeries.counsel(who, s.id(), new EyeModels.Counsel(tariff.get(packageCode), null, plan, insurer,
                    "Explained the procedure, risks, recovery and costs. Patient and family agreed.", BusinessTime.today().plusDays(7),
                    decision, s.version())).id();
        }

        void schedule(Actor who, UUID surgery, java.time.LocalDate date, String time) {
            var s = surgeries.surgery(who, surgery);
            surgeries.schedule(who, surgery, new EyeModels.ScheduleSurgery(date, LocalTime.parse(time), "OT 1", surgeon.personId(), s.version()));
        }

        void preOp(Actor who, UUID surgery, boolean ready) {
            var s = surgeries.surgery(who, surgery);
            surgeries.preOp(who, surgery, new EyeModels.PreOp(true, true, true, true, "+21.5 D", true, "130/80", "142 mg/dL",
                    "Fasting since midnight; consent from patient and son", ready, s.version()));
        }

        void operate(UUID surgery) {
            var s = surgeries.surgery(surgeon, surgery);
            surgeries.operate(surgeon, surgery, new EyeModels.Operation("TOPICAL", "Hydrophobic acrylic foldable IOL +21.5 D in the bag",
                    "Clear corneal incision, capsulorhexis, phacoemulsification, IOL in the bag. Wound sealed.",
                    "None", "Moxifloxacin-prednisolone eye drops 6 times a day; shield at night; review tomorrow", true, s.version()));
        }

        BillingModels.BillDetail openBill(UUID visit, UUID patient, String... codes) {
            List<BillingModels.LineInput> lines = java.util.Arrays.stream(codes).map(c -> new BillingModels.LineInput(tariff.get(c), 1)).toList();
            return billing.createBill(reception, centre, new BillingModels.CreateBill(patient, visit, null, lines));
        }

        void bill(UUID visit, UUID patient, String mode, String reference, String... codes) {
            var created = openBill(visit, patient, codes).bill();
            billing.collect(reception, created.id(), new BillingModels.CollectPayment(mode, created.netAmount(), reference, created.version()));
        }

        void packageBill(UUID patient, UUID surgery, String code, String mode, BigDecimal amount, String reference) {
            var created = billing.createBill(reception, centre, new BillingModels.CreateBill(patient, null, surgery,
                    List.of(new BillingModels.LineInput(tariff.get(code), 1)))).bill();
            billing.collect(reception, created.id(), new BillingModels.CollectPayment(mode, amount == null ? created.netAmount() : amount,
                    reference, created.version()));
        }

        void checkout(UUID visit, String noChargeReason) {
            var v = visits.chart(reception, visit).visit();
            visits.checkout(reception, visit, new EyeModels.Checkout(noChargeReason, v.version()));
        }

        BigDecimal dec(String s) {
            return s == null ? null : new BigDecimal(s);
        }
    }

    private UUID cityNode(Actor a) {
        var nodes = org.nodes(a);
        var existing = nodes.stream().filter(n -> n.code().equals("BLR")).findFirst();
        if (existing.isPresent()) {
            return existing.get().id();
        }
        var levels = org.levels(a);
        UUID state = levels.stream().filter(l -> l.code().equals("STATE")).map(OrgModels.LevelView::id).findFirst()
                .orElseGet(() -> org.createLevel(a, new OrgModels.CreateLevel("STATE", "State", 1)).id());
        UUID cityLevel = levels.stream().filter(l -> l.code().equals("CITY")).map(OrgModels.LevelView::id).findFirst()
                .orElseGet(() -> org.createLevel(a, new OrgModels.CreateLevel("CITY", "City", 2)).id());
        UUID ka = nodes.stream().filter(n -> n.code().equals("KA")).map(OrgModels.NodeView::id).findFirst()
                .orElseGet(() -> org.createNode(a, new OrgModels.CreateNode(state, null, "KA", "Karnataka")).id());
        return org.createNode(a, new OrgModels.CreateNode(cityLevel, ka, "BLR", "Bengaluru")).id();
    }

    private UUID publishRole(Actor a, Actor b, String code, String name, Set<String> permissions) {
        IamModels.RoleDetail draft = roles.create(a, new IamModels.CreateRole(code, name, "Vijaya Eye Clinic demonstration role", permissions, "Initial version"));
        IamModels.RoleVersionView v = draft.versions().getFirst();
        IamModels.RoleDetail submitted = roles.submit(a, v.id(), new IamModels.Decision("Ready for review", v.version()));
        roles.approve(b, v.id(), new IamModels.Decision("Reviewed", submitted.versions().getFirst().version()));
        return draft.role().id();
    }
}
