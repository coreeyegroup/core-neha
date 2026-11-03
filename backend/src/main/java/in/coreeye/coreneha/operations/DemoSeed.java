package in.coreeye.coreneha.operations;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import in.coreeye.coreneha.iam.AssignmentService;
import in.coreeye.coreneha.iam.BootstrapService;
import in.coreeye.coreneha.iam.IamModels;
import in.coreeye.coreneha.iam.ResponsibilityService;
import in.coreeye.coreneha.iam.RoleService;
import in.coreeye.coreneha.iam.UserService;
import in.coreeye.coreneha.kernel.Actor;
import in.coreeye.coreneha.kernel.TenantContext;
import in.coreeye.coreneha.org.OrgModels;
import in.coreeye.coreneha.org.OrgService;
import in.coreeye.coreneha.workforce.WorkforceModels;
import in.coreeye.coreneha.workforce.WorkforceService;
import org.springframework.stereotype.Component;

/**
 * Synthetic demonstration tenant (ADR-0010): 20 centres with different
 * capability combinations, a department and position library, synthetic staff
 * with dated postings (some at several centres), custom roles and scoped
 * assignments. Everything goes through the normal services and rules: roles and
 * assignments are drafted or requested by one administrator and approved by the other.
 */
@Component
class DemoSeed {

    record Summary(String tenantCode, UUID tenantId, int centres, int people, List<CredentialsFile.Entry> logins) {
    }

    static final String TENANT_CODE = "demo";

    // Department library: code, name, category
    static final String[][] DEPARTMENTS = {
            {"RECEPTION", "Reception and front office", "ADMINISTRATIVE"},
            {"CENTRE_MANAGEMENT", "Centre management", "ADMINISTRATIVE"},
            {"BILLING", "Billing and accounts", "ADMINISTRATIVE"},
            {"CONSULTATION", "General consultation", "CLINICAL"},
            {"OPHTHALMOLOGY", "Ophthalmology", "CLINICAL"},
            {"OPTOMETRY", "Optometry", "CLINICAL"},
            {"ENT", "ENT", "CLINICAL"},
            {"CARDIOLOGY", "Cardiology", "CLINICAL"},
            {"COUNSELLING", "Counselling", "CLINICAL"},
            {"SURGERY_DAYCARE", "Surgery and day-care", "CLINICAL"},
            {"ANAESTHESIA", "Anaesthesia", "CLINICAL"},
            {"LABORATORY", "Laboratory", "CLINICAL_SUPPORT"},
            {"PHARMACY", "Pharmacy", "CLINICAL_SUPPORT"},
            {"OPTICAL_RETAIL", "Optical retail and dispensing", "OPERATIONS"},
            {"OPTICAL_MANUFACTURING", "Optical manufacturing", "OPERATIONS"},
            {"STORES", "Stores", "OPERATIONS"},
    };

    // Centre templates: capability combinations
    static final Map<String, List<String>> TEMPLATES = new LinkedHashMap<>();

    static {
        TEMPLATES.put("EYE_CLINIC", List.of("RECEPTION", "CENTRE_MANAGEMENT", "BILLING", "OPHTHALMOLOGY", "OPTOMETRY", "OPTICAL_RETAIL", "PHARMACY"));
        TEMPLATES.put("EYE_HOSPITAL", List.of("RECEPTION", "CENTRE_MANAGEMENT", "BILLING", "OPHTHALMOLOGY", "OPTOMETRY", "OPTICAL_RETAIL",
                "PHARMACY", "SURGERY_DAYCARE", "ANAESTHESIA", "LABORATORY", "STORES"));
        TEMPLATES.put("MULTISPECIALTY_CLINIC", List.of("RECEPTION", "CENTRE_MANAGEMENT", "BILLING", "CONSULTATION", "ENT", "CARDIOLOGY",
                "COUNSELLING", "LABORATORY", "PHARMACY"));
        TEMPLATES.put("DIAGNOSTIC_CENTRE", List.of("RECEPTION", "CENTRE_MANAGEMENT", "BILLING", "LABORATORY", "CARDIOLOGY"));
        TEMPLATES.put("OPTICAL_STORE", List.of("RECEPTION", "BILLING", "OPTOMETRY", "OPTICAL_RETAIL"));
        TEMPLATES.put("OPTICAL_LAB", List.of("CENTRE_MANAGEMENT", "BILLING", "OPTICAL_MANUFACTURING", "STORES"));
    }

