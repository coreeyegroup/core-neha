package in.coreeye.coreneha.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import in.coreeye.coreneha.iam.IdentityProvisioner;
import in.coreeye.coreneha.kernel.TenantContext;
import in.coreeye.coreneha.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** The demonstration tenant has 20 centres with different compositions, built through the normal rules. */
@SpringBootTest(properties = "CORE_NEHA_ENVIRONMENT=demo")
class DemoSeedTest extends PostgresTestBase {

    @Autowired DemoSeed seed;
    @Autowired JdbcClient jdbc;
    @MockitoBean IdentityProvisioner provisioner;

    @Test
    void loadsTwentyCentresWithDifferentCapabilities() {
        when(provisioner.create(any(), any(), any(), any(), anyBoolean()))
                .thenAnswer(inv -> new IdentityProvisioner.ProvisionedIdentity("kc-" + UUID.randomUUID(), "Temporary1234Ab"));
        DemoSeed.Summary summary = seed.load();
        assertThat(summary.centres()).isEqualTo(20);
        assertThat(summary.logins()).hasSize(7);

        TenantContext.callAs(summary.tenantId(), () -> {
            assertThat(jdbc.sql("select count(*) from org.centre").query(Integer.class).single()).isEqualTo(20);
            Map<String, List<String>> deptsByCentre = jdbc.sql("""
                    select c.code, d.code as dept from org.centre c join org.centre_department cd on cd.centre_id = c.id
                    join org.department_definition d on d.id = cd.department_id""")
                    .query((rs, i) -> new String[] {rs.getString(1), rs.getString(2)}).list().stream()
                    .collect(Collectors.groupingBy(r -> r[0], Collectors.mapping(r -> r[1], Collectors.toList())));
            assertThat(deptsByCentre.get("BLR-IND")).contains("SURGERY_DAYCARE").doesNotContain("OPTICAL_MANUFACTURING");
            assertThat(deptsByCentre.get("BLR-OPL")).contains("OPTICAL_MANUFACTURING").doesNotContain("OPHTHALMOLOGY");
            assertThat(deptsByCentre.get("BLR-JAY")).doesNotContain("SURGERY_DAYCARE");
            assertThat(deptsByCentre.values().stream().map(v -> v.stream().sorted().toList()).distinct().count()).isEqualTo(6);
            // No establishment in a department its centre has not enabled
            assertThat(jdbc.sql("""
                    select count(*) from workforce.establishment e join org.centre_department cd on cd.id = e.centre_department_id
                    where cd.centre_id <> e.centre_id""").query(Integer.class).single()).isZero();
            // The visiting consultant holds two scoped, approved assignments
            assertThat(jdbc.sql("""
                    select count(*) from iam.role_assignment ra join iam.user_account u on u.id = ra.user_id
                    join workforce.person p on p.id = u.person_id
                    where p.given_name = 'Ravi' and p.family_name = 'Rao' and ra.status = 'ACTIVE' and ra.scope_type = 'CENTRE'""")
                    .query(Integer.class).single()).isEqualTo(2);
            // Every non-bootstrap assignment was approved by someone other than its requester
            assertThat(jdbc.sql("""
                    select count(*) from iam.role_assignment where not via_bootstrap
                    and (decided_by is null or decided_by = requested_by or decided_by = user_id)""")
                    .query(Integer.class).single()).isZero();
            // Logins are employee numbers, not names or positions
            assertThat(jdbc.sql("select count(*) from iam.user_account u join workforce.person p on p.id = u.person_id where u.username <> lower(p.employee_no)")
                    .query(Integer.class).single()).isZero();
            return null;
        });
    }
}
