package in.coreeye.coreneha.kernel;

import java.util.Objects;
import java.util.UUID;

/** A scope: the whole tenant, a hierarchy node (and everything below it) or one centre. */
public record Scope(ScopeType type, UUID id) {

    public Scope {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(id, "id");
    }

    public static Scope tenant(UUID tenantId) {
        return new Scope(ScopeType.TENANT, tenantId);
    }

    public static Scope node(UUID nodeId) {
        return new Scope(ScopeType.ORG_NODE, nodeId);
    }

    public static Scope centre(UUID centreId) {
        return new Scope(ScopeType.CENTRE, centreId);
    }
}