    // State, city, centre code, centre name, template, legal entity
    static final String[][] CENTRES = {
            {"KA", "BLR", "BLR-IND", "Indiranagar Eye Hospital", "EYE_HOSPITAL", "HOSP"},
            {"KA", "BLR", "BLR-JAY", "Jayanagar Eye Clinic", "EYE_CLINIC", "CARE"},
            {"KA", "BLR", "BLR-WHF", "Whitefield Multispecialty Clinic", "MULTISPECIALTY_CLINIC", "CARE"},
            {"KA", "BLR", "BLR-OPL", "Peenya Optical Laboratory", "OPTICAL_LAB", "OPTL"},
            {"KA", "BLR", "BLR-KOR", "Koramangala Optical Store", "OPTICAL_STORE", "CARE"},
            {"KA", "MYS", "MYS-VVM", "Mysuru Eye Clinic", "EYE_CLINIC", "CARE"},
            {"KA", "MYS", "MYS-DGN", "Mysuru Diagnostics", "DIAGNOSTIC_CENTRE", "CARE"},
            {"TN", "CHE", "CHE-ANN", "Anna Nagar Eye Hospital", "EYE_HOSPITAL", "HOSP"},
            {"TN", "CHE", "CHE-TNG", "T Nagar Multispecialty Clinic", "MULTISPECIALTY_CLINIC", "CARE"},
            {"TN", "CHE", "CHE-ADY", "Adyar Optical Store", "OPTICAL_STORE", "CARE"},
            {"TN", "CBE", "CBE-RSP", "RS Puram Eye Clinic", "EYE_CLINIC", "CARE"},
            {"TN", "CBE", "CBE-DGN", "Coimbatore Diagnostics", "DIAGNOSTIC_CENTRE", "CARE"},
            {"TS", "HYD", "HYD-BNJ", "Banjara Hills Eye Hospital", "EYE_HOSPITAL", "HOSP"},
            {"TS", "HYD", "HYD-KPH", "Kukatpally Eye Clinic", "EYE_CLINIC", "CARE"},
            {"TS", "HYD", "HYD-OPL", "Hyderabad Optical Laboratory", "OPTICAL_LAB", "OPTL"},
            {"TS", "HYD", "HYD-MDP", "Madhapur Multispecialty Clinic", "MULTISPECIALTY_CLINIC", "CARE"},
            {"MH", "PUN", "PUN-KTH", "Kothrud Eye Clinic", "EYE_CLINIC", "CARE"},
            {"MH", "PUN", "PUN-DGN", "Pune Diagnostics", "DIAGNOSTIC_CENTRE", "CARE"},
            {"MH", "MUM", "MUM-AND", "Andheri Eye Hospital", "EYE_HOSPITAL", "HOSP"},
            {"MH", "MUM", "MUM-BND", "Bandra Optical Store", "OPTICAL_STORE", "CARE"},
    };

