package in.coreeye.coreneha.iam;

import java.time.LocalDate;
import java.util.List;
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
import in.coreeye.coreneha.kernel.ScopeType;
import in.coreeye.coreneha.iam.IamModels.AssignmentView;
import in.coreeye.coreneha.workforce.WorkforceDirectory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Access and authority assignments (IAM-01, IAM-02, IAM-18). A request needs
 * the request permission at the scope AND assignment authority for the role
 * covering the scope. It becomes effective only when someone else, who is
 * neither the requester nor the assignee, approves it. Revocation is immediate.
 */
@Service
public class AssignmentService {

    static final String VIEW_SELECT = """
            select ra.id, ra.kind, ra.user_id, u.username, ra.role_id, r.code as role_code, r.name as role_name,
                   ra.scope_type, ra.scope_id, null as scope_name, ra.posting_id, ra.valid_from, ra.valid_to, ra.status,
                   ra.reason, ra.requested_by, rq.username as requested_by_name, ra.requested_at, ra.decided_by,
                   ra.decided_at, ra.decision_note, ra.via_bootstrap, ra.version
            from iam.role_assignment ra
            join iam.user_account u on u.id = ra.user_id
            left join iam.role r on r.id = ra.role_id
            left join iam.user_account rq on rq.id = ra.requested_by
            """;

    private final JdbcClient jdbc;
    private final AccessControl access;
    private final AccessEvaluator evaluator;
    private final AuditLog audit;
    private final IamSupport support;
    private final WorkforceDirectory workforce;

    AssignmentService(JdbcClient jdbc, AccessControl access, AccessEvaluator evaluator, AuditLog audit, IamSupport support,
                      WorkforceDirectory workforce) {
        this.jdbc = jdbc;
        this.access = access;
        this.evaluator = evaluator;
        this.audit = audit;
        this.support = support;
        this.workforce = workforce;
    }

    @Transactional
    public AssignmentView request(Actor actor, IamModels.RequestAssignment cmd) {
        Scope scope = support.validScope(actor.tenantId(), cmd.scopeType(), cmd.scopeId());
        String action = "iam.assignment.request";
        access.require(actor, Permission.IAM_ASSIGNMENT_REQUEST, scope, action, "user_account", cmd.userId().toString());
        if (actor.userId().equals(cmd.userId())) {
            refuse(actor, action, cmd.userId(), scope, "iam.assignment.self", "You cannot request access or authority for yourself");
        }
        String userStatus = jdbc.sql("select status from iam.user_account where id = :id").param("id", cmd.userId())
                .query(String.class).optional().orElseThrow(() -> new NotFoundException("User not found"));
        if (!"ACTIVE".equals(userStatus)) {
            throw new RuleViolationException("iam.user.disabled", "The user account is disabled");
        }
        boolean authority = "AUTHORITY".equals(cmd.kind());
        if (!authority && cmd.roleId() == null) {
            throw new RuleViolationException("iam.assignment.role-required", "An access assignment needs a role");
        }
        if (cmd.roleId() != null) {
            boolean published = jdbc.sql("""
                    select count(*) from iam.role r join iam.role_version rv on rv.role_id = r.id and rv.status = 'PUBLISHED'
                    where r.id = :r and r.status = 'ACTIVE'
                    """).param("r", cmd.roleId()).query(Integer.class).single() == 1;
            if (!published) {
                throw new RuleViolationException("iam.role.not-published", "The role has no published version");
            }
        }
        if (!holdsAuthority(actor, cmd.roleId(), scope)) {
            refuse(actor, action, cmd.userId(), scope, "iam.assignment.no-authority",
                    "Your assignment authority does not cover this role at this scope");
        }
        LocalDate from = cmd.validFrom() == null ? LocalDate.now() : cmd.validFrom();
        if (cmd.validTo() != null && cmd.validTo().isBefore(from)) {
            throw new RuleViolationException("iam.assignment.dates", "The assignment ends before it starts");
        }
        if (cmd.postingId() != null) {
            var posting = workforce.posting(cmd.postingId()).orElseThrow(() -> new NotFoundException("Posting not found"));
            if (!posting.personId().equals(support.personOfUser(cmd.userId()))) {
                throw new RuleViolationException("iam.assignment.posting-mismatch", "The posting belongs to another person");
            }
            if (!evaluator.covers(scope, Scope.centre(posting.centreId()), actor.tenantId())) {
                throw new RuleViolationException("iam.assignment.posting-outside-scope", "The posting is outside the requested scope");
            }
        }
        UUID id = jdbc.sql("""
                insert into iam.role_assignment (tenant_id, kind, user_id, role_id, scope_type, scope_id, posting_id,
                    valid_from, valid_to, status, reason, requested_by)
                values (:t, :k, :u, :r, :st, :sid, :p, :from, :to, 'PENDING_APPROVAL', :reason, :by) returning id
                """).param("t", actor.tenantId()).param("k", cmd.kind()).param("u", cmd.userId()).param("r", cmd.roleId())
                .param("st", scope.type().name()).param("sid", scope.id()).param("p", cmd.postingId())
                .param("from", from).param("to", cmd.validTo()).param("reason", cmd.reason()).param("by", actor.userId())
                .query(UUID.class).single();
        audit.record(AuditEntry.success(actor, action, "role_assignment", id, scope,
                IamSupport.details("kind", cmd.kind(), "userId", cmd.userId().toString(),
                        "roleId", String.valueOf(cmd.roleId()), "reason", cmd.reason())));
        return view(id);
    }

