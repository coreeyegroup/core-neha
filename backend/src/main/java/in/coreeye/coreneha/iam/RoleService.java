package in.coreeye.coreneha.iam;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
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
import in.coreeye.coreneha.iam.IamModels.PermissionView;
import in.coreeye.coreneha.iam.IamModels.RoleDetail;
import in.coreeye.coreneha.iam.IamModels.RoleSummary;
import in.coreeye.coreneha.iam.IamModels.RoleVersionView;
import in.coreeye.coreneha.iam.IamModels.UserView;
import in.coreeye.coreneha.iam.IamModels.VersionImpact;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Roles and role versions (IAM-12, IAM-13, IAM-14). A role grants permissions
 * only through its published version; every version is drafted, submitted and
 * approved by someone other than its submitter and author.
 */
@Service
public class RoleService {

    private static final String SUMMARY_SELECT = """
            select r.id, r.code, r.name, r.description, r.is_system as "system", r.status,
                   (select rv.version_no from iam.role_version rv where rv.role_id = r.id and rv.status = 'PUBLISHED') as published_version,
                   (select rv.version_no from iam.role_version rv where rv.role_id = r.id and rv.status in ('DRAFT','PENDING_APPROVAL')) as open_version,
                   (select rv.status from iam.role_version rv where rv.role_id = r.id and rv.status in ('DRAFT','PENDING_APPROVAL')) as open_version_status,
                   r.version
            from iam.role r
            """;

    private final JdbcClient jdbc;
    private final AccessControl access;
    private final AuditLog audit;

    RoleService(JdbcClient jdbc, AccessControl access, AuditLog audit) {
        this.jdbc = jdbc;
        this.access = access;
        this.audit = audit;
    }

    private Scope tenant(Actor actor) {
        return Scope.tenant(actor.tenantId());
    }

    @Transactional(readOnly = true)
    public List<PermissionView> permissions(Actor actor) {
        access.require(actor, Permission.IAM_ROLE_READ, tenant(actor), "iam.permission.list", "permission", null);
        return jdbc.sql("select code, module, description, tenant_only from iam.permission order by module, code")
                .query(PermissionView.class).list();
    }

    @Transactional(readOnly = true)
    public List<RoleSummary> roles(Actor actor) {
        access.require(actor, Permission.IAM_ROLE_READ, tenant(actor), "iam.role.list", "role", null);
        return jdbc.sql(SUMMARY_SELECT + " order by r.name").query(RoleSummary.class).list();
    }

    @Transactional(readOnly = true)
    public RoleDetail role(Actor actor, UUID roleId) {
        access.require(actor, Permission.IAM_ROLE_READ, tenant(actor), "iam.role.read", "role", roleId.toString());
        return detail(roleId);
    }

    @Transactional
    public RoleDetail create(Actor actor, IamModels.CreateRole cmd) {
        access.require(actor, Permission.IAM_ROLE_DRAFT, tenant(actor), "iam.role.create", "role", null);
        UUID roleId = insertRole(actor.tenantId(), cmd.code(), cmd.name(), cmd.description(), false);
        UUID versionId = insertVersion(actor.tenantId(), roleId, 1, "DRAFT", cmd.changeNote(), actor.userId());
        setGrants(actor.tenantId(), versionId, cmd.permissions());
        audit.record(AuditEntry.success(actor, "iam.role.create", "role", roleId, tenant(actor),
                IamSupport.details("code", cmd.code(), "permissions", new TreeSet<>(cmd.permissions()).toString())));
        return detail(roleId);
    }

    @Transactional
    public RoleDetail cloneRole(Actor actor, IamModels.CloneRole cmd) {
        access.require(actor, Permission.IAM_ROLE_DRAFT, tenant(actor), "iam.role.clone", "role", cmd.sourceRoleId().toString());
        UUID sourceVersion = jdbc.sql("select id from iam.role_version where role_id = :r and status = 'PUBLISHED'")
                .param("r", cmd.sourceRoleId()).query(UUID.class).optional()
                .orElseThrow(() -> new RuleViolationException("iam.role.not-published", "Only a published role can be cloned"));
        Set<String> permissions = new TreeSet<>(grantsOf(sourceVersion));
        UUID roleId = insertRole(actor.tenantId(), cmd.code(), cmd.name(), cmd.description(), false);
        UUID versionId = insertVersion(actor.tenantId(), roleId, 1, "DRAFT", "Cloned from role " + cmd.sourceRoleId(), actor.userId());
        setGrants(actor.tenantId(), versionId, permissions);
        audit.record(AuditEntry.success(actor, "iam.role.clone", "role", roleId, tenant(actor),
                IamSupport.details("sourceRoleId", cmd.sourceRoleId().toString(), "code", cmd.code())));
        return detail(roleId);
    }