    // Position code, title, category, department where it is established, headcount
    static final String[][] POSITIONS = {
            {"CENTRE_MANAGER", "Centre manager", "MANAGEMENT", "CENTRE_MANAGEMENT", "1"},
            {"RECEPTIONIST", "Receptionist", "ADMINISTRATIVE", "RECEPTION", "2"},
            {"CASHIER", "Cashier", "ADMINISTRATIVE", "BILLING", "1"},
            {"OPHTHALMOLOGIST", "Ophthalmologist", "CLINICAL", "OPHTHALMOLOGY", "2"},
            {"OPTOMETRIST", "Optometrist", "ALLIED_HEALTH", "OPTOMETRY", "2"},
            {"OPTICAL_DISPENSER", "Optical dispenser", "TECHNICAL", "OPTICAL_RETAIL", "2"},
            {"CONSULTANT_PHYSICIAN", "Consultant physician", "CLINICAL", "CONSULTATION", "1"},
            {"ENT_SURGEON", "ENT surgeon", "CLINICAL", "ENT", "1"},
            {"CARDIOLOGIST", "Cardiologist", "CLINICAL", "CARDIOLOGY", "1"},
            {"COUNSELLOR", "Counsellor", "ALLIED_HEALTH", "COUNSELLING", "1"},
            {"SURGEON", "Surgeon", "CLINICAL", "SURGERY_DAYCARE", "2"},
            {"ANAESTHETIST", "Anaesthetist", "CLINICAL", "ANAESTHESIA", "1"},
            {"LAB_TECHNICIAN", "Laboratory technician", "TECHNICAL", "LABORATORY", "2"},
            {"PHARMACIST", "Pharmacist", "ALLIED_HEALTH", "PHARMACY", "1"},
            {"LENS_TECHNICIAN", "Lens technician", "TECHNICAL", "OPTICAL_MANUFACTURING", "4"},
            {"QC_INSPECTOR", "Quality control inspector", "TECHNICAL", "OPTICAL_MANUFACTURING", "1"},
            {"STORE_KEEPER", "Store keeper", "SUPPORT", "STORES", "1"},
            {"INTERNAL_AUDITOR", "Internal auditor", "MANAGEMENT", "CENTRE_MANAGEMENT", "1"},
    };

    static final String[] GIVEN = {"Meena", "Rajesh", "Anita", "Kiran", "Arun", "Lakshmi", "Suresh", "Divya", "Farhan", "Geeta",
            "Harish", "Isha", "Joseph", "Kavya", "Manoj", "Nandini", "Omkar", "Pooja", "Rahul", "Sneha", "Tarun", "Usha",
            "Vikram", "Yasmin", "Zubin", "Bhavana", "Chetan", "Deepa", "Eshwar", "Fatima"};
    static final String[] FAMILY = {"Iyer", "Rao", "Sharma", "Reddy", "Nair", "Patil", "Menon", "Gupta", "Khan", "Das",
            "Kulkarni", "Pillai", "Fernandes", "Joshi", "Shetty"};

    private final BootstrapService bootstrap;
    private final OrgService org;
    private final WorkforceService workforce;
    private final RoleService roles;
    private final AssignmentService assignments;
    private final UserService users;
    private final ResponsibilityService responsibilities;

    DemoSeed(BootstrapService bootstrap, OrgService org, WorkforceService workforce, RoleService roles,
             AssignmentService assignments, UserService users, ResponsibilityService responsibilities) {
        this.bootstrap = bootstrap;
        this.org = org;
        this.workforce = workforce;
        this.roles = roles;
        this.assignments = assignments;
        this.users = users;
        this.responsibilities = responsibilities;
    }

    Summary load() {
        BootstrapService.Result boot = bootstrap.bootstrap(TENANT_CODE, "Neha Health Group (demonstration)", true, List.of(
                new BootstrapService.Administrator("Asha", "Admin", "asha.admin@demo.core-neha.invalid", "asha.admin", null),
                new BootstrapService.Administrator("Bala", "Approver", "bala.approver@demo.core-neha.invalid", "bala.approver", null)));
        UUID tenant = boot.tenantId();
        Actor a = new Actor(boot.userIds().get(0), tenant, null, "demo-seed (as asha.admin)", 0);
        Actor b = new Actor(boot.userIds().get(1), tenant, null, "demo-seed (as bala.approver)", 0);
        List<CredentialsFile.Entry> logins = new ArrayList<>();
        boot.credentials().forEach(c -> logins.add(new CredentialsFile.Entry(c.username(), c.temporaryPassword(),
                "Administrator (" + (c.username().startsWith("asha") ? "requests and configures" : "approves") + ")")));
        int[] people = {0};
        TenantContext.callAs(tenant, () -> {
            build(a, b, tenant, logins, people);
            return null;
        });
        return new Summary(TENANT_CODE, tenant, CENTRES.length, people[0] + 2, logins);
    }

