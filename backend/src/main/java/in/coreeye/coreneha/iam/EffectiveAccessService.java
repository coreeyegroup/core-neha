package in.coreeye.coreneha.iam;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import in.coreeye.coreneha.audit.AuditEntry;
import in.coreeye.coreneha.audit.AuditLog;
import in.coreeye.coreneha.kernel.AccessControl;
import in.coreeye.coreneha.kernel.Actor;
import in.coreeye.coreneha.kernel.NotFoundException;
import in.coreeye.coreneha.kernel.Permission;
import in.coreeye.coreneha.kernel.Scope;
import in.coreeye.coreneha.kernel.ScopeType;
import in.coreeye.coreneha.iam.IamModels.AssignmentView;
import in.coreeye.coreneha.iam.IamModels.CentreAccess;
import in.coreeye.coreneha.iam.IamModels.EffectiveAccess;
import in.coreeye.coreneha.iam.IamModels.Explanation;
import in.coreeye.coreneha.iam.IamModels.Me;
import in.coreeye.coreneha.iam.IamModels.PermissionGrant;
import in.coreeye.coreneha.iam.IamModels.ScopeGrant;
import in.coreeye.coreneha.iam.IamModels.UserView;
import in.coreeye.coreneha.org.OrgDirectory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Effective-access preview and explanation (IAM-21), and the signed-in user's own context (/me). */
@Service
public class EffectiveAccessService {

    private final JdbcClient jdbc;
    private final AccessControl access;
    private final AccessEvaluator evaluator;
    private final AuditLog audit;
    private final IamSupport support;
    private final UserService users;
    private final AssignmentService assignments;
    private final ResponsibilityService responsibilities;
    private final OrgDirectory org;

    EffectiveAccessService(JdbcClient jdbc, AccessControl access, AccessEvaluator evaluator, AuditLog audit,
                           IamSupport support, UserService users, AssignmentService assignments,
                           ResponsibilityService responsibilities, OrgDirectory org) {
        this.jdbc = jdbc;
        this.access = access;
        this.evaluator = evaluator;
        this.audit = audit;
        this.support = support;
        this.users = users;
        this.assignments = assignments;
        this.responsibilities = responsibilities;
        this.org = org;
    }

    @Transactional(readOnly = true)
    public EffectiveAccess forUser(Actor actor, UUID userId) {
        UserView user = users.row(userId);
        checkMaySee(actor, user, "iam.access.preview");
        Map<String, PermissionGrant> byPermission = new LinkedHashMap<>();
        for (AccessEvaluator.Grant g : user.status().equals("ACTIVE") ? evaluator.grants(userId) : List.<AccessEvaluator.Grant>of()) {
            Permission p = Permission.fromCode(g.permission()).orElse(null);
            if (p == null) {
                continue;
            }
            boolean effective = !p.tenantOnly() || g.scopeType() == ScopeType.TENANT;
            if (!effective) {
                continue;
            }
            PermissionGrant pg = byPermission.computeIfAbsent(p.code(),
                    c -> new PermissionGrant(c, describe(c), p.tenantOnly(), new ArrayList<>()));
            pg.scopes().add(new ScopeGrant(g.scopeType().name(), g.scopeId(), support.scopeName(g.scopeType().name(), g.scopeId()),
                    g.roleCode(), g.assignmentId()));
        }
        List<String> notes = new ArrayList<>();
        if (!"ACTIVE".equals(user.status())) {
            notes.add("The account is disabled: no permission is effective.");
        }
        notes.add("Responsibilities are listed for information only; they never grant access.");
        notes.add("Only ACTIVE access assignments of roles with a PUBLISHED version count. Pending, rejected, revoked and expired assignments grant nothing.");
        audit.recordIndependently(AuditEntry.success(actor, "iam.access.preview", "user_account", userId, null, Map.of()));
        return new EffectiveAccess(user, assignments.rowsForUser(userId), new ArrayList<>(byPermission.values()),
                responsibilities.forPersonRows(user.personId()), notes);
    }