    @Transactional
    public AssignmentView approve(Actor actor, UUID assignmentId, IamModels.Decision cmd) {
        return decide(actor, assignmentId, cmd, true);
    }

    @Transactional
    public AssignmentView reject(Actor actor, UUID assignmentId, IamModels.Decision cmd) {
        return decide(actor, assignmentId, cmd, false);
    }

    private AssignmentView decide(Actor actor, UUID assignmentId, IamModels.Decision cmd, boolean approve) {
        String action = approve ? "iam.assignment.approve" : "iam.assignment.reject";
        Row row = lockRow(assignmentId);
        access.require(actor, Permission.IAM_ASSIGNMENT_APPROVE, row.scope(), action, "role_assignment", assignmentId.toString());
        if (!"PENDING_APPROVAL".equals(row.status())) {
            throw new RuleViolationException("iam.assignment.not-pending", "Only a pending request can be decided");
        }
        if (actor.userId().equals(row.requestedBy()) || actor.userId().equals(row.userId())) {
            refuse(actor, action, row.userId(), row.scope(), "iam.self-approval",
                    "You cannot decide a request you made or one that gives you access");
        }
        int updated = jdbc.sql("""
                update iam.role_assignment set status = :s, decided_by = :u, decided_at = now(), decision_note = :note,
                       version = version + 1
                where id = :id and status = 'PENDING_APPROVAL' and version = :v
                """).param("s", approve ? "ACTIVE" : "REJECTED").param("u", actor.userId()).param("note", cmd.note())
                .param("id", assignmentId).param("v", cmd.expectedVersion()).update();
        if (updated == 0) {
            throw new ConflictException("iam.assignment.stale", "The request was changed by someone else");
        }
        if (approve) {
            jdbc.sql("update iam.user_account set access_version = access_version + 1 where id = :id")
                    .param("id", row.userId()).update();
            AccessEvaluator.forgetRequestCache();
        }
        audit.record(AuditEntry.success(actor, action, "role_assignment", assignmentId, row.scope(),
                IamSupport.details("userId", row.userId().toString(), "note", cmd.note())));
        return view(assignmentId);
    }

    @Transactional
    public AssignmentView revoke(Actor actor, UUID assignmentId, IamModels.Revoke cmd) {
        Row row = lockRow(assignmentId);
        access.require(actor, Permission.IAM_ASSIGNMENT_REVOKE, row.scope(), "iam.assignment.revoke", "role_assignment",
                assignmentId.toString());
        int updated = jdbc.sql("""
                update iam.role_assignment set status = 'REVOKED', decided_by = coalesce(decided_by, :u),
                       decision_note = :reason, valid_to = greatest(valid_from, least(coalesce(valid_to, current_date), current_date)),
                       version = version + 1
                where id = :id and status in ('ACTIVE', 'PENDING_APPROVAL') and version = :v
                """).param("u", actor.userId()).param("reason", cmd.reason()).param("id", assignmentId)
                .param("v", cmd.expectedVersion()).update();
        if (updated == 0) {
            throw new ConflictException("iam.assignment.stale", "The assignment was changed by someone else or has ended");
        }
        jdbc.sql("update iam.user_account set access_version = access_version + 1 where id = :id").param("id", row.userId()).update();
        AccessEvaluator.forgetRequestCache();
        audit.record(AuditEntry.success(actor, "iam.assignment.revoke", "role_assignment", assignmentId, row.scope(),
                IamSupport.details("userId", row.userId().toString(), "reason", cmd.reason())));
        return view(assignmentId);
    }