    @Transactional
    public RoleDetail draftVersion(Actor actor, UUID roleId, IamModels.DraftVersion cmd) {
        access.require(actor, Permission.IAM_ROLE_DRAFT, tenant(actor), "iam.role.version.draft", "role", roleId.toString());
        requireActiveRole(roleId);
        int next = jdbc.sql("select coalesce(max(version_no), 0) + 1 from iam.role_version where role_id = :r")
                .param("r", roleId).query(Integer.class).single();
        UUID versionId;
        try {
            versionId = insertVersion(actor.tenantId(), roleId, next, "DRAFT", cmd.changeNote(), actor.userId());
        } catch (ConflictException e) {
            throw new ConflictException("iam.role.open-version", "This role already has a draft or pending version");
        }
        setGrants(actor.tenantId(), versionId, cmd.permissions());
        audit.record(AuditEntry.success(actor, "iam.role.version.draft", "role_version", versionId, tenant(actor),
                IamSupport.details("roleId", roleId.toString(), "versionNo", next,
                        "permissions", new TreeSet<>(cmd.permissions()).toString())));
        return detail(roleId);
    }

    @Transactional
    public RoleDetail updateDraft(Actor actor, UUID versionId, IamModels.UpdateDraft cmd) {
        access.require(actor, Permission.IAM_ROLE_DRAFT, tenant(actor), "iam.role.version.update", "role_version", versionId.toString());
        UUID roleId = roleOf(versionId);
        int updated = jdbc.sql("""
                update iam.role_version set change_note = :note, version = version + 1
                where id = :id and status = 'DRAFT' and version = :v
                """).param("note", cmd.changeNote()).param("id", versionId).param("v", cmd.expectedVersion()).update();
        if (updated == 0) {
            throw new ConflictException("iam.role.version.stale", "The draft was changed by someone else or is no longer a draft");
        }
        jdbc.sql("delete from iam.role_version_grant where role_version_id = :id").param("id", versionId).update();
        setGrants(actor.tenantId(), versionId, cmd.permissions());
        audit.record(AuditEntry.success(actor, "iam.role.version.update", "role_version", versionId, tenant(actor),
                IamSupport.details("permissions", new TreeSet<>(cmd.permissions()).toString())));
        return detail(roleId);
    }

    @Transactional
    public RoleDetail submit(Actor actor, UUID versionId, IamModels.Decision cmd) {
        access.require(actor, Permission.IAM_ROLE_DRAFT, tenant(actor), "iam.role.version.submit", "role_version", versionId.toString());
        UUID roleId = roleOf(versionId);
        int updated = jdbc.sql("""
                update iam.role_version set status = 'PENDING_APPROVAL', submitted_by = :u, submitted_at = now(),
                       version = version + 1
                where id = :id and status = 'DRAFT' and version = :v
                """).param("u", actor.userId()).param("id", versionId).param("v", cmd.expectedVersion()).update();
        if (updated == 0) {
            throw new ConflictException("iam.role.version.stale", "The draft was changed by someone else or is no longer a draft");
        }
        audit.record(AuditEntry.success(actor, "iam.role.version.submit", "role_version", versionId, tenant(actor),
                IamSupport.details("note", cmd.note())));
        return detail(roleId);
    }

    @Transactional
    public RoleDetail approve(Actor actor, UUID versionId, IamModels.Decision cmd) {
        return decide(actor, versionId, cmd, true);
    }

    @Transactional
    public RoleDetail reject(Actor actor, UUID versionId, IamModels.Decision cmd) {
        return decide(actor, versionId, cmd, false);
    }

