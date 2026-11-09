package in.coreeye.coreneha.org;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import in.coreeye.coreneha.audit.AuditEntry;
import in.coreeye.coreneha.audit.AuditLog;
import in.coreeye.coreneha.kernel.Actor;
import in.coreeye.coreneha.kernel.NotFoundException;
import in.coreeye.coreneha.kernel.RuleViolationException;
import in.coreeye.coreneha.kernel.TenantContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Licences of the hospital chains (tenants) that use the platform (V8). The vendor manages them
 * from the server console only (ops/tenant-*.sh); there is no web route that changes a licence,
 * so no hospital user, however privileged, can widen their own licence. Each chain's staff can
 * read their own licence. Enforcement is in the access evaluator: an unlicensed module's
 * permissions are never granted, and a suspended chain has no access at all.
 */
@Service
public class TenantLicensing {

    public static final List<String> ALL_MODULES = List.of("EYE_CARE", "BILLING", "INVENTORY", "PHARMACY", "OPTICAL", "OPTICAL_LAB",
            "LABORATORY", "RADIOLOGY");
    private static final String VENDOR = "platform operator (server console)";

    public record TenantSummary(UUID tenantId, String code, String name, boolean demo, String status, String licenceNo,
                                String licenseeName, String plan, LocalDate validFrom, LocalDate validTo, Integer maxCentres,
                                String licenceStatus, List<String> modules, long centres, long users) {
    }

    public record LicenceView(String tenantName, String licenceNo, String licenseeName, String plan, LocalDate validFrom,
                              LocalDate validTo, Integer maxCentres, String status, boolean current, List<String> modules,
                              long centres) {
    }

    public record LicenceChange(String licenceNo, String licenseeName, String plan, LocalDate validFrom, LocalDate validTo,
                                Integer maxCentres, List<String> modules, String reason) {
    }

    private final JdbcClient jdbc;
    private final AuditLog audit;
    private final TransactionTemplate tx;

