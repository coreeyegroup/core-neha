package in.coreeye.coreneha.org;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Organisation lookups without access checks, for other modules (they check access themselves). */
@Component
class JdbcOrgDirectory implements OrgDirectory {

    private final JdbcClient jdbc;

    JdbcOrgDirectory(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }


    @Override
    public Optional<CentreRef> centre(UUID centreId) {
        return jdbc.sql("""
                select c.id, c.tenant_id, c.node_id, n.path as node_path, c.code, c.name, c.status
                from org.centre c join org.org_node n on n.id = c.node_id where c.id = :id
                """).param("id", centreId).query(CentreRef.class).optional();
    }

    @Override
    public Optional<NodeRef> node(UUID nodeId) {
        return jdbc.sql("select id, tenant_id, path, name from org.org_node where id = :id")
                .param("id", nodeId).query(NodeRef.class).optional();
    }

    @Override
    public List<CentreRef> allCentres() {
        return jdbc.sql("""
                select c.id, c.tenant_id, c.node_id, n.path as node_path, c.code, c.name, c.status
                from org.centre c join org.org_node n on n.id = c.node_id order by c.code
                """).query(CentreRef.class).list();
    }

    @Override
    public Optional<CentreDepartmentRef> centreDepartment(UUID centreDepartmentId) {
        return jdbc.sql("""
                select cd.id, cd.centre_id, cd.department_id, d.code as department_code,
                       (cd.valid_to is null or cd.valid_to >= current_date) as open
                from org.centre_department cd join org.department_definition d on d.id = cd.department_id
                where cd.id = :id
                """).param("id", centreDepartmentId).query(CentreDepartmentRef.class).optional();
    }

    @Override
    public Optional<UUID> tenantIdByCode(String code) {
        return jdbc.sql("select x.id from org.tenant_id_by_code(:c) as x(id) where x.id is not null").param("c", code).query(UUID.class).optional();
    }
}
