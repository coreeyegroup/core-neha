package in.coreeye.coreneha.iam;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

import in.coreeye.coreneha.kernel.AccessControl;
import in.coreeye.coreneha.kernel.Actor;
import in.coreeye.coreneha.kernel.Permission;
import in.coreeye.coreneha.kernel.Scope;
import in.coreeye.coreneha.kernel.ScopeType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The access guide in the help centre: which licence modules the chain has, every permission and
 * what it allows, and what the signed-in person holds. Group-wide administrators (the developer
 * guide's audience: iam.role.approve for the whole chain) also get every role, every position and
 * who holds what, so they can see the whole access model in one place. Read only; built from the
 * live database, so it never goes out of date.
 */
@Service
public class AccessGuideService {

    /** One licence module and whether the chain has it today. CORE is always licensed. */
    public record ModuleInfo(String code, boolean licensed, int permissions) {
    }

    public record LicenceInfo(String licenceNo, String plan, String status, LocalDate validFrom, LocalDate validTo, Integer maxCentres) {
    }

    /** A place where the signed-in person holds a permission, and the role that gives it. */
    public record HeldAt(String scopeType, String scopeName, String role) {
    }

    /**
     * One permission. {@code heldAt} is the signed-in person's own grants; {@code roles} (role codes
     * whose published version contains it) is filled for administrators only.
     */
    public record PermissionInfo(String code, String area, String module, String description, boolean tenantOnly, boolean licensed,
                                 List<HeldAt> heldAt, List<String> roles) {
    }

    public record RoleInfo(String code, String name, String description, boolean system, String status, Integer publishedVersion,
                           List<String> permissions, int activeAssignments, List<String> positions) {
    }

    public record PositionInfo(String code, String title, String category, String status, int people, List<String> roles) {
    }

    public record AccessGuide(boolean full, String chainName, LicenceInfo licence, List<ModuleInfo> modules,
                              List<PermissionInfo> permissions, List<RoleInfo> roles, List<PositionInfo> positions) {
    }

    private final JdbcClient jdbc;
    private final AccessControl access;
    private final AccessEvaluator evaluator;
    private final IamSupport support;

    AccessGuideService(JdbcClient jdbc, AccessControl access, AccessEvaluator evaluator, IamSupport support) {
        this.jdbc = jdbc;
        this.access = access;
        this.evaluator = evaluator;
        this.support = support;
    }

    @Transactional(readOnly = true)
    public AccessGuide guide(Actor actor) {
        UUID tenant = actor.tenantId();
        boolean full = access.holds(actor, Permission.IAM_ROLE_APPROVE, Scope.tenant(tenant));
        String chain = jdbc.sql("select name from org.tenant where id = :t").param("t", tenant).query(String.class).optional().orElse("");
        LicenceInfo licence = jdbc.sql("""
                select licence_no, plan, status, valid_from, valid_to, max_centres from org.tenant_licence where tenant_id = :t""")
                .param("t", tenant).query((rs, i) -> new LicenceInfo(rs.getString("licence_no"), rs.getString("plan"), rs.getString("status"),
                        rs.getObject("valid_from", LocalDate.class), rs.getObject("valid_to", LocalDate.class),
                        (Integer) rs.getObject("max_centres"))).optional().orElse(null);

        // The person's own grants (already filtered by licence, account, chain and role status).
        Map<String, List<HeldAt>> held = new LinkedHashMap<>();
        for (AccessEvaluator.Grant g : evaluator.grants(actor.userId())) {
            Permission p = Permission.fromCode(g.permission()).orElse(null);
            if (p == null || (p.tenantOnly() && g.scopeType() != ScopeType.TENANT)) {
                continue;
            }
            held.computeIfAbsent(p.code(), k -> new ArrayList<>())
                    .add(new HeldAt(g.scopeType().name(), support.scopeName(g.scopeType().name(), g.scopeId()), g.roleCode()));
        }

        Map<String, List<String>> rolesByPermission = new LinkedHashMap<>();
        List<RoleInfo> roles = List.of();
        List<PositionInfo> positions = List.of();
        if (full) {
            Map<String, List<String>> grants = new LinkedHashMap<>();
            jdbc.sql("""
                    select r.code as role_code, g.permission_code
                    from iam.role r join iam.role_version rv on rv.role_id = r.id and rv.status = 'PUBLISHED'
                    join iam.role_version_grant g on g.role_version_id = rv.id
                    where r.status = 'ACTIVE' order by r.code, g.permission_code""")
                    .query((rs, i) -> new String[] {rs.getString("role_code"), rs.getString("permission_code")}).list()
                    .forEach(row -> {
                        grants.computeIfAbsent(row[0], k -> new ArrayList<>()).add(row[1]);
                        rolesByPermission.computeIfAbsent(row[1], k -> new ArrayList<>()).add(row[0]);
                    });
            Map<String, TreeSet<String>> positionsByRole = new LinkedHashMap<>();
            Map<String, TreeSet<String>> rolesByPosition = new LinkedHashMap<>();
            jdbc.sql("""
                    select distinct r.code as role_code, pos.code as position_code, pos.title
                    from iam.role_assignment ra join iam.role r on r.id = ra.role_id
                    join workforce.posting p on p.id = ra.posting_id
                    join workforce.establishment e on e.id = p.establishment_id
                    join workforce.position_definition pos on pos.id = e.position_id
                    where ra.kind = 'ACCESS' and ra.status = 'ACTIVE'
                      and (ra.valid_to is null or ra.valid_to >= current_date)""")
                    .query((rs, i) -> new String[] {rs.getString("role_code"), rs.getString("position_code"), rs.getString("title")}).list()
                    .forEach(row -> {
                        positionsByRole.computeIfAbsent(row[0], k -> new TreeSet<>()).add(row[2]);
                        rolesByPosition.computeIfAbsent(row[1], k -> new TreeSet<>()).add(row[0]);
                    });
            roles = jdbc.sql("""
                    select r.code, r.name, r.description, r.is_system, r.status,
                           (select max(version_no) from iam.role_version v where v.role_id = r.id and v.status = 'PUBLISHED') as published,
                           (select count(*) from iam.role_assignment a where a.role_id = r.id and a.kind = 'ACCESS' and a.status = 'ACTIVE'
                              and (a.valid_to is null or a.valid_to >= current_date)) as active
                    from iam.role r order by r.is_system desc, r.code""")
                    .query((rs, i) -> new RoleInfo(rs.getString("code"), rs.getString("name"), rs.getString("description"),
                            rs.getBoolean("is_system"), rs.getString("status"), (Integer) rs.getObject("published"),
                            grants.getOrDefault(rs.getString("code"), List.of()), rs.getInt("active"),
                            List.copyOf(positionsByRole.getOrDefault(rs.getString("code"), new TreeSet<>())))).list();
            positions = jdbc.sql("""
                    select pos.code, pos.title, pos.category, pos.status,
                           (select count(distinct p.person_id) from workforce.posting p join workforce.establishment e on e.id = p.establishment_id
                             where e.position_id = pos.id and p.valid_from <= current_date
                               and (p.valid_to is null or p.valid_to >= current_date)) as people
                    from workforce.position_definition pos order by pos.category, pos.title""")
                    .query((rs, i) -> new PositionInfo(rs.getString("code"), rs.getString("title"), rs.getString("category"),
                            rs.getString("status"), rs.getInt("people"),
                            List.copyOf(rolesByPosition.getOrDefault(rs.getString("code"), new TreeSet<>())))).list();
        }

        List<PermissionInfo> permissions = jdbc.sql("""
                select code, module, description, tenant_only, org.permission_module(code) as licence_module,
                       org.permission_licensed(:t, code) as licensed
                from iam.permission order by org.permission_module(code) = 'CORE' desc, org.permission_module(code), code""")
                .param("t", tenant)
                .query((rs, i) -> new PermissionInfo(rs.getString("code"), rs.getString("module"), rs.getString("licence_module"),
                        rs.getString("description"), rs.getBoolean("tenant_only"), rs.getBoolean("licensed"),
                        held.getOrDefault(rs.getString("code"), List.of()),
                        full ? rolesByPermission.getOrDefault(rs.getString("code"), List.of()) : List.of())).list();

        Map<String, int[]> counts = new LinkedHashMap<>();
        permissions.forEach(p -> counts.computeIfAbsent(p.module(), k -> new int[1])[0]++);
        List<ModuleInfo> modules = new ArrayList<>();
        counts.forEach((code, n) -> modules.add(new ModuleInfo(code, "CORE".equals(code) || isLicensed(tenant, code), n[0])));
        return new AccessGuide(full, chain, licence, modules, permissions, roles, positions);
    }

    private boolean isLicensed(UUID tenant, String module) {
        return Boolean.TRUE.equals(jdbc.sql("""
                select exists (select 1 from org.tenant_licence l join org.tenant_module m on m.tenant_id = l.tenant_id and m.module_code = :m
                               where l.tenant_id = :t and l.status = 'ACTIVE' and l.valid_from <= current_date
                                 and (l.valid_to is null or l.valid_to >= current_date))""")
                .param("t", tenant).param("m", module).query(Boolean.class).single());
    }
}
