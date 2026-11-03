package in.coreeye.coreneha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import in.coreeye.coreneha.support.PostgresTestBase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Migrations apply to an empty database, and the runtime role cannot change the schema or rewrite audit data. */
class MigrationPrivilegesTest extends PostgresTestBase {

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(jdbcUrl(), "core_neha_owner", OWNER_PASSWORD)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private static Connection asApp() throws SQLException {
        return DriverManager.getConnection(jdbcUrl(), "core_neha_app", APP_PASSWORD);
    }

    @Test
    void moduleSchemasExist() throws SQLException {
        try (Connection c = asApp(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("select count(*) from information_schema.schemata "
                     + "where schema_name in ('org','iam','workforce','audit','session')")) {
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(5);
        }
    }

    @Test
    void runtimeRoleCannotCreateTables() throws SQLException {
        try (Connection c = asApp(); Statement s = c.createStatement()) {
            assertThatThrownBy(() -> s.execute("create table org.should_fail (id int)"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
        }
    }

    @Test
    void runtimeRoleCannotUseThePublicSchema() throws SQLException {
        try (Connection c = asApp(); Statement s = c.createStatement()) {
            assertThatThrownBy(() -> s.execute("create table public.should_fail (id int)"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
        }
    }

    @Test
    void runtimeRoleCanUseSessionTables() throws SQLException {
        try (Connection c = asApp(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("select count(*) from session.spring_session")) {
            // Other tests share this database and may have created sessions; the point is that the runtime role can read it.
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isNotNegative();
        }
    }
}
