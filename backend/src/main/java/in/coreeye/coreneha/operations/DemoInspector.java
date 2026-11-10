package in.coreeye.coreneha.operations;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import in.coreeye.coreneha.iam.AssignmentService;
import in.coreeye.coreneha.iam.IamModels;
import in.coreeye.coreneha.iam.RoleService;
import in.coreeye.coreneha.iam.UserService;
import in.coreeye.coreneha.kernel.Actor;
import in.coreeye.coreneha.kernel.NotFoundException;
import in.coreeye.coreneha.kernel.Permission;
import in.coreeye.coreneha.kernel.TenantContext;
import in.coreeye.coreneha.org.OrgDirectory;
import in.coreeye.coreneha.org.OrgModels;
import in.coreeye.coreneha.org.OrgService;
import in.coreeye.coreneha.workforce.WorkforceModels;
import in.coreeye.coreneha.workforce.WorkforceService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * One login for the developer to look at everything in the DEMONSTRATION organisation: a role with
 * every permission, given at whole-organisation scope through the normal controls (drafted by one
 * administrator, approved by the other, audited). It is not a bypass: the licence, the scope checks
 * and every two-person rule still apply to it. Refused for any organisation not marked as demo.
 */
@Component
class DemoInspector {

    static final String ROLE_CODE = "DEMO_INSPECTOR";
    static final String POSITION_CODE = "DEMO_INSPECTOR";
    static final String GIVEN = "Demo";
    static final String FAMILY = "Inspector";

    record Summary(boolean created, String username, int permissions, CredentialsFile.Entry login) {
    }

    private final JdbcClient jdbc;
    private final OrgDirectory directory;
    private final OrgService org;
    private final WorkforceService workforce;
    private final RoleService roles;
    private final AssignmentService assignments;
    private final UserService users;

    DemoInspector(JdbcClient jdbc, OrgDirectory directory, OrgService org, WorkforceService workforce, RoleService roles,
                  AssignmentService assignments, UserService users) {
        this.jdbc = jdbc;
        this.directory = directory;
        this.org = org;
        this.workforce = workforce;
        this.roles = roles;
        this.assignments = assignments;
        this.users = users;
    }

    Summary load(String tenantCode) {
        UUID tenant = directory.tenantIdByCode(tenantCode)
                .orElseThrow(() -> new NotFoundException("Tenant '" + tenantCode + "' not found; load the demonstration tenant first (ops/demo-seed.sh)"));
        return TenantContext.callAs(tenant, () -> build(tenant));
    }

    private Summary build(UUID tenant) {
        boolean demo = jdbc.sql("select is_demo from org.tenant where id = :t").param("t", tenant).query(Boolean.class).single();
        if (!demo) {
            throw new IllegalStateException("The all-permission inspector is created only in a demonstration organisation");
        }
        List<UUID> admins = jdbc.sql("select user_id from iam.role_assignment where via_bootstrap and kind = 'ACCESS' order by requested_at, id")
                .query(UUID.class).list();
        if (admins.size() < 2) {
            throw new IllegalStateException("The tenant has no bootstrap administrators");
        }
        Actor a = new Actor(admins.get(0), tenant, null, "demo-inspector (as first administrator)", 0);
        Actor b = new Actor(admins.get(1), tenant, null, "demo-inspector (as second administrator)", 0);
        Set<String> every = Arrays.stream(Permission.values()).map(Permission::code).collect(Collectors.toSet());

        // The role: every permission. A later run adds permissions created since (a new version, approved by the second administrator).
        UUID roleId = roles.roles(a).stream().filter(r -> r.code().equals(ROLE_CODE)).map(r -> r.id()).findFirst().orElse(null);
        if (roleId == null) {
            IamModels.RoleDetail draft = roles.create(a, new IamModels.CreateRole(ROLE_CODE, "Demonstration inspector (every permission)",
                    "Demonstration only: lets the developer see and try every screen. Never create in a real organisation.", every, "Initial version"));
            approve(a, b, draft.versions().getFirst());
            roleId = draft.role().id();
        } else {
            List<String> granted = jdbc.sql("""
                    select g.permission_code from iam.role_version v join iam.role_version_grant g on g.role_version_id = v.id
                    where v.role_id = :r and v.status = 'PUBLISHED'""").param("r", roleId).query(String.class).list();
            if (!granted.containsAll(every)) {
                IamModels.RoleDetail draft = roles.draftVersion(a, roleId, new IamModels.DraftVersion(every, "Adds permissions created since"));
                approve(a, b, draft.versions().stream().filter(v -> "DRAFT".equals(v.status())).findFirst().orElseThrow());
            }
        }

        String email = ClinicStaff.email(GIVEN, FAMILY);
        var existing = workforce.people(a).stream().filter(p -> p.email() != null && p.email().toLowerCase(Locale.ROOT).equals(email)).findFirst();
        if (existing.isPresent()) {
            return new Summary(false, existing.get().employeeNo().toLowerCase(Locale.ROOT), every.size(), null);
        }

        UUID centre = org.centres(a).stream().filter(c -> c.code().equals(ClinicSeed.CENTRE_CODE)).map(OrgModels.CentreView::id)
                .findFirst().orElseThrow(() -> new NotFoundException(ClinicSeed.CENTRE_NAME + " not found; run ops/clinic-seed.sh first"));
        UUID position = workforce.positions(a).stream().filter(p -> p.code().equals(POSITION_CODE)).map(p -> p.id()).findFirst()
                .orElseGet(() -> workforce.definePosition(a, new WorkforceModels.DefinePosition(POSITION_CODE,
                        "Demonstration inspector (software provider)", "MANAGEMENT", null)).id());
        var establishment = workforce.establishments(a, centre).stream()
                .filter(e -> e.positionId().equals(position) && e.centreDepartmentId() == null && e.validTo() == null).findFirst()
                .orElseGet(() -> workforce.establish(a, centre, new WorkforceModels.Establish(position, null, 1, null)));
        UUID personId = workforce.createPerson(a, new WorkforceModels.CreatePerson(GIVEN, FAMILY, email), true).id();
        UUID posting = workforce.addPosting(a, personId, new WorkforceModels.AddPosting(establishment.id(), BigDecimal.ONE, null, null)).id();
        IamModels.UserCreated created = users.create(a, new IamModels.CreateUser(personId, null));
        IamModels.AssignmentView requested = assignments.request(a, new IamModels.RequestAssignment("ACCESS", created.user().id(), roleId,
                "TENANT", tenant, posting, null, null, "Developer inspection of the demonstration organisation"));
        assignments.approve(b, requested.id(), new IamModels.Decision("Approved for the demonstration only", requested.version()));
        return new Summary(true, created.user().username(), every.size(), new CredentialsFile.Entry(created.user().username(),
                created.temporaryPassword(), "Demo Inspector, demonstration inspector with every permission, whole organisation (developer use, demo only)"));
    }

    private void approve(Actor a, Actor b, IamModels.RoleVersionView v) {
        IamModels.RoleDetail submitted = roles.submit(a, v.id(), new IamModels.Decision("Ready for review", v.version()));
        int version = submitted.versions().stream().filter(x -> x.id().equals(v.id())).findFirst().orElseThrow().version();
        roles.approve(b, v.id(), new IamModels.Decision("Reviewed: demonstration only", version));
    }
}