    private void build(Actor a, Actor b, UUID tenant, List<CredentialsFile.Entry> logins, int[] peopleCount) {
        // Hierarchy and legal entities
        UUID state = org.createLevel(a, new OrgModels.CreateLevel("STATE", "State", 1)).id();
        UUID city = org.createLevel(a, new OrgModels.CreateLevel("CITY", "City", 2)).id();
        Map<String, UUID> nodes = new HashMap<>();
        String[][] states = {{"KA", "Karnataka"}, {"TN", "Tamil Nadu"}, {"TS", "Telangana"}, {"MH", "Maharashtra"}};
        for (String[] s : states) {
            nodes.put(s[0], org.createNode(a, new OrgModels.CreateNode(state, null, s[0], s[1])).id());
        }
        String[][] cities = {{"KA", "BLR", "Bengaluru"}, {"KA", "MYS", "Mysuru"}, {"TN", "CHE", "Chennai"}, {"TN", "CBE", "Coimbatore"},
                {"TS", "HYD", "Hyderabad"}, {"MH", "PUN", "Pune"}, {"MH", "MUM", "Mumbai"}};
        for (String[] c : cities) {
            nodes.put(c[1], org.createNode(a, new OrgModels.CreateNode(city, nodes.get(c[0]), c[1], c[2])).id());
        }
        Map<String, UUID> entities = new HashMap<>();
        entities.put("CARE", org.createLegalEntity(a, new OrgModels.CreateLegalEntity("CARE", "Neha Eye and Health Care Pvt Ltd (demo)")).id());
        entities.put("HOSP", org.createLegalEntity(a, new OrgModels.CreateLegalEntity("HOSP", "Neha Hospitals Pvt Ltd (demo)")).id());
        entities.put("OPTL", org.createLegalEntity(a, new OrgModels.CreateLegalEntity("OPTL", "Neha Optical Labs Pvt Ltd (demo)")).id());

        // Department library, then centres from templates (each centre gets only its own departments)
        Map<String, UUID> departments = new HashMap<>();
        for (String[] d : DEPARTMENTS) {
            departments.put(d[0], org.createDepartment(a, new OrgModels.CreateDepartment(d[0], d[1], d[2])).id());
        }
        Map<String, UUID> centres = new LinkedHashMap<>();
        for (String[] c : CENTRES) {
            List<UUID> deptIds = TEMPLATES.get(c[4]).stream().map(departments::get).toList();
            centres.put(c[2], org.createCentre(a, new OrgModels.CreateCentre(nodes.get(c[1]), entities.get(c[5]), c[2], c[3], c[4], deptIds)).id());
        }

        // Position library and establishments where the department is enabled
        Map<String, UUID> positions = new HashMap<>();
        for (String[] p : POSITIONS) {
            positions.put(p[0], workforce.definePosition(a, new WorkforceModels.DefinePosition(p[0], p[1], p[2], null)).id());
        }
        Map<String, Map<String, UUID>> establishments = new HashMap<>();   // centre code -> position code -> establishment
        for (Map.Entry<String, UUID> centre : centres.entrySet()) {
            Map<String, UUID> byDept = new HashMap<>();
            org.centreDepartments(a, centre.getValue(), false).forEach(cd -> byDept.put(cd.departmentCode(), cd.id()));
            Map<String, UUID> est = new HashMap<>();
            for (String[] p : POSITIONS) {
                UUID cd = byDept.get(p[3]);
                if (cd != null) {
                    est.put(p[0], workforce.establish(a, centre.getValue(),
                            new WorkforceModels.Establish(positions.get(p[0]), cd, Integer.parseInt(p[4]), null)).id());
                }
            }
            establishments.put(centre.getKey(), est);
        }

        // Custom roles: drafted and submitted by Asha, approved by Bala
        UUID centreManager = publishRole(a, b, "CENTRE_MANAGER_ACCESS", "Centre manager access",
                Set.of("org.centre.read", "org.centre.configure", "workforce.read", "workforce.establish",
                        "workforce.person.manage", "iam.user.manage", "iam.assignment.read", "iam.assignment.request", "audit.read"));
        UUID frontOffice = publishRole(a, b, "FRONT_OFFICE", "Front office", Set.of("org.centre.read"));
        UUID clinical = publishRole(a, b, "CLINICAL_STAFF", "Clinical staff", Set.of("org.centre.read", "workforce.read"));
        UUID auditor = publishRole(a, b, "GROUP_AUDITOR", "Group auditor (read only)",
                Set.of("org.structure.read", "org.centre.read", "workforce.read", "iam.assignment.read", "iam.role.read", "audit.read"));

        // Synthetic staff: one manager per centre plus clinical, front-office and technical staff
        int n = 0;
        Map<String, UUID> namedPeople = new HashMap<>();
        for (Map.Entry<String, UUID> centre : centres.entrySet()) {
            Map<String, UUID> est = establishments.get(centre.getKey());
            for (String position : List.of("CENTRE_MANAGER", "RECEPTIONIST", "OPTOMETRIST", "OPTICAL_DISPENSER", "LAB_TECHNICIAN",
                    "LENS_TECHNICIAN", "CONSULTANT_PHYSICIAN")) {
                UUID e = est.get(position);
                if (e == null) {
                    continue;
                }
                String given = GIVEN[n % GIVEN.length];
                String family = FAMILY[(n * 7) % FAMILY.length];
                UUID person = workforce.createPerson(a, new WorkforceModels.CreatePerson(given, family,
                        (given + "." + family + "." + n + "@demo.core-neha.invalid").toLowerCase()), true).id();
                workforce.addPosting(a, person, new WorkforceModels.AddPosting(e, BigDecimal.ONE, null, null));
                namedPeople.putIfAbsent(position + "@" + centre.getKey(), person);
                n++;
            }
        }
        // A visiting ophthalmologist posted at two centres (independent dates and FTE)
        UUID visiting = workforce.createPerson(a, new WorkforceModels.CreatePerson("Ravi", "Rao", "ravi.rao.visiting@demo.core-neha.invalid"), true).id();
        workforce.addPosting(a, visiting, new WorkforceModels.AddPosting(establishments.get("BLR-IND").get("OPHTHALMOLOGIST"), new BigDecimal("0.60"), null, null));
        workforce.addPosting(a, visiting, new WorkforceModels.AddPosting(establishments.get("BLR-JAY").get("OPHTHALMOLOGIST"), new BigDecimal("0.40"), null, null));
        // A group auditor posted at the hospital's management department
        UUID auditorPerson = workforce.createPerson(a, new WorkforceModels.CreatePerson("Anita", "Auditor", "anita.auditor@demo.core-neha.invalid"), true).id();
        workforce.addPosting(a, auditorPerson, new WorkforceModels.AddPosting(establishments.get("CHE-ANN").get("INTERNAL_AUDITOR"), new BigDecimal("0.10"), null, null));
        peopleCount[0] = n + 2;

        // Login-enabled demonstration users with scoped, approved assignments
        UUID meena = namedPeople.get("CENTRE_MANAGER@BLR-IND");
        UUID kiran = namedPeople.get("RECEPTIONIST@BLR-JAY");
        UUID arun = namedPeople.get("OPTICAL_DISPENSER@BLR-KOR");
        login(a, logins, meena, "meena.manager", "Centre manager, Indiranagar Eye Hospital");
        login(a, logins, visiting, "ravi.visiting", "Visiting ophthalmologist at two centres");
        login(a, logins, auditorPerson, "anita.auditor", "Group auditor (read-only, all centres)");
        login(a, logins, kiran, "kiran.frontdesk", "Receptionist, Jayanagar Eye Clinic");
        login(a, logins, arun, "arun.optical", "Optical dispenser, Koramangala (stock controller responsibility)");
        Map<String, UUID> userIds = new HashMap<>();
        users.list(a).forEach(u -> userIds.put(u.username(), u.id()));

        grant(a, b, "ACCESS", userIds.get("meena.manager"), centreManager, "CENTRE", centres.get("BLR-IND"), "Centre manager");
        grant(a, b, "AUTHORITY", userIds.get("meena.manager"), frontOffice, "CENTRE", centres.get("BLR-IND"),
                "May request front-office access for her centre");
        grant(a, b, "ACCESS", userIds.get("ravi.visiting"), clinical, "CENTRE", centres.get("BLR-IND"), "Visiting consultant (Tue/Thu)");
        grant(a, b, "ACCESS", userIds.get("ravi.visiting"), clinical, "CENTRE", centres.get("BLR-JAY"), "Visiting consultant (Mon/Wed)");
        grant(a, b, "ACCESS", userIds.get("anita.auditor"), auditor, "TENANT", tenant, "Group internal audit");
        grant(a, b, "ACCESS", userIds.get("kiran.frontdesk"), frontOffice, "CENTRE", centres.get("BLR-JAY"), "Front office");
        grant(a, b, "ACCESS", userIds.get("arun.optical"), frontOffice, "CENTRE", centres.get("BLR-KOR"), "Optical counter");

        // Responsibilities record accountability only
        UUID stock = responsibilities.define(a, new IamModels.DefineResponsibility("STOCK_CONTROLLER", "Stock controller")).id();
        UUID safety = responsibilities.define(a, new IamModels.DefineResponsibility("FIRE_SAFETY_WARDEN", "Fire safety warden")).id();
        responsibilities.assign(a, new IamModels.AssignResponsibility(arun, stock, "CENTRE", centres.get("BLR-KOR"), null, null));
        responsibilities.assign(a, new IamModels.AssignResponsibility(meena, safety, "CENTRE", centres.get("BLR-IND"), null, null));
    }