    private RoleDetail decide(Actor actor, UUID versionId, IamModels.Decision cmd, boolean approve) {
        String action = approve ? "iam.role.version.approve" : "iam.role.version.reject";
        access.require(actor, Permission.IAM_ROLE_APPROVE, tenant(actor), action, "role_version", versionId.toString());
        var v = jdbc.sql("select role_id, status, created_by, submitted_by from iam.role_version where id = :id for update")
                .param("id", versionId)
                .query((rs, i) -> new Object[] {rs.getObject("role_id", UUID.class), rs.getString("status"),
                        rs.getObject("created_by", UUID.class), rs.getObject("submitted_by", UUID.class)})
                .optional().orElseThrow(() -> new NotFoundException("Role version not found"));
        UUID roleId = (UUID) v[0];
        if (!"PENDING_APPROVAL".equals(v[1])) {
            throw new RuleViolationException("iam.role.version.not-pending", "Only a submitted version can be decided");
        }
        if (actor.userId().equals(v[2]) || actor.userId().equals(v[3])) {
            audit.recordIndependently(AuditEntry.denied(actor, action, "role_version", versionId.toString(), tenant(actor),
                    "self-approval refused: the approver drafted or submitted this version"));
            throw new RuleViolationException("iam.self-approval", "You cannot decide a role version you drafted or submitted");
        }
        if (approve) {
            // The approver must not widen their own access: refuse if they hold this role and the version adds permissions.
            boolean holdsRole = jdbc.sql("select exists(select 1 from iam.role_assignment where role_id = :r and user_id = :u"
                            + " and kind = 'ACCESS' and status in ('ACTIVE', 'PENDING_APPROVAL'))")
                    .param("r", roleId).param("u", actor.userId()).query(Boolean.class).single();
            List<String> added = jdbc.sql("select g.permission_code from iam.role_version_grant g where g.role_version_id = :v"
                            + " except select g.permission_code from iam.role_version_grant g"
                            + " join iam.role_version rv on rv.id = g.role_version_id where rv.role_id = :r and rv.status = 'PUBLISHED'")
                    .param("v", versionId).param("r", roleId).query(String.class).list();
            if (holdsRole && !added.isEmpty()) {
                audit.recordIndependently(AuditEntry.denied(actor, action, "role_version", versionId.toString(), tenant(actor),
                        "self-benefit refused: the approver holds this role and the version adds " + added));
                throw new RuleViolationException("iam.role.version.self-benefit",
                        "You hold this role and this version adds permissions (" + String.join(", ", added)
                                + "); someone who does not hold the role must approve it");
            }
            jdbc.sql("update iam.role_version set status = 'SUPERSEDED', version = version + 1 where role_id = :r and status = 'PUBLISHED'")
                    .param("r", roleId).update();
        }
        int updated = jdbc.sql("""
                update iam.role_version set status = :s, decided_by = :u, decided_at = now(), decision_note = :note,
                       version = version + 1
                where id = :id and status = 'PENDING_APPROVAL' and version = :v
                """).param("s", approve ? "PUBLISHED" : "REJECTED").param("u", actor.userId()).param("note", cmd.note())
                .param("id", versionId).param("v", cmd.expectedVersion()).update();
        if (updated == 0) {
            throw new ConflictException("iam.role.version.stale", "The version was changed by someone else");
        }
        int affected = 0;
        if (approve) {
            affected = jdbc.sql("""
                    update iam.user_account set access_version = access_version + 1 where id in
                      (select user_id from iam.role_assignment where role_id = :r and kind = 'ACCESS' and status = 'ACTIVE')
                    """).param("r", roleId).update();
            AccessEvaluator.forgetRequestCache();
        }
        audit.record(AuditEntry.success(actor, action, "role_version", versionId, tenant(actor),
                IamSupport.details("roleId", roleId.toString(), "note", cmd.note(), "affectedUsers", affected)));
        return detail(roleId);
    }

    @Transactional(readOnly = true)
    public VersionImpact impact(Actor actor, UUID versionId) {
        access.require(actor, Permission.IAM_ROLE_READ, tenant(actor), "iam.role.version.impact", "role_version", versionId.toString());
        UUID roleId = roleOf(versionId);
        Set<String> proposed = new TreeSet<>(grantsOf(versionId));
        Set<String> current = jdbc.sql("select id from iam.role_version where role_id = :r and status = 'PUBLISHED'")
                .param("r", roleId).query(UUID.class).optional().map(id -> (Set<String>) new TreeSet<>(grantsOf(id)))
                .orElse(new TreeSet<>());
        List<String> added = proposed.stream().filter(p -> !current.contains(p)).toList();
        List<String> removed = current.stream().filter(p -> !proposed.contains(p)).toList();
        List<UserView> affected = jdbc.sql(UserService.USER_SELECT + """
                 where u.id in (select user_id from iam.role_assignment where role_id = :r and kind = 'ACCESS' and status = 'ACTIVE')
                 order by u.username
                """).param("r", roleId).query(UserView.class).list();
        return new VersionImpact(versionId, added, removed, affected.size(), affected);
    }

