package in.coreeye.coreneha.platform;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

import javax.sql.DataSource;

import in.coreeye.coreneha.kernel.TenantContext;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Sets the PostgreSQL session setting app.tenant_id on every connection
 * checkout, from {@link TenantContext}. Row-level security policies read it,
 * so a query can only see its own tenant's rows; with no tenant it sees none
 * of the tenant-owned tables (ADR-0007).
 */
public class TenantAwareDataSource extends DelegatingDataSource {

    public TenantAwareDataSource(DataSource target) {
        super(target);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return applyTenant(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return applyTenant(super.getConnection(username, password));
    }

    private static Connection applyTenant(Connection connection) throws SQLException {
        UUID tenant = TenantContext.get();
        try (PreparedStatement ps = connection.prepareStatement("select set_config('app.tenant_id', ?, false)")) {
            ps.setString(1, tenant == null ? "" : tenant.toString());
            ps.execute();
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
        return connection;
    }
}
