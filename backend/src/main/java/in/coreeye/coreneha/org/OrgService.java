package in.coreeye.coreneha.org;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import in.coreeye.coreneha.audit.AuditEntry;
import in.coreeye.coreneha.audit.AuditLog;
import in.coreeye.coreneha.kernel.AccessControl;
import in.coreeye.coreneha.kernel.Actor;
import in.coreeye.coreneha.kernel.ConflictException;
import in.coreeye.coreneha.kernel.NotFoundException;
import in.coreeye.coreneha.kernel.Permission;
import in.coreeye.coreneha.kernel.RuleViolationException;
import in.coreeye.coreneha.kernel.Scope;
import in.coreeye.coreneha.org.OrgModels.CentreDepartmentView;
import in.coreeye.coreneha.org.OrgModels.CentreView;
import in.coreeye.coreneha.org.OrgModels.DepartmentView;
import in.coreeye.coreneha.org.OrgModels.LegalEntityView;
import in.coreeye.coreneha.org.OrgModels.LevelView;
import in.coreeye.coreneha.org.OrgModels.NodeView;
import in.coreeye.coreneha.org.OrgModels.RetireImpact;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Organisation commands and queries. Every command checks access and writes an audit event in its transaction. */
@Service
public class OrgService {

    private final JdbcClient jdbc;
    private final AccessControl access;
    private final AuditLog audit;
    private final List<DepartmentRetirementCheck> retirementChecks;
    private final OrgDirectory directory;

    private final TenantLicensing licensing;

    OrgService(JdbcClient jdbc, AccessControl access, AuditLog audit, List<DepartmentRetirementCheck> retirementChecks,
               OrgDirectory directory, TenantLicensing licensing) {
        this.directory = directory;
        this.licensing = licensing;
        this.jdbc = jdbc;
        this.access = access;
        this.audit = audit;
        this.retirementChecks = retirementChecks;
    }

    // ---------------------------------------------------------------- structure