    // ------------------------------------------------------------------ internals (also used by bootstrap)

    UUID insertRole(UUID tenantId, String code, String name, String description, boolean system) {
        try {
            return jdbc.sql("""
                    insert into iam.role (tenant_id, code, name, description, is_system) values (:t, :c, :n, :d, :s) returning id
                    """).param("t", tenantId).param("c", code).param("n", name.trim()).param("d", description)
                    .param("s", system).query(UUID.class).single();
        } catch (DuplicateKeyException e) {
            throw new ConflictException("duplicate", "A role with this code already exists");
        }
    }

    UUID insertVersion(UUID tenantId, UUID roleId, int versionNo, String status, String note, UUID createdBy) {
        try {
            return jdbc.sql("""
                    insert into iam.role_version (tenant_id, role_id, version_no, status, change_note, created_by)
                    values (:t, :r, :n, :s, :note, :by) returning id
                    """).param("t", tenantId).param("r", roleId).param("n", versionNo).param("s", status)
                    .param("note", note).param("by", createdBy).query(UUID.class).single();
        } catch (DuplicateKeyException e) {
            throw new ConflictException("iam.role.open-version", "This role already has a draft or pending version");
        }
    }

    void setGrants(UUID tenantId, UUID versionId, Collection<String> permissions) {
        for (String code : new TreeSet<>(permissions)) {
            if (Permission.fromCode(code).isEmpty()) {
                throw new RuleViolationException("iam.permission.unknown", "Unknown permission: " + code);
            }
            jdbc.sql("insert into iam.role_version_grant (role_version_id, permission_code, tenant_id) values (:v, :p, :t)")
                    .param("v", versionId).param("p", code).param("t", tenantId).update();
        }
    }

    private List<String> grantsOf(UUID versionId) {
        return jdbc.sql("select permission_code from iam.role_version_grant where role_version_id = :v order by permission_code")
                .param("v", versionId).query(String.class).list();
    }

    private UUID roleOf(UUID versionId) {
        return jdbc.sql("select role_id from iam.role_version where id = :id").param("id", versionId).query(UUID.class)
                .optional().orElseThrow(() -> new NotFoundException("Role version not found"));
    }

    private void requireActiveRole(UUID roleId) {
        String status = jdbc.sql("select status from iam.role where id = :id").param("id", roleId).query(String.class)
                .optional().orElseThrow(() -> new NotFoundException("Role not found"));
        if (!"ACTIVE".equals(status)) {
            throw new RuleViolationException("iam.role.retired", "The role is retired");
        }
    }

    private RoleDetail detail(UUID roleId) {
        RoleSummary summary = jdbc.sql(SUMMARY_SELECT + " where r.id = :id").param("id", roleId).query(RoleSummary.class)
                .optional().orElseThrow(() -> new NotFoundException("Role not found"));
        List<RoleVersionView> versions = new ArrayList<>();
        jdbc.sql("""
                select id, role_id, version_no, status, change_note, created_by, submitted_by, submitted_at, decided_by,
                       decided_at, decision_note, version
                from iam.role_version where role_id = :r order by version_no desc
                """).param("r", roleId).query((rs, i) -> new RoleVersionView(
                        rs.getObject("id", UUID.class), rs.getObject("role_id", UUID.class), rs.getInt("version_no"),
                        rs.getString("status"), rs.getString("change_note"), rs.getObject("created_by", UUID.class),
                        rs.getObject("submitted_by", UUID.class), rs.getObject("submitted_at", java.time.OffsetDateTime.class),
                        rs.getObject("decided_by", UUID.class), rs.getObject("decided_at", java.time.OffsetDateTime.class),
                        rs.getString("decision_note"), List.of(), rs.getInt("version")))
                .list()
                .forEach(v -> versions.add(new RoleVersionView(v.id(), v.roleId(), v.versionNo(), v.status(), v.changeNote(),
                        v.createdBy(), v.submittedBy(), v.submittedAt(), v.decidedBy(), v.decidedAt(), v.decisionNote(),
                        grantsOf(v.id()), v.version())));
        return new RoleDetail(summary, versions);
    }
}
