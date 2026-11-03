package in.coreeye.coreneha.iam;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import in.coreeye.coreneha.kernel.Actor;
import in.coreeye.coreneha.kernel.NotFoundException;
import in.coreeye.coreneha.kernel.Permission;
import in.coreeye.coreneha.kernel.RuleViolationException;
import in.coreeye.coreneha.kernel.Scope;
import in.coreeye.coreneha.kernel.ScopeType;
import in.coreeye.coreneha.org.OrgDirectory;
import in.coreeye.coreneha.workforce.WorkforceDirectory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Shared helpers: scope validation and naming, the "may this actor see this user" rule, session revocation. */
@Component
class IamSupport {

    private final JdbcClient jdbc;
    private final OrgDirectory org;
    private final WorkforceDirectory workforce;
    private final AccessEvaluator access;

    IamSupport(JdbcClient jdbc, OrgDirectory org, WorkforceDirectory workforce, AccessEvaluator access) {
        this.jdbc = jdbc;
        this.org = org;
        this.workforce = workforce;
        this.access = access;
    }

    /** Validates that the scope exists in the actor's tenant and is usable. */
    Scope validScope(UUID tenantId, String scopeType, UUID scopeId) {
        ScopeType type = ScopeType.valueOf(scopeType);
        switch (type) {
            case TENANT -> {
                if (!tenantId.equals(scopeId)) {
                    throw new NotFoundException("Tenant scope must be your own tenant");
                }
            }
            case ORG_NODE -> org.node(scopeId).orElseThrow(() -> new NotFoundException("Hierarchy node not found"));
            case CENTRE -> {
                var c = org.centre(scopeId).orElseThrow(() -> new NotFoundException("Centre not found"));
                if (!"ACTIVE".equals(c.status())) {
                    throw new RuleViolationException("org.centre.retired", "The centre is retired");
                }
            }
        }
        return new Scope(type, scopeId);
    }

    String scopeName(String scopeType, UUID scopeId) {
        return switch (ScopeType.valueOf(scopeType)) {
            case TENANT -> jdbc.sql("select name from org.tenant where id = :id").param("id", scopeId)
                    .query(String.class).optional().orElse("tenant");
            case ORG_NODE -> org.node(scopeId).map(OrgDirectory.NodeRef::name).orElse("(unknown node)");
            case CENTRE -> org.centre(scopeId).map(OrgDirectory.CentreRef::name).orElse("(unknown centre)");
        };
    }

    /**
     * An actor may see or manage a user's access with the given permission at tenant
     * scope, or at a centre where that user's person currently holds a posting.
     */
    boolean mayActOnUser(Actor actor, Permission permission, UUID personId) {
        if (access.holds(actor, permission, Scope.tenant(actor.tenantId()))) {
            return true;
        }
        return workforce.activePostings(personId).stream()
                .anyMatch(p -> access.holds(actor, permission, Scope.centre(p.centreId())));
    }

    UUID personOfUser(UUID userId) {
        return jdbc.sql("select person_id from iam.user_account where id = :id").param("id", userId)
                .query(UUID.class).optional().orElseThrow(() -> new NotFoundException("User not found"));
    }

    /** Ends every server-side session of the user and marks their access as changed. */
    int endSessionsAndBumpAccess(UUID userId) {
        jdbc.sql("update iam.user_account set access_version = access_version + 1 where id = :id").param("id", userId).update();
        AccessEvaluator.forgetRequestCache();
        return jdbc.sql("""
                delete from session.spring_session where principal_name in
                  (select subject from iam.login_identity where user_id = :id)
                """).param("id", userId).update();
    }

    static Map<String, Object> details(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1] == null ? "" : kv[i + 1]);
        }
        return m;
    }
}