    @Transactional
    public LegalEntityView createLegalEntity(Actor actor, OrgModels.CreateLegalEntity cmd) {
        Scope scope = Scope.tenant(actor.tenantId());
        access.require(actor, Permission.ORG_STRUCTURE_MANAGE, scope, "org.legal-entity.create", "legal_entity", null);
        UUID id = insertReturningId("""
                insert into org.legal_entity (tenant_id, code, name) values (:t, :code, :name) returning id
                """, Map.of("t", actor.tenantId(), "code", cmd.code().trim(), "name", cmd.name().trim()),
                "A legal entity with this code already exists");
        audit.record(AuditEntry.success(actor, "org.legal-entity.create", "legal_entity", id, scope,
                Map.of("code", cmd.code(), "name", cmd.name())));
        return legalEntityRows().stream().filter(l -> l.id().equals(id)).findFirst().orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<LegalEntityView> legalEntities(Actor actor) {
        access.requireAnywhere(actor, Permission.ORG_STRUCTURE_READ, "org.legal-entity.list", "legal_entity");
        return legalEntityRows();
    }

    private List<LegalEntityView> legalEntityRows() {
        return jdbc.sql("select id, code, name, status, version from org.legal_entity order by code")
                .query(LegalEntityView.class).list();
    }

    @Transactional
    public LevelView createLevel(Actor actor, OrgModels.CreateLevel cmd) {
        Scope scope = Scope.tenant(actor.tenantId());
        access.require(actor, Permission.ORG_STRUCTURE_MANAGE, scope, "org.level.create", "hierarchy_level", null);
        UUID id = insertReturningId("""
                insert into org.hierarchy_level (tenant_id, code, name, rank) values (:t, :code, :name, :rank) returning id
                """, Map.of("t", actor.tenantId(), "code", cmd.code(), "name", cmd.name().trim(), "rank", cmd.rank()),
                "A level with this code or rank already exists");
        audit.record(AuditEntry.success(actor, "org.level.create", "hierarchy_level", id, scope,
                Map.of("code", cmd.code(), "rank", cmd.rank())));
        return levelRows().stream().filter(l -> l.id().equals(id)).findFirst().orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<LevelView> levels(Actor actor) {
        access.requireAnywhere(actor, Permission.ORG_STRUCTURE_READ, "org.level.list", "hierarchy_level");
        return levelRows();
    }

    private List<LevelView> levelRows() {
        return jdbc.sql("select id, code, name, rank, status, version from org.hierarchy_level order by rank")
                .query(LevelView.class).list();
    }

    @Transactional
    public NodeView createNode(Actor actor, OrgModels.CreateNode cmd) {
        Scope scope = Scope.tenant(actor.tenantId());
        access.require(actor, Permission.ORG_STRUCTURE_MANAGE, scope, "org.node.create", "org_node", null);
        int levelRank = jdbc.sql("select rank from org.hierarchy_level where id = :id and status = 'ACTIVE'")
                .param("id", cmd.levelId()).query(Integer.class).optional()
                .orElseThrow(() -> new NotFoundException("Hierarchy level not found"));
        String parentPath = "/";
        if (cmd.parentId() != null) {
            var parent = jdbc.sql("""
                    select n.path, l.rank from org.org_node n join org.hierarchy_level l on l.id = n.level_id
                    where n.id = :id and n.status = 'ACTIVE'
                    """).param("id", cmd.parentId())
                    .query((rs, i) -> new Object[] {rs.getString("path"), rs.getInt("rank")}).optional()
                    .orElseThrow(() -> new NotFoundException("Parent node not found"));
            if ((int) parent[1] >= levelRank) {
                throw new RuleViolationException("org.node.level-order",
                        "A node must be at a lower level (higher rank) than its parent");
            }
            parentPath = (String) parent[0];
        }
        UUID id = UUID.randomUUID();
        String path = parentPath + id + "/";
        try {
            jdbc.sql("""
                    insert into org.org_node (id, tenant_id, level_id, parent_id, code, name, path)
                    values (:id, :t, :level, :parent, :code, :name, :path)
                    """).param("id", id).param("t", actor.tenantId()).param("level", cmd.levelId())
                    .param("parent", cmd.parentId()).param("code", cmd.code().trim()).param("name", cmd.name().trim())
                    .param("path", path).update();
        } catch (DuplicateKeyException e) {
            throw new ConflictException("org.node.duplicate", "A node with this code already exists");
        }
        audit.record(AuditEntry.success(actor, "org.node.create", "org_node", id, scope,
                Map.of("code", cmd.code(), "name", cmd.name(), "parentId", String.valueOf(cmd.parentId()))));
        return nodeRows().stream().filter(n -> n.id().equals(id)).findFirst().orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<NodeView> nodes(Actor actor) {
        access.requireAnywhere(actor, Permission.ORG_STRUCTURE_READ, "org.node.list", "org_node");
        return nodeRows();
    }

    private List<NodeView> nodeRows() {
        return jdbc.sql("""
                select n.id, n.level_id, l.code as level_code, n.parent_id, n.code, n.name, n.path, n.status, n.version
                from org.org_node n join org.hierarchy_level l on l.id = n.level_id order by l.rank, n.name
                """).query(NodeView.class).list();
    }

    @Transactional
    public DepartmentView createDepartment(Actor actor, OrgModels.CreateDepartment cmd) {
        Scope scope = Scope.tenant(actor.tenantId());
        access.require(actor, Permission.ORG_STRUCTURE_MANAGE, scope, "org.department.define", "department_definition", null);
        UUID id = insertReturningId("""
                insert into org.department_definition (tenant_id, code, name, category)
                values (:t, :code, :name, :category) returning id
                """, Map.of("t", actor.tenantId(), "code", cmd.code(), "name", cmd.name().trim(), "category", cmd.category()),
                "A department with this code already exists");
        audit.record(AuditEntry.success(actor, "org.department.define", "department_definition", id, scope,
                Map.of("code", cmd.code(), "name", cmd.name(), "category", cmd.category())));
        return departmentRows().stream().filter(d -> d.id().equals(id)).findFirst().orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<DepartmentView> departments(Actor actor) {
        access.requireAnywhere(actor, Permission.ORG_STRUCTURE_READ, "org.department.list", "department_definition");
        return departmentRows();
    }

    private List<DepartmentView> departmentRows() {
        return jdbc.sql("select id, code, name, category, status, version from org.department_definition order by name")
                .query(DepartmentView.class).list();
    }

    // ---------------------------------------------------------------- centres

    @Transactional
    public CentreView createCentre(Actor actor, OrgModels.CreateCentre cmd) {
        OrgDirectory.NodeRef node = directory.node(cmd.nodeId()).orElseThrow(() -> new NotFoundException("Hierarchy node not found"));
        Scope scope = Scope.node(node.id());
        access.require(actor, Permission.ORG_CENTRE_CREATE, scope, "org.centre.create", "centre", null);
        licensing.requireCentreAllowance(actor.tenantId());
        boolean legalEntityActive = jdbc.sql("select count(*) from org.legal_entity where id = :id and status = 'ACTIVE'")
                .param("id", cmd.legalEntityId()).query(Integer.class).single() == 1;
        if (!legalEntityActive) {
            throw new NotFoundException("Legal entity not found");
        }
        UUID id = insertReturningId("""
                insert into org.centre (tenant_id, node_id, legal_entity_id, code, name, centre_type)
                values (:t, :node, :le, :code, :name, :type) returning id
                """, Map.of("t", actor.tenantId(), "node", node.id(), "le", cmd.legalEntityId(),
                        "code", cmd.code().trim(), "name", cmd.name().trim(), "type", cmd.centreType().trim()),
                "A centre with this code already exists");
        audit.record(AuditEntry.success(actor, "org.centre.create", "centre", id, Scope.centre(id),
                Map.of("code", cmd.code(), "name", cmd.name(), "centreType", cmd.centreType(), "nodeId", node.id().toString())));
        if (cmd.departmentIds() != null) {
            for (UUID departmentId : cmd.departmentIds()) {
                enableDepartmentChecked(actor, id, departmentId, null, LocalDate.now());
            }
        }
        return centreView(id);
    }

    @Transactional(readOnly = true)
    public List<CentreView> centres(Actor actor) {
        Set<UUID> visible = access.centresWhere(actor, Permission.ORG_CENTRE_READ);
        if (visible.isEmpty()) {
            access.requireAnywhere(actor, Permission.ORG_CENTRE_READ, "org.centre.list", "centre");
        }
        return centreViews().stream().filter(c -> visible.contains(c.id())).toList();
    }

    @Transactional(readOnly = true)
    public CentreView centre(Actor actor, UUID centreId) {
        access.require(actor, Permission.ORG_CENTRE_READ, Scope.centre(centreId), "org.centre.read", "centre", centreId.toString());
        return centreView(centreId);
    }

    @Transactional(readOnly = true)
    public List<CentreDepartmentView> centreDepartments(Actor actor, UUID centreId, boolean includeRetired) {
        access.require(actor, Permission.ORG_CENTRE_READ, Scope.centre(centreId), "org.centre.departments", "centre", centreId.toString());
        return centreDepartmentViews(centreId, includeRetired);
    }

    private List<CentreDepartmentView> centreDepartmentViews(UUID centreId, boolean includeRetired) {
        return jdbc.sql("""
                select cd.id, cd.department_id, d.code as department_code, d.name as department_name, d.category,
                       cd.local_name, cd.valid_from, cd.valid_to, cd.version
                from org.centre_department cd join org.department_definition d on d.id = cd.department_id
                where cd.centre_id = :c and (:all or cd.valid_to is null or cd.valid_to >= current_date)
                order by d.name, cd.valid_from
                """).param("c", centreId).param("all", includeRetired).query(CentreDepartmentView.class).list();
    }

    @Transactional
    public CentreDepartmentView enableDepartment(Actor actor, UUID centreId, OrgModels.EnableDepartment cmd) {
        UUID id = enableDepartmentChecked(actor, centreId, cmd.departmentId(), cmd.localName(),
                cmd.validFrom() == null ? LocalDate.now() : cmd.validFrom());
        return centreDepartmentViews(centreId, true).stream().filter(v -> v.id().equals(id)).findFirst().orElseThrow();
    }

    private UUID enableDepartmentChecked(Actor actor, UUID centreId, UUID departmentId, String localName, LocalDate validFrom) {
        Scope scope = Scope.centre(centreId);
        access.require(actor, Permission.ORG_CENTRE_CONFIGURE, scope, "org.centre.department.enable", "centre", centreId.toString());
        OrgDirectory.CentreRef centre = directory.centre(centreId).orElseThrow(() -> new NotFoundException("Centre not found"));
        if (!"ACTIVE".equals(centre.status())) {
            throw new RuleViolationException("org.centre.retired", "The centre is retired");
        }
        boolean departmentActive = jdbc.sql("select count(*) from org.department_definition where id = :id and status = 'ACTIVE'")
                .param("id", departmentId).query(Integer.class).single() == 1;
        if (!departmentActive) {
            throw new NotFoundException("Department definition not found");
        }
        UUID id = insertReturningId("""
                insert into org.centre_department (tenant_id, centre_id, department_id, local_name, valid_from)
                values (:t, :c, :d, :local, :from) returning id
                """, mapOf("t", actor.tenantId(), "c", centreId, "d", departmentId, "local", localName, "from", validFrom),
                "This department is already enabled at the centre");
        audit.record(AuditEntry.success(actor, "org.centre.department.enable", "centre_department", id, scope,
                mapOf("departmentId", departmentId.toString(), "validFrom", validFrom.toString())));
        return id;
    }

    @Transactional(readOnly = true)
    public RetireImpact retireImpact(Actor actor, UUID centreId, UUID centreDepartmentId) {
        access.require(actor, Permission.ORG_CENTRE_CONFIGURE, Scope.centre(centreId), "org.centre.department.retire-impact",
                "centre_department", centreDepartmentId.toString());
        return impact(centreId, centreDepartmentId);
    }

    @Transactional
    public CentreDepartmentView retireDepartment(Actor actor, UUID centreId, UUID centreDepartmentId,
                                                 OrgModels.RetireDepartment cmd) {
        Scope scope = Scope.centre(centreId);
        access.require(actor, Permission.ORG_CENTRE_CONFIGURE, scope, "org.centre.department.retire", "centre_department",
                centreDepartmentId.toString());
        RetireImpact impact = impact(centreId, centreDepartmentId);
        if (!impact.blockingReasons().isEmpty()) {
            throw new RuleViolationException("org.centre.department.active-work",
                    "Active work prevents retirement: " + String.join("; ", impact.blockingReasons()));
        }
        int updated = jdbc.sql("""
                update org.centre_department set valid_to = :to, version = version + 1
                where id = :id and centre_id = :c and version = :v and valid_to is null and valid_from <= :to
                """).param("to", cmd.validTo()).param("id", centreDepartmentId).param("c", centreId)
                .param("v", cmd.expectedVersion()).update();
        if (updated == 0) {
            throw new ConflictException("org.centre.department.stale",
                    "The department was changed by someone else, is already retired, or the date is before it started");
        }
        audit.record(AuditEntry.success(actor, "org.centre.department.retire", "centre_department", centreDepartmentId,
                scope, Map.of("validTo", cmd.validTo().toString())));
        return centreDepartmentViews(centreId, true).stream().filter(v -> v.id().equals(centreDepartmentId))
                .findFirst().orElseThrow();
    }

    private RetireImpact impact(UUID centreId, UUID centreDepartmentId) {
        OrgDirectory.CentreDepartmentRef ref = directory.centreDepartment(centreDepartmentId)
                .filter(cd -> cd.centreId().equals(centreId))
                .orElseThrow(() -> new NotFoundException("Department not enabled at this centre"));
        List<String> reasons = new ArrayList<>();
        int establishments = 0;
        int postings = 0;
        for (DepartmentRetirementCheck check : retirementChecks) {
            for (DepartmentRetirementCheck.Blocker b : check.blockers(ref.id())) {
                reasons.add(b.count() + " " + b.description());
                if (b.description().contains("establishment")) {
                    establishments += b.count();
                }
                if (b.description().contains("posting")) {
                    postings += b.count();
                }
            }
        }
        return new RetireImpact(ref.id(), establishments, postings, reasons);
    }

    // ---------------------------------------------------------------- helpers

    private CentreView centreView(UUID id) {
        return centreViews().stream().filter(c -> c.id().equals(id)).findFirst()
                .orElseThrow(() -> new NotFoundException("Centre not found"));
    }

    private List<CentreView> centreViews() {
        return jdbc.sql("""
                select c.id, c.node_id, n.name as node_name, c.legal_entity_id, le.name as legal_entity_name, c.code,
                       c.name, c.centre_type, c.status, c.created_at, c.version
                from org.centre c join org.org_node n on n.id = c.node_id join org.legal_entity le on le.id = c.legal_entity_id
                order by c.name
                """).query(CentreView.class).list();
    }

    private UUID insertReturningId(String sql, Map<String, ?> params, String duplicateMessage) {
        try {
            return jdbc.sql(sql).params(params).query(UUID.class).single();
        } catch (DuplicateKeyException e) {
            throw new ConflictException("duplicate", duplicateMessage);
        }
    }

    private static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new java.util.HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }
}
