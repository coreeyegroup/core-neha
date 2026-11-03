package in.coreeye.coreneha.iam;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import in.coreeye.coreneha.audit.AuditEntry;
import in.coreeye.coreneha.audit.AuditLog;
import in.coreeye.coreneha.kernel.AccessControl;
import in.coreeye.coreneha.kernel.Actor;
import in.coreeye.coreneha.kernel.ConflictException;
import in.coreeye.coreneha.kernel.NotFoundException;
import in.coreeye.coreneha.kernel.Permission;
import in.coreeye.coreneha.kernel.RuleViolationException;
import in.coreeye.coreneha.kernel.Scope;
import in.coreeye.coreneha.iam.IamModels.UserView;
import in.coreeye.coreneha.workforce.WorkforceDirectory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** User accounts (IAM-01) and their immediate disabling (IAM-04). */
@Service
public class UserService {

    static final String USER_SELECT = """
            select u.id, u.person_id, p.given_name || ' ' || p.family_name as person_name, u.username, u.status,
                   u.access_version, exists(select 1 from iam.login_identity li where li.user_id = u.id) as login_linked,
                   u.version
            from iam.user_account u join workforce.person p on p.id = u.person_id
            """;

    private final JdbcClient jdbc;
    private final AccessControl access;
    private final AuditLog audit;
    private final IamSupport support;
    private final WorkforceDirectory workforce;

    UserService(JdbcClient jdbc, AccessControl access, AuditLog audit, IamSupport support, WorkforceDirectory workforce) {
        this.jdbc = jdbc;
        this.access = access;
        this.audit = audit;
        this.support = support;
        this.workforce = workforce;
    }

    @Transactional
    public UserView create(Actor actor, IamModels.CreateUser cmd) {
        workforce.person(cmd.personId()).orElseThrow(() -> new NotFoundException("Person not found"));
        if (!support.mayActOnUser(actor, Permission.IAM_USER_MANAGE, cmd.personId())) {
            access.require(actor, Permission.IAM_USER_MANAGE, Scope.tenant(actor.tenantId()), "iam.user.create", "person",
                    cmd.personId().toString());
        }
        UUID id = insert(actor.tenantId(), cmd.personId(), cmd.username());
        audit.record(AuditEntry.success(actor, "iam.user.create", "user_account", id, null,
                IamSupport.details("personId", cmd.personId().toString(), "username", cmd.username())));
        return row(id);
    }

    UUID insert(UUID tenantId, UUID personId, String username) {
        try {
            return jdbc.sql("""
                    insert into iam.user_account (tenant_id, person_id, username) values (:t, :p, :u) returning id
                    """).param("t", tenantId).param("p", personId).param("u", username).query(UUID.class).single();
        } catch (DuplicateKeyException e) {
            throw new ConflictException("duplicate", "This username is taken or the person already has an account");
        }
    }

    /** Links the identity-provider subject to the account (called by provisioning and bootstrap). */
    void linkIdentity(UUID tenantId, UUID userId, String subject) {
        try {
            jdbc.sql("insert into iam.login_identity (subject, user_id, tenant_id) values (:s, :u, :t)")
                    .param("s", subject).param("u", userId).param("t", tenantId).update();
        } catch (DuplicateKeyException e) {
            throw new ConflictException("duplicate", "This login identity is already linked");
        }
    }

    @Transactional
    public UserView disable(Actor actor, UUID userId, IamModels.ChangeUserStatus cmd) {
        UUID personId = support.personOfUser(userId);
        if (!support.mayActOnUser(actor, Permission.IAM_USER_MANAGE, personId)) {
            access.require(actor, Permission.IAM_USER_MANAGE, Scope.tenant(actor.tenantId()), "iam.user.disable",
                    "user_account", userId.toString());
        }
        if (actor.userId().equals(userId)) {
            throw new RuleViolationException("iam.user.self-disable", "You cannot disable your own account");
        }
        int updated = jdbc.sql("""
                update iam.user_account set status = 'DISABLED', disabled_at = now(), version = version + 1
                where id = :id and status = 'ACTIVE' and version = :v
                """).param("id", userId).param("v", cmd.expectedVersion()).update();
        if (updated == 0) {
            throw new ConflictException("iam.user.stale", "The account was changed by someone else or is already disabled");
        }
        int sessions = support.endSessionsAndBumpAccess(userId);
        audit.record(AuditEntry.success(actor, "iam.user.disable", "user_account", userId, null,
                IamSupport.details("reason", cmd.reason(), "sessionsEnded", sessions)));
        return row(userId);
    }

    @Transactional
    public UserView enable(Actor actor, UUID userId, IamModels.ChangeUserStatus cmd) {
        UUID personId = support.personOfUser(userId);
        if (!support.mayActOnUser(actor, Permission.IAM_USER_MANAGE, personId)) {
            access.require(actor, Permission.IAM_USER_MANAGE, Scope.tenant(actor.tenantId()), "iam.user.enable",
                    "user_account", userId.toString());
        }
        int updated = jdbc.sql("""
                update iam.user_account set status = 'ACTIVE', disabled_at = null, version = version + 1,
                       access_version = access_version + 1
                where id = :id and status = 'DISABLED' and version = :v
                """).param("id", userId).param("v", cmd.expectedVersion()).update();
        if (updated == 0) {
            throw new ConflictException("iam.user.stale", "The account was changed by someone else or is already active");
        }
        audit.record(AuditEntry.success(actor, "iam.user.enable", "user_account", userId, null,
                IamSupport.details("reason", cmd.reason())));
        return row(userId);
    }

    @Transactional(readOnly = true)
    public List<UserView> list(Actor actor) {
        List<UserView> all = jdbc.sql(USER_SELECT + " order by u.username").query(UserView.class).list();
        if (access.holds(actor, Permission.IAM_ASSIGNMENT_READ, Scope.tenant(actor.tenantId()))) {
            return all;
        }
        Set<UUID> centres = access.centresWhere(actor, Permission.IAM_ASSIGNMENT_READ);
        if (centres.isEmpty()) {
            access.requireAnywhere(actor, Permission.IAM_ASSIGNMENT_READ, "iam.user.list", "user_account");
        }
        return all.stream().filter(u -> workforce.activePostings(u.personId()).stream()
                .anyMatch(p -> centres.contains(p.centreId()))).toList();
    }

    UserView row(UUID id) {
        return jdbc.sql(USER_SELECT + " where u.id = :id").param("id", id).query(UserView.class).optional()
                .orElseThrow(() -> new NotFoundException("User not found"));
    }
}
