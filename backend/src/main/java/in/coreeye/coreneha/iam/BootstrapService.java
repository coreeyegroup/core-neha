package in.coreeye.coreneha.iam;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import in.coreeye.coreneha.audit.AuditEntry;
import in.coreeye.coreneha.audit.AuditLog;
import in.coreeye.coreneha.kernel.ConflictException;
import in.coreeye.coreneha.kernel.Permission;
import in.coreeye.coreneha.kernel.RuleViolationException;
import in.coreeye.coreneha.kernel.Scope;
import in.coreeye.coreneha.kernel.TenantContext;
import in.coreeye.coreneha.workforce.WorkforceModels;
import in.coreeye.coreneha.workforce.WorkforceService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Controlled first-administrator bootstrap (ADR-0009). Creates a tenant with
 * exactly two administrators so that every later access expansion can be
 * approved by someone other than its requester. Runs once per tenant; all
 * later assignments follow the normal request and approval rules.
 */
@Service
public class BootstrapService {

    public static final String SYSTEM_ROLE_CODE = "SYSTEM_ADMINISTRATOR";

    /** {@code subject} null means: create the login account in the identity provider now. */
    public record Administrator(String givenName, String familyName, String email, String username, String subject) {
    }

    public record Credential(String username, String temporaryPassword) {
    }

    public record Result(UUID tenantId, UUID systemRoleId, List<UUID> userIds, List<Credential> credentials) {
    }

    private final JdbcClient jdbc;
    private final AuditLog audit;
    private final RoleService roles;
    private final UserService users;
    private final WorkforceService workforce;
    private final TransactionTemplate tx;

    BootstrapService(JdbcClient jdbc, AuditLog audit, RoleService roles, UserService users, WorkforceService workforce,
                     PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.roles = roles;
        this.users = users;
        this.workforce = workforce;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public Result bootstrap(String tenantCode, String tenantName, boolean demo, List<Administrator> admins) {
        if (admins == null || admins.size() != 2) {
            throw new RuleViolationException("bootstrap.two-administrators",
                    "Bootstrap needs exactly two administrators, so neither can approve their own access");
        }
        if (admins.get(0).email().equalsIgnoreCase(admins.get(1).email())
                || admins.get(0).username().equals(admins.get(1).username())) {
            throw new RuleViolationException("bootstrap.distinct-administrators", "The two administrators must be different people");
        }
        boolean exists = jdbc.sql("select exists(select 1 from iam.login_identity li where li.subject in (:a, :b))")
                .param("a", String.valueOf(admins.get(0).subject())).param("b", String.valueOf(admins.get(1).subject()))
                .query(Boolean.class).single();
        if (exists) {
            throw new ConflictException("bootstrap.identity-in-use", "A login identity is already linked to another account");
        }
        UUID tenantId = UUID.randomUUID();
        return TenantContext.callAs(tenantId, () -> tx.execute(status -> run(tenantId, tenantCode, tenantName, demo, admins)));
    }

    private Result run(UUID tenantId, String code, String name, boolean demo, List<Administrator> admins) {
        try {
            jdbc.sql("insert into org.tenant (id, code, name, is_demo) values (:id, :c, :n, :d)")
                    .param("id", tenantId).param("c", code).param("n", name).param("d", demo).update();
        } catch (DuplicateKeyException e) {
            throw new ConflictException("bootstrap.tenant-exists", "A tenant with this code already exists; bootstrap runs only once");
        }
        Scope tenantScope = Scope.tenant(tenantId);
        audit.record(AuditEntry.system(tenantId, "bootstrap", "org.tenant.create", "tenant", tenantId, tenantScope,
                Map.of("code", code, "demo", demo)));

        UUID roleId = roles.insertRole(tenantId, SYSTEM_ROLE_CODE, "System administrator",
                "Created by bootstrap. Configures organisation, workforce and access; all changes still need independent approval.", true);
        UUID versionId = roles.insertVersion(tenantId, roleId, 1, "DRAFT", "Bootstrap version", null);
        roles.setGrants(tenantId, versionId, Arrays.stream(Permission.values()).filter(Permission::administrative).map(Permission::code).toList());
        jdbc.sql("update iam.role_version set status = 'PUBLISHED', decided_at = now(), decision_note = 'Published by bootstrap' where id = :id")
                .param("id", versionId).update();
        audit.record(AuditEntry.system(tenantId, "bootstrap", "iam.role.bootstrap-publish", "role", roleId, tenantScope,
                Map.of("code", SYSTEM_ROLE_CODE, "permissions", Arrays.stream(Permission.values()).filter(Permission::administrative).count())));

        List<UUID> userIds = new ArrayList<>();
        List<Credential> credentials = new ArrayList<>();
        for (Administrator a : admins) {
            var person = workforce.insertPerson(tenantId,
                    new WorkforceModels.CreatePerson(a.givenName(), a.familyName(), a.email()), demo, null);
            UUID userId;
            if (a.subject() == null) {
                var created = users.createForLoader(tenantId, person.id(), a.username(), a.email(), a.givenName(),
                        a.familyName(), demo);
                userId = created.user().id();
                credentials.add(new Credential(a.username(), created.temporaryPassword()));
            } else {
                userId = users.insert(tenantId, person.id(), a.username());
                users.linkIdentity(tenantId, userId, a.subject());
            }
            for (String kind : List.of("ACCESS", "AUTHORITY")) {
                UUID assignmentId = jdbc.sql("""
                        insert into iam.role_assignment (tenant_id, kind, user_id, role_id, scope_type, scope_id, valid_from,
                            status, reason, via_bootstrap)
                        values (:t, :k, :u, :r, 'TENANT', :t, :from, 'ACTIVE', 'Initial administrator (bootstrap)', true)
                        returning id
                        """).param("t", tenantId).param("k", kind).param("u", userId)
                        .param("r", "ACCESS".equals(kind) ? roleId : null).param("from", LocalDate.now())
                        .query(UUID.class).single();
                audit.record(AuditEntry.system(tenantId, "bootstrap", "iam.assignment.bootstrap", "role_assignment",
                        assignmentId, tenantScope, Map.of("kind", kind, "username", a.username())));
            }
            userIds.add(userId);
        }
        return new Result(tenantId, roleId, userIds, credentials);
    }
}
