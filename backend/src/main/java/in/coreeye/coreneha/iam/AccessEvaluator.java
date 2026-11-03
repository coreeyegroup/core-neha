package in.coreeye.coreneha.iam;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import in.coreeye.coreneha.audit.AuditEntry;
import in.coreeye.coreneha.audit.AuditLog;
import in.coreeye.coreneha.kernel.AccessControl;
import in.coreeye.coreneha.kernel.AccessDeniedException;
import in.coreeye.coreneha.kernel.Actor;
import in.coreeye.coreneha.kernel.Permission;
import in.coreeye.coreneha.kernel.Scope;
import in.coreeye.coreneha.kernel.ScopeType;
import in.coreeye.coreneha.org.OrgDirectory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Evaluates access from active, approved ACCESS assignments of roles with a
 * published version, for an active user. Evaluated on every request (cached
 * only for the duration of one request), so revocation takes effect on the
 * next request. Restrictions win: a permission is either held at a covering
 * scope or refused; nothing else grants access.
 */
@Service
class AccessEvaluator implements AccessControl {

    record Grant(UUID assignmentId, UUID roleId, String roleCode, ScopeType scopeType, UUID scopeId, String permission) {
    }

    static final String GRANTS_SQL = """
            select ra.id as assignment_id, r.id as role_id, r.code as role_code, ra.scope_type, ra.scope_id,
                   g.permission_code as permission
            from iam.role_assignment ra
            join iam.user_account u on u.id = ra.user_id and u.status = 'ACTIVE'
            join iam.role r on r.id = ra.role_id and r.status = 'ACTIVE'
            join iam.role_version rv on rv.role_id = r.id and rv.status = 'PUBLISHED'
            join iam.role_version_grant g on g.role_version_id = rv.id
            where ra.user_id = :u and ra.kind = 'ACCESS' and ra.status = 'ACTIVE'
              and ra.valid_from <= current_date and (ra.valid_to is null or ra.valid_to >= current_date)
            """;

    private final JdbcClient jdbc;
    private final OrgDirectory org;
    private final AuditLog audit;

    AccessEvaluator(JdbcClient jdbc, OrgDirectory org, AuditLog audit) {
        this.jdbc = jdbc;
        this.org = org;
        this.audit = audit;
    }

    @Override
    public void require(Actor actor, Permission permission, Scope scope, String action, String objectType, String objectId) {
        if (!holds(actor, permission, scope)) {
            deny(actor, permission, scope, action, objectType, objectId);
        }
    }

    @Override
    public void requireAnywhere(Actor actor, Permission permission, String action, String objectType) {
        boolean any = grants(actor.userId()).stream()
                .anyMatch(g -> g.permission().equals(permission.code())
                        && (!permission.tenantOnly() || g.scopeType() == ScopeType.TENANT));
        if (!any) {
            deny(actor, permission, null, action, objectType, null);
        }
    }

    @Override
    public boolean holds(Actor actor, Permission permission, Scope scope) {
        return grants(actor.userId()).stream()
                .filter(g -> g.permission().equals(permission.code()))
                .filter(g -> !permission.tenantOnly() || g.scopeType() == ScopeType.TENANT)
                .anyMatch(g -> covers(new Scope(g.scopeType(), g.scopeId()), scope, actor.tenantId()));
    }

    @Override
    public Set<UUID> centresWhere(Actor actor, Permission permission) {
        Set<UUID> result = new HashSet<>();
        for (OrgDirectory.CentreRef c : org.allCentres()) {
            if (holds(actor, permission, Scope.centre(c.id()))) {
                result.add(c.id());
            }
        }
        return result;
    }

    /** True if an assignment at {@code granted} applies to {@code target}. */
    boolean covers(Scope granted, Scope target, UUID tenantId) {
        if (target == null) {
            return granted.type() == ScopeType.TENANT;
        }
        return switch (granted.type()) {
            case TENANT -> granted.id().equals(tenantId);
            case ORG_NODE -> {
                String grantedPath = nodePath(granted.id());
                if (grantedPath == null) {
                    yield false;
                }
                String targetPath = switch (target.type()) {
                    case TENANT -> null;
                    case ORG_NODE -> nodePath(target.id());
                    case CENTRE -> centreNodePath(target.id());
                };
                yield targetPath != null && targetPath.startsWith(grantedPath);
            }
            case CENTRE -> target.type() == ScopeType.CENTRE && target.id().equals(granted.id());
        };
    }

    List<Grant> grants(UUID userId) {
        return cached("iam.grants." + userId, () -> jdbc.sql(GRANTS_SQL).param("u", userId)
                .query((rs, i) -> new Grant(rs.getObject("assignment_id", UUID.class), rs.getObject("role_id", UUID.class),
                        rs.getString("role_code"), ScopeType.valueOf(rs.getString("scope_type")),
                        rs.getObject("scope_id", UUID.class), rs.getString("permission")))
                .list());
    }

    private String nodePath(UUID nodeId) {
        return cached("org.node." + nodeId, () -> org.node(nodeId).map(OrgDirectory.NodeRef::path).orElse(null));
    }

    private String centreNodePath(UUID centreId) {
        return cached("org.centre." + centreId, () -> org.centre(centreId).map(OrgDirectory.CentreRef::nodePath).orElse(null));
    }

    private void deny(Actor actor, Permission permission, Scope scope, String action, String objectType, String objectId) {
        audit.recordIndependently(AuditEntry.denied(actor, action, objectType, objectId, scope,
                "missing permission " + permission.code() + (scope == null ? "" : " at " + scope.type() + " " + scope.id())));
        throw new AccessDeniedException("access.denied",
                "You do not have permission '" + permission.code() + "' for this " + (scope == null ? "tenant" : scope.type().name().toLowerCase()));
    }

    /** Per-request memo; outside a web request (jobs, tests) nothing is cached. */
    @SuppressWarnings("unchecked")
    private static <T> T cached(String key, java.util.function.Supplier<T> loader) {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return loader.get();
        }
        Map<String, Object> memo = (Map<String, Object>) attrs.getAttribute("coreneha.access.memo", RequestAttributes.SCOPE_REQUEST);
        if (memo == null) {
            memo = new HashMap<>();
            attrs.setAttribute("coreneha.access.memo", memo, RequestAttributes.SCOPE_REQUEST);
        }
        if (memo.containsKey(key)) {
            return (T) memo.get(key);
        }
        T value = loader.get();
        memo.put(key, value);
        return value;
    }

    /** Clears the per-request memo after this request changed access (approval, revocation). */
    static void forgetRequestCache() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            attrs.removeAttribute("coreneha.access.memo", RequestAttributes.SCOPE_REQUEST);
        }
    }
}
