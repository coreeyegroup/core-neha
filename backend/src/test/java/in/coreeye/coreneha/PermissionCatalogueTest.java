package in.coreeye.coreneha;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import in.coreeye.coreneha.kernel.Permission;
import in.coreeye.coreneha.support.PostgresTestBase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/** The permission enum used by code and the catalogue table used by roles must be identical. */
class PermissionCatalogueTest extends PostgresTestBase {

    @Test
    void codeAndDatabaseCataloguesMatch() throws Exception {
        Flyway.configure().dataSource(jdbcUrl(), "core_neha_owner", OWNER_PASSWORD).locations("classpath:db/migration").load().migrate();
        Map<String, Boolean> db = new HashMap<>();
        try (Connection c = DriverManager.getConnection(jdbcUrl(), "core_neha_app", APP_PASSWORD);
             Statement s = c.createStatement(); ResultSet rs = s.executeQuery("select code, tenant_only from iam.permission")) {
            while (rs.next()) {
                db.put(rs.getString(1), rs.getBoolean(2));
            }
        }
        Map<String, Boolean> code = new HashMap<>();
        Arrays.stream(Permission.values()).forEach(p -> code.put(p.code(), p.tenantOnly()));
        assertThat(db).isEqualTo(code);
    }
}