    private UUID publishRole(Actor a, Actor b, String code, String name, Set<String> permissions) {
        IamModels.RoleDetail draft = roles.create(a, new IamModels.CreateRole(code, name, "Demonstration role", permissions, "Initial version"));
        IamModels.RoleVersionView v = draft.versions().getFirst();
        IamModels.RoleDetail submitted = roles.submit(a, v.id(), new IamModels.Decision("Ready for review", v.version()));
        roles.approve(b, v.id(), new IamModels.Decision("Reviewed", submitted.versions().getFirst().version()));
        return draft.role().id();
    }

    private void grant(Actor a, Actor b, String kind, UUID userId, UUID roleId, String scopeType, UUID scopeId, String reason) {
        IamModels.AssignmentView requested = assignments.request(a, new IamModels.RequestAssignment(kind, userId, roleId,
                scopeType, scopeId, null, null, null, reason));
        assignments.approve(b, requested.id(), new IamModels.Decision("Approved for demonstration", requested.version()));
    }

    private void login(Actor a, List<CredentialsFile.Entry> logins, UUID personId, String username, String description) {
        IamModels.UserCreated created = users.create(a, new IamModels.CreateUser(personId, username));
        logins.add(new CredentialsFile.Entry(username, created.temporaryPassword(), description));
    }
}
