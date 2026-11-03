package in.coreeye.coreneha.audit;

import java.sql.Timestamp;
import java.util.Map;

import org.slf4j.MDC;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes audit events. {@link #record} must run inside the transaction of the
 * change it describes, so a failed audit write rolls the change back.
 * {@link #recordIndependently} commits on its own (refusals, audited reads).
 */
@Service
public class AuditLog {

    private static final String INSERT = """
            insert into audit.audit_event (tenant_id, occurred_at, actor_user_id, actor_label, action, object_type,
                object_id, scope_type, scope_id, outcome, reason, request_id, details)
            values (:tenantId, :occurredAt, :actorUserId, :actorLabel, :action, :objectType,
                :objectId, :scopeType, :scopeId, :outcome, :reason, :requestId, cast(:details as jsonb))
            """;

    private final JdbcClient jdbc;
    private final TransactionTemplate independent;
    private final JsonMapper json;

    AuditLog(JdbcClient jdbc, PlatformTransactionManager transactionManager, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
        this.independent = new TransactionTemplate(transactionManager);
        this.independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Records within the caller's transaction (required). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditEntry entry) {
        insert(entry);
    }

    /** Records in a separate transaction that commits even if the caller's work fails. */
    public void recordIndependently(AuditEntry entry) {
        independent.executeWithoutResult(status -> insert(entry));
    }

    private void insert(AuditEntry e) {
        Map<String, Object> details = e.details() == null ? Map.of() : e.details();
        jdbc.sql(INSERT)
                .param("tenantId", e.tenantId())
                .param("occurredAt", Timestamp.from(e.occurredAt()))
                .param("actorUserId", e.actorUserId())
                .param("actorLabel", e.actorLabel())
                .param("action", e.action())
                .param("objectType", e.objectType())
                .param("objectId", e.objectId())
                .param("scopeType", e.scope() == null ? null : e.scope().type().name())
                .param("scopeId", e.scope() == null ? null : e.scope().id())
                .param("outcome", e.outcome().name())
                .param("reason", e.reason())
                .param("requestId", MDC.get("request_id"))
                .param("details", json.writeValueAsString(details))
                .update();
    }
}
