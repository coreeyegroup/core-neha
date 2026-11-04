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
            select u.id, u.person_id, p.given_name || ' ' || p.family_name as person_name, p.employee_no,
                   (select string_agg(pd.title || coalesce(' (' || coalesce(cd.local_name, dd.name) || ', ' || c.code || ')', ' (' || c.code || ')'), '; '
                            order by pd.title)
                    from workforce.posting po join workforce.establishment e on e.id = po.establishment_id
                    join workforce.position_definition pd on pd.id = e.position_id join org.centre c on c.id = e.centre_id
                    left join org.centre_department cd on cd.id = e.centre_department_id
                    left join org.department_definition dd on dd.id = cd.department_id
                    where po.person_id = u.person_id and po.valid_from <= current_date
                      and (po.valid_to is null or po.valid_to >= current_date)) as positions,
                   u.username, u.status,
                   u.access_version, exists(select 1 from iam.login_identity li where li.user_id = u.id) as login_linked,
                   u.mfa_required, u.version
            from iam.user_account u join workforce.person p on p.id = u.person_id
            """;

    private final JdbcClient jdbc;
    private final AccessControl access;
    private final AuditLog audit;
    private final IamSupport support;
    private final WorkforceDirectory workforce;
    private final IdentityProvisioner provisioner;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UserService.class);

    UserService(JdbcClient jdbc, AccessControl access, AuditLog audit, IamSupport support, WorkforceDirectory workforce,
                IdentityProvisioner provisioner) {
        this.provisioner = provisioner;
        this.jdbc = jdbc;
        this.access = access;
        this.audit = audit;
        this.support = support;
        this.workforce = workforce;
    }

    @Transactional
    public IamModels.UserCreated create(Actor actor, IamModels.CreateUser cmd) {
        var person = workforce.person(cmd.personId()).orElseThrow(() -> new NotFoundException("Person not found"));
        if (!support.mayActOnUser(actor, Permission.IAM_USER_MANAGE, cmd.personId())) {
            access.require(actor, Permission.IAM_USER_MANAGE, Scope.tenant(actor.tenantId()), "iam.user.create", "person",
                    cmd.personId().toString());
        }
        boolean demo = Boolean.TRUE.equals(jdbc.sql("select is_demo from workforce.person where id = :id")
                .param("id", cmd.personId()).query(Boolean.class).single());
        String username = loginId(cmd.username(), person.employeeNo());
        UUID id = insert(actor.tenantId(), cmd.personId(), username);
        IdentityProvisioner.ProvisionedIdentity identity =
                provisioner.create(username, person.email(), person.givenName(), person.familyName(), demo);
        try {
            linkIdentity(actor.tenantId(), id, identity.subject());
            audit.record(AuditEntry.success(actor, "iam.user.create", "user_account", id, null,
                    IamSupport.details("personId", cmd.personId().toString(), "username", username,
                            "loginAccountCreated", true, "temporaryPasswordIssued", true)));
        } catch (RuntimeException e) {
            safeDelete(identity.subject());
            throw e;
        }
        return new IamModels.UserCreated(row(id), identity.temporaryPassword(),
                "Give this temporary password to the person privately. At first sign-in they must choose a new password and set up an authenticator app.");
    }

    /** Creates the account and login for bootstrap and demonstration loaders (they audit as the system). */
    IamModels.UserCreated createForLoader(UUID tenantId, UUID personId, String requestedUsername, String email, String givenName,
                                          String familyName, boolean demo) {
        String username = loginId(requestedUsername, workforce.person(personId).map(WorkforceDirectory.PersonRef::employeeNo).orElse(null));
        UUID id = insert(tenantId, personId, username);
        IdentityProvisioner.ProvisionedIdentity identity = provisioner.create(username, email, givenName, familyName, demo);
        try {
            linkIdentity(tenantId, id, identity.subject());
        } catch (RuntimeException e) {
            safeDelete(identity.subject());
            throw e;
        }
        return new IamModels.UserCreated(row(id), identity.temporaryPassword(), null);
    }

    /** The login id: the requested username, or else the employee number in lower case (Keycloak stores usernames in lower case). */
    static String loginId(String requested, String employeeNo) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim();
        }
        if (employeeNo == null) {
            throw new RuleViolationException("iam.user.no-login-id", "Give a username: this person has no employee number");
        }
        return employeeNo.toLowerCase(java.util.Locale.ROOT);
    }

    private void safeDelete(String subject) {
        try {
            provisioner.delete(subject);
        } catch (RuntimeException ex) {
            log.warn("Could not remove login account {} after a failed user creation", subject);
        }
    }

    private String subjectOf(UUID userId) {
        return jdbc.sql("select subject from iam.login_identity where user_id = :id").param("id", userId)
                .query(String.class).optional().orElse(null);
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
        // Access in core-neha has already ended (checked on every request). Also stop new logins at the identity provider.
        boolean loginDisabled = false;
        String subject = subjectOf(userId);
        if (subject != null) {
            try {
                provisioner.disable(subject);
                loginDisabled = true;
            } catch (RuntimeException e) {
                log.warn("Identity provider did not confirm disabling login {}: {}", subject, e.getMessage());
            }
        }
        audit.record(AuditEntry.success(actor, "iam.user.disable", "user_account", userId, null,
                IamSupport.details("reason", cmd.reason(), "sessionsEnded", sessions, "loginDisabled", loginDisabled)));
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
        String subject = subjectOf(userId);
        if (subject != null) {
            provisioner.enable(subject);
        }
        audit.record(AuditEntry.success(actor, "iam.user.enable", "user_account", userId, null,
                IamSupport.details("reason", cmd.reason())));
        return row(userId);
    }

    /**
     * Turns the one-time code (TOTP) at sign-in on or off for one account. Only a group-wide
     * user administrator may do it, never for their own account, always with a reason.
     * Turning it on ends the user's sessions so the next sign-in sets up the authenticator.
     */
    @Transactional
    public UserView changeMfa(Actor actor, UUID userId, IamModels.ChangeMfa cmd) {
        access.require(actor, Permission.IAM_USER_MANAGE, Scope.tenant(actor.tenantId()), "iam.user.mfa",
                "user_account", userId.toString());
        if (actor.userId().equals(userId)) {
            throw new RuleViolationException("iam.user.self-mfa", "You cannot change the sign-in security of your own account");
        }
        UserView before = row(userId);
        int updated = jdbc.sql("update iam.user_account set mfa_required = :r, version = version + 1 where id = :id and version = :v")
                .param("r", cmd.required()).param("id", userId).param("v", cmd.expectedVersion()).update();
        if (updated == 0) {
            throw new ConflictException("iam.user.stale", "The account was changed by someone else");
        }
        String subject = subjectOf(userId);
        if (subject != null) {
            provisioner.setOneTimeCodeRequired(subject, cmd.required());
        }
        int sessions = cmd.required() ? support.endSessionsAndBumpAccess(userId) : 0;
        audit.record(AuditEntry.success(actor, cmd.required() ? "iam.user.mfa.require" : "iam.user.mfa.exempt", "user_account",
                userId, Scope.tenant(actor.tenantId()), IamSupport.details("username", before.username(), "reason", cmd.reason(),
                        "before", before.mfaRequired(), "sessionsEnded", sessions)));
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
