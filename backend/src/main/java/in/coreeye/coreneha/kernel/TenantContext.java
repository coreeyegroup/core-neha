package in.coreeye.coreneha.kernel;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Tenant of the current thread. The data source copies it into the database
 * session setting app.tenant_id, which row-level security enforces (ADR-0007).
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static UUID get() {
        return CURRENT.get();
    }

    public static void set(UUID tenantId) {
        if (tenantId == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(tenantId);
        }
    }

    public static void clear() {
        CURRENT.remove();
    }

    /** Runs work in the given tenant and restores the previous tenant afterwards. */
    public static <T> T callAs(UUID tenantId, Supplier<T> work) {
        UUID previous = CURRENT.get();
        set(tenantId);
        try {
            return work.get();
        } finally {
            set(previous);
        }
    }
}
