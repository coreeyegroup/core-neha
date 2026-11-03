package in.coreeye.coreneha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import in.coreeye.coreneha.support.PostgresTestBase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Database-level tenant isolation for the runtime role (ADR-0007) and append-only audit (ADR-0008). */
class RowLevelSecurityTest extends PostgresTestBase {

    static final UUID TENANT_X = UUID.randomUUID();
    static final UUID TENANT_Y = UUID.randomUUID();

    @BeforeAll
    static void setUp() throws SQLException {
        Flyway.configure().dataSource(jdbcUrl(), "core_neha_owner", OWNER_PASSWORD).locations("classpath:db/migration").load().migrate();
        for (UUID t : new UUID[] {TENANT_X, TENANT_Y}) {
            try (Connection c = app(t); PreparedStatement ps = c.prepareStatement(
                    "insert into org.tenant (id, code, name) values (?, ?, 'RLS test tenant')")) {
                ps.setObject(1, t);
                ps.setString(2, "rls-" + t.toString().substring(0, 8));
                ps.executeUpdate();
            }
        }
    }

    private static Connection app(UUID tenant) throws SQLException {
        Connection c = DriverManager.getConnection(jdbcUrl(), "core_neha_app", APP_PASSWORD);
        try (PreparedStatement ps = c.prepareStatement("select set_config('app.tenant_id', ?, false)")) {
            ps.setString(1, tenant == null ? "" : tenant.toString());
            ps.execute();
        }
        return c;
    }

    private static int count(Connection c, String sql) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    void eachTenantSeesOnlyItself() throws SQLException {
        try (Connection c = app(TENANT_X)) {
            assertThat(count(c, "select count(*) from org.tenant where id = '" + TENANT_Y + "'")).isZero();
            assertThat(count(c, "select count(*) from org.tenant where id = '" + TENANT_X + "'")).isEqualTo(1);
        }
    }

    @Test
    void noTenantContextSeesNothing() throws SQLException {
        try (Connection c = app(null)) {
            assertThat(count(c, "select count(*) from org.tenant")).isZero();
        }
    }

    @Test
    void cannotWriteRowsForAnotherTenant() throws SQLException {
        try (Connection c = app(TENANT_X); PreparedStatement ps = c.prepareStatement(
                "insert into org.legal_entity (tenant_id, code, name) values (?, 'LE', 'Other tenant entity')")) {
            ps.setObject(1, TENANT_Y);
            assertThatThrownBy(ps::executeUpdate).isInstanceOf(SQLException.class).hasMessageContaining("row-level security");
        }
    }

    @Test
    void auditEventsCannotBeChangedOrDeleted() throws SQLException {
        try (Connection c = app(TENANT_X)) {
            try (PreparedStatement ps = c.prepareStatement("""
                    insert into audit.audit_event (tenant_id, occurred_at, actor_label, action, object_type, outcome)
                    values (?, now(), 'test', 'test.action', 'test', 'SUCCESS')""")) {
                ps.setObject(1, TENANT_X);
                ps.executeUpdate();
            }
            assertThatThrownBy(() -> c.createStatement().executeUpdate("update audit.audit_event set action = 'x'"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("permission denied");
            assertThatThrownBy(() -> c.createStatement().executeUpdate("delete from audit.audit_event"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("permission denied");
        }
    }
}