    @Transactional(readOnly = true)
    public List<AssignmentView> pendingForMe(Actor actor) {
        return jdbc.sql(VIEW_SELECT + " where ra.status = 'PENDING_APPROVAL' order by ra.requested_at").query(AssignmentView.class)
                .list().stream()
                .filter(a -> !actor.userId().equals(a.requestedBy()) && !actor.userId().equals(a.userId()))
                .filter(a -> access.holds(actor, Permission.IAM_ASSIGNMENT_APPROVE, new Scope(ScopeType.valueOf(a.scopeType()), a.scopeId())))
                .map(this::named).toList();
    }

    @Transactional(readOnly = true)
    public List<AssignmentView> forUser(Actor actor, UUID userId) {
        UUID personId = support.personOfUser(userId);
        if (!support.mayActOnUser(actor, Permission.IAM_ASSIGNMENT_READ, personId) && !actor.userId().equals(userId)) {
            access.require(actor, Permission.IAM_ASSIGNMENT_READ, Scope.tenant(actor.tenantId()), "iam.assignment.list",
                    "user_account", userId.toString());
        }
        return rowsForUser(userId);
    }

    List<AssignmentView> rowsForUser(UUID userId) {
        return jdbc.sql(VIEW_SELECT + " where ra.user_id = :u order by ra.requested_at desc").param("u", userId)
                .query(AssignmentView.class).list().stream().map(this::named).toList();
    }

    /** Authority: an active AUTHORITY assignment of the actor for this role (or all roles) covering the scope. */
    boolean holdsAuthority(Actor actor, UUID roleId, Scope scope) {
        List<Object[]> rows = jdbc.sql("""
                select scope_type, scope_id from iam.role_assignment
                where user_id = :u and kind = 'AUTHORITY' and status = 'ACTIVE'
                  and valid_from <= current_date and (valid_to is null or valid_to >= current_date)
                  and (role_id is null or role_id = :r)
                """).param("u", actor.userId()).param("r", roleId)
                .query((rs, i) -> new Object[] {rs.getString("scope_type"), rs.getObject("scope_id", UUID.class)}).list();
        if (roleId == null) {
            // Delegating authority over all roles needs authority over all roles.
            rows = jdbc.sql("""
                    select scope_type, scope_id from iam.role_assignment
                    where user_id = :u and kind = 'AUTHORITY' and status = 'ACTIVE' and role_id is null
                      and valid_from <= current_date and (valid_to is null or valid_to >= current_date)
                    """).param("u", actor.userId())
                    .query((rs, i) -> new Object[] {rs.getString("scope_type"), rs.getObject("scope_id", UUID.class)}).list();
        }
        return rows.stream().anyMatch(r -> evaluator.covers(new Scope(ScopeType.valueOf((String) r[0]), (UUID) r[1]),
                scope, actor.tenantId()));
    }

    private void refuse(Actor actor, String action, UUID userId, Scope scope, String code, String message) {
        audit.recordIndependently(AuditEntry.denied(actor, action, "user_account", userId.toString(), scope, message));
        throw new RuleViolationException(code, message);
    }

    private record Row(UUID userId, UUID requestedBy, String status, Scope scope) {
    }

    private Row lockRow(UUID id) {
        return jdbc.sql("select user_id, requested_by, status, scope_type, scope_id from iam.role_assignment where id = :id for update")
                .param("id", id)
                .query((rs, i) -> new Row(rs.getObject("user_id", UUID.class), rs.getObject("requested_by", UUID.class),
                        rs.getString("status"),
                        new Scope(ScopeType.valueOf(rs.getString("scope_type")), rs.getObject("scope_id", UUID.class))))
                .optional().orElseThrow(() -> new NotFoundException("Assignment not found"));
    }

    AssignmentView view(UUID id) {
        return jdbc.sql(VIEW_SELECT + " where ra.id = :id").param("id", id).query(AssignmentView.class).optional()
                .map(this::named).orElseThrow(() -> new NotFoundException("Assignment not found"));
    }

    private AssignmentView named(AssignmentView a) {
        return new AssignmentView(a.id(), a.kind(), a.userId(), a.username(), a.roleId(), a.roleCode(), a.roleName(),
                a.scopeType(), a.scopeId(), support.scopeName(a.scopeType(), a.scopeId()), a.postingId(), a.validFrom(),
                a.validTo(), a.status(), a.reason(), a.requestedBy(), a.requestedByName(), a.requestedAt(),
                a.decidedBy(), a.decidedAt(), a.decisionNote(), a.viaBootstrap(), a.version());
    }
}
