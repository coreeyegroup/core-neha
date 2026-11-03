package in.coreeye.coreneha.kernel;

import java.util.Set;
import java.util.UUID;

/**
 * The only way modules check access. Implemented by the iam module from the
 * actor's active, approved assignments of published role versions. There is
 * no "administrator can do everything" path (ADR-0006).
 */
public interface AccessControl {

    /**
     * Requires the permission at the given scope. If it is not held, the refusal
     * is audited (in its own transaction) and {@link AccessDeniedException} is thrown.
     */
    void require(Actor actor, Permission permission, Scope scope, String action, String objectType, String objectId);

    /** Requires the permission at any scope (for tenant-wide catalogues such as positions). */
    void requireAnywhere(Actor actor, Permission permission, String action, String objectType);

    /** True if the actor holds the permission at the scope. No audit. */
    boolean holds(Actor actor, Permission permission, Scope scope);

    /** Centres (of the actor's tenant) where the actor holds the permission. */
    Set<UUID> centresWhere(Actor actor, Permission permission);
}