    @Transactional(readOnly = true)
    public Explanation explain(Actor actor, UUID userId, String permissionCode, String scopeType, UUID scopeId) {
        UserView user = users.row(userId);
        checkMaySee(actor, user, "iam.access.explain");
        Permission p = Permission.fromCode(permissionCode).orElseThrow(() -> new NotFoundException("Unknown permission"));
        Scope target = new Scope(ScopeType.valueOf(scopeType), scopeId);
        List<String> reasons = new ArrayList<>();
        boolean allowed = false;
        if (!"ACTIVE".equals(user.status())) {
            reasons.add("Refused: the account is disabled.");
        } else {
            List<AccessEvaluator.Grant> withPermission = evaluator.grants(userId).stream()
                    .filter(g -> g.permission().equals(p.code())).toList();
            if (withPermission.isEmpty()) {
                reasons.add("Refused: no active assignment of a published role grants " + p.code() + ".");
            }
            for (AccessEvaluator.Grant g : withPermission) {
                String where = g.scopeType() + " " + support.scopeName(g.scopeType().name(), g.scopeId());
                if (p.tenantOnly() && g.scopeType() != ScopeType.TENANT) {
                    reasons.add("Not counted: role " + g.roleCode() + " grants it at " + where
                            + ", but this permission is effective only from a tenant-wide assignment.");
                } else if (evaluator.covers(new Scope(g.scopeType(), g.scopeId()), target, actor.tenantId())) {
                    allowed = true;
                    reasons.add("Allowed: role " + g.roleCode() + " at " + where + " covers the requested scope.");
                } else {
                    reasons.add("Not counted: role " + g.roleCode() + " at " + where + " does not cover the requested scope.");
                }
            }
            long pending = jdbc.sql("select count(*) from iam.role_assignment where user_id = :u and status = 'PENDING_APPROVAL'")
                    .param("u", userId).query(Long.class).single();
            if (pending > 0) {
                reasons.add(pending + " pending request(s) are not effective until approved.");
            }
        }
        audit.recordIndependently(AuditEntry.success(actor, "iam.access.explain", "user_account", userId, target,
                Map.of("permission", p.code(), "allowed", allowed)));
        return new Explanation(allowed, p.code(), scopeType, scopeId, reasons);
    }

    @Transactional(readOnly = true)
    public Me me(Actor actor) {
        UserView user = users.row(actor.userId());
        var tenant = jdbc.sql("select name, is_demo from org.tenant where id = :id").param("id", actor.tenantId())
                .query((rs, i) -> new Object[] {rs.getString("name"), rs.getBoolean("is_demo")}).single();
        List<String> tenantPermissions = new ArrayList<>();
        for (Permission p : Permission.values()) {
            if (access.holds(actor, p, Scope.tenant(actor.tenantId()))) {
                tenantPermissions.add(p.code());
            }
        }
        List<CentreAccess> centres = new ArrayList<>();
        for (OrgDirectory.CentreRef c : org.allCentres()) {
            if (!"ACTIVE".equals(c.status())) {
                continue;
            }
            List<String> perms = new ArrayList<>();
            for (Permission p : Permission.values()) {
                if (!p.tenantOnly() && access.holds(actor, p, Scope.centre(c.id()))) {
                    perms.add(p.code());
                }
            }
            if (!perms.isEmpty()) {
                centres.add(new CentreAccess(c.id(), c.code(), c.name(), perms));
            }
        }
        int pending = assignments.pendingForMe(actor).size();
        List<IamModels.MyPosting> postings = support.postingsOf(user.personId()).stream()
                .map(p -> new IamModels.MyPosting(p.centreId(), org.centre(p.centreId()).map(OrgDirectory.CentreRef::name).orElse(""),
                        p.positionTitle(), p.departmentName()))
                .toList();
        return new Me(actor.userId(), actor.username(), user.personName(), actor.tenantId(), (String) tenant[0],
                (Boolean) tenant[1], tenantPermissions, centres, pending, responsibilities.forPersonRows(user.personId()), postings);
    }

    private void checkMaySee(Actor actor, UserView user, String action) {
        if (!actor.userId().equals(user.id()) && !support.mayActOnUser(actor, Permission.IAM_ASSIGNMENT_READ, user.personId())) {
            access.require(actor, Permission.IAM_ASSIGNMENT_READ, Scope.tenant(actor.tenantId()), action, "user_account",
                    user.id().toString());
        }
    }

    private String describe(String code) {
        return jdbc.sql("select description from iam.permission where code = :c").param("c", code).query(String.class)
                .optional().orElse(code);
    }

    List<AssignmentView> assignmentsOf(UUID userId) {
        return assignments.rowsForUser(userId);
    }
}