    TenantLicensing(JdbcClient jdbc, AuditLog audit, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** Every chain on this installation, with licence and size only (no patient or staff data). */
    public List<TenantSummary> tenants() {
        return jdbc.sql("select * from org.platform_tenants()").query((rs, i) -> new TenantSummary(
                rs.getObject("tenant_id", UUID.class), rs.getString("code"), rs.getString("name"), rs.getBoolean("is_demo"),
                rs.getString("status"), rs.getString("licence_no"), rs.getString("licensee_name"), rs.getString("plan"),
                rs.getObject("valid_from", LocalDate.class), rs.getObject("valid_to", LocalDate.class),
                (Integer) rs.getObject("max_centres"), rs.getString("licence_status"),
                rs.getString("modules") == null ? List.of() : List.of(rs.getString("modules").split(",")),
                rs.getLong("centres"), rs.getLong("users"))).list();
    }

    public UUID tenantId(String code) {
        return tenants().stream().filter(t -> t.code().equals(code)).map(TenantSummary::tenantId).findFirst()
                .orElseThrow(() -> new NotFoundException("No tenant with code '" + code + "'"));
    }

    /** Vendor: replaces the licence terms and modules of one chain. */
    public void setLicence(String tenantCode, LicenceChange change) {
        UUID tenant = tenantId(tenantCode);
        Set<String> modules = new LinkedHashSet<>(change.modules());
        for (String m : modules) {
            if (!ALL_MODULES.contains(m)) {
                throw new RuleViolationException("licence.unknown-module", "Unknown module " + m + "; known: " + ALL_MODULES);
            }
        }
        if (change.reason() == null || change.reason().isBlank()) {
            throw new RuleViolationException("licence.reason-required", "Give a reason for the licence change");
        }
        TenantContext.callAs(tenant, () -> tx.execute(status -> {
            jdbc.sql("""
                    insert into org.tenant_licence (tenant_id, licence_no, licensee_name, plan, valid_from, valid_to, max_centres, notes)
                    values (:t, :no, :n, :plan, :from, :to, :max, :notes)
                    on conflict (tenant_id) do update set licence_no = excluded.licence_no, licensee_name = excluded.licensee_name,
                        plan = excluded.plan, valid_from = excluded.valid_from, valid_to = excluded.valid_to,
                        max_centres = excluded.max_centres, notes = excluded.notes, updated_at = now(),
                        version = org.tenant_licence.version + 1
                    """).param("t", tenant).param("no", change.licenceNo()).param("n", change.licenseeName())
                    .param("plan", change.plan()).param("from", change.validFrom()).param("to", change.validTo())
                    .param("max", change.maxCentres()).param("notes", change.reason()).update();
            jdbc.sql("delete from org.tenant_module where tenant_id = :t and not (module_code = any(cast(:m as text[])))")
                    .param("t", tenant).param("m", modules.toArray(new String[0])).update();
            jdbc.sql("""
                    insert into org.tenant_module (tenant_id, module_code) select :t, m from unnest(cast(:m as text[])) as m
                    on conflict do nothing
                    """).param("t", tenant).param("m", modules.toArray(new String[0])).update();
            Map<String, Object> details = new HashMap<>();
            details.put("licenceNo", change.licenceNo());
            details.put("plan", change.plan());
            details.put("validTo", String.valueOf(change.validTo()));
            details.put("maxCentres", String.valueOf(change.maxCentres()));
            details.put("modules", String.join(",", modules));
            details.put("reason", change.reason());
            audit.record(AuditEntry.system(tenant, VENDOR, "platform.licence.set", "tenant", tenant, null, details));
            return null;
        }));
    }

    /** Vendor: suspends (no access for anyone in the chain) or reactivates a chain. */
    public void setStatus(String tenantCode, boolean active, String reason) {
        UUID tenant = tenantId(tenantCode);
        if (reason == null || reason.isBlank()) {
            throw new RuleViolationException("licence.reason-required", "Give a reason");
        }
        TenantContext.callAs(tenant, () -> tx.execute(status -> {
            String s = active ? "ACTIVE" : "SUSPENDED";
            jdbc.sql("update org.tenant set status = :s, version = version + 1 where id = :t").param("s", s).param("t", tenant).update();
            jdbc.sql("update org.tenant_licence set status = :s, updated_at = now(), version = version + 1 where tenant_id = :t")
                    .param("s", s).param("t", tenant).update();
            audit.record(AuditEntry.system(tenant, VENDOR, active ? "platform.tenant.activate" : "platform.tenant.suspend", "tenant",
                    tenant, null, Map.of("reason", reason)));
            return null;
        }));
    }

    /** The signed-in user's own chain's licence (any member of the chain may read it). */
    public LicenceView licence(Actor actor) {
        return jdbc.sql("""
                select t.name, l.licence_no, l.licensee_name, l.plan, l.valid_from, l.valid_to, l.max_centres, l.status,
                       (l.status = 'ACTIVE' and l.valid_from <= current_date and (l.valid_to is null or l.valid_to >= current_date)) as current,
                       (select string_agg(m.module_code, ',' order by m.module_code) from org.tenant_module m where m.tenant_id = t.id) as modules,
                       (select count(*) from org.centre c where c.tenant_id = t.id) as centres
                from org.tenant t join org.tenant_licence l on l.tenant_id = t.id where t.id = :t
                """).param("t", actor.tenantId()).query((rs, i) -> new LicenceView(rs.getString("name"), rs.getString("licence_no"),
                rs.getString("licensee_name"), rs.getString("plan"), rs.getObject("valid_from", LocalDate.class),
                rs.getObject("valid_to", LocalDate.class), (Integer) rs.getObject("max_centres"), rs.getString("status"),
                rs.getBoolean("current"), rs.getString("modules") == null ? List.of() : List.of(rs.getString("modules").split(",")),
                rs.getLong("centres"))).optional().orElseThrow(() -> new NotFoundException("This organisation has no licence"));
    }

    /** Refuses a new centre beyond the licensed number. */
    void requireCentreAllowance(UUID tenantId) {
        Integer max = jdbc.sql("select max_centres from org.tenant_licence where tenant_id = :t").param("t", tenantId)
                .query(Integer.class).optional().orElse(null);
        if (max == null) {
            return;
        }
        long centres = jdbc.sql("select count(*) from org.centre where tenant_id = :t").param("t", tenantId).query(Long.class).single();
        if (centres >= max) {
            throw new RuleViolationException("licence.centre-limit",
                    "Your licence allows " + max + " centres and all are in use; ask the software provider to extend the licence");
        }
    }
}
