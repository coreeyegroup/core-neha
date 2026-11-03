package in.coreeye.coreneha.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import in.coreeye.coreneha.kernel.Actor;
import in.coreeye.coreneha.kernel.Scope;

/**
 * One audit event: who did what to which object, where, with what outcome.
 * {@code details} carries before/after values or other context (no secrets).
 */
public record AuditEntry(
        UUID tenantId,
        UUID actorUserId,
        String actorLabel,
        String action,
        String objectType,
        String objectId,
        Scope scope,
        AuditOutcome outcome,
        String reason,
        Instant occurredAt,
        Map<String, Object> details) {

    public static AuditEntry success(Actor actor, String action, String objectType, Object objectId, Scope scope,
                                     Map<String, Object> details) {
        return new AuditEntry(actor.tenantId(), actor.userId(), actor.username(), action, objectType,
                objectId == null ? null : objectId.toString(), scope, AuditOutcome.SUCCESS, null, Instant.now(), details);
    }

    public static AuditEntry denied(Actor actor, String action, String objectType, String objectId, Scope scope,
                                    String reason) {
        return new AuditEntry(actor.tenantId(), actor.userId(), actor.username(), action, objectType, objectId,
                scope, AuditOutcome.DENIED, reason, Instant.now(), Map.of());
    }

    public static AuditEntry system(UUID tenantId, String label, String action, String objectType, Object objectId,
                                    Scope scope, Map<String, Object> details) {
        return new AuditEntry(tenantId, null, label, action, objectType, objectId == null ? null : objectId.toString(),
                scope, AuditOutcome.SUCCESS, null, Instant.now(), details);
    }
}
