package in.coreeye.coreneha.audit;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import in.coreeye.coreneha.kernel.AccessControl;
import in.coreeye.coreneha.kernel.Actor;
import in.coreeye.coreneha.kernel.Permission;
import in.coreeye.coreneha.kernel.Scope;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Audit search (AUD-02). Tenant-wide holders see all events; centre holders see their centres' events. */
@Service
public class AuditQuery {

    public record Filter(UUID actorUserId, String objectType, String objectId, String action, String outcome,
                         Instant from, Instant to, Long beforeId, Integer limit) {
    }

    public record EventView(long id, OffsetDateTime occurredAt, OffsetDateTime recordedAt, UUID actorUserId,
                            String actorLabel, String action, String objectType, String objectId, String scopeType,
                            UUID scopeId, String outcome, String reason, String requestId, String details) {
    }

    private final JdbcClient jdbc;
    private final AccessControl access;
    private final AuditLog audit;

    AuditQuery(JdbcClient jdbc, AccessControl access, AuditLog audit) {
        this.jdbc = jdbc;
        this.access = access;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<EventView> search(Actor actor, Filter f) {
        boolean tenantWide = access.holds(actor, Permission.AUDIT_READ, Scope.tenant(actor.tenantId()));
        Set<UUID> centres = tenantWide ? Set.of() : access.centresWhere(actor, Permission.AUDIT_READ);
        if (!tenantWide && centres.isEmpty()) {
            access.requireAnywhere(actor, Permission.AUDIT_READ, "audit.search", "audit_event");
        }
        StringBuilder sql = new StringBuilder("""
                select id, occurred_at, recorded_at, actor_user_id, actor_label, action, object_type, object_id,
                       scope_type, scope_id, outcome, reason, request_id, details::text as details
                from audit.audit_event where tenant_id = :tenantId
                """);
        Map<String, Object> p = new HashMap<>();
        p.put("tenantId", actor.tenantId());
        if (!tenantWide) {
            sql.append(" and scope_type = 'CENTRE' and scope_id in (:centres)");
            p.put("centres", new ArrayList<>(centres));
        }
        if (f.actorUserId() != null) { sql.append(" and actor_user_id = :actor"); p.put("actor", f.actorUserId()); }
        if (f.objectType() != null) { sql.append(" and object_type = :objectType"); p.put("objectType", f.objectType()); }
        if (f.objectId() != null) { sql.append(" and object_id = :objectId"); p.put("objectId", f.objectId()); }
        if (f.action() != null) { sql.append(" and action = :action"); p.put("action", f.action()); }
        if (f.outcome() != null) { sql.append(" and outcome = :outcome"); p.put("outcome", f.outcome()); }
        if (f.from() != null) { sql.append(" and occurred_at >= :from"); p.put("from", java.sql.Timestamp.from(f.from())); }
        if (f.to() != null) { sql.append(" and occurred_at < :to"); p.put("to", java.sql.Timestamp.from(f.to())); }
        if (f.beforeId() != null) { sql.append(" and id < :beforeId"); p.put("beforeId", f.beforeId()); }
        int limit = f.limit() == null ? 50 : Math.max(1, Math.min(200, f.limit()));
        sql.append(" order by id desc limit ").append(limit);
        List<EventView> rows = jdbc.sql(sql.toString()).params(p).query(EventView.class).list();
        audit.recordIndependently(AuditEntry.success(actor, "audit.search", "audit_event", null,
                tenantWide ? Scope.tenant(actor.tenantId()) : null, Map.of("results", rows.size())));
        return rows;
    }
}
