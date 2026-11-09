package in.coreeye.coreneha.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import in.coreeye.coreneha.iam.BootstrapService;
import in.coreeye.coreneha.iam.IdentityProvisioner;
import in.coreeye.coreneha.kernel.AccessControl;
import in.coreeye.coreneha.kernel.Actor;
import in.coreeye.coreneha.kernel.Permission;
import in.coreeye.coreneha.kernel.RuleViolationException;
import in.coreeye.coreneha.kernel.Scope;
import in.coreeye.coreneha.kernel.TenantContext;
import in.coreeye.coreneha.org.OrgModels;
import in.coreeye.coreneha.org.OrgService;
import in.coreeye.coreneha.org.TenantLicensing;
import in.coreeye.coreneha.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Two hospital chains on one installation: neither sees the other's patients, staff, centres or
 * logins; the vendor's licence switches modules off, limits centres and suspends a chain.
 */
@SpringBootTest(properties = "CORE_NEHA_ENVIRONMENT=demo")
class MultiTenantTest extends PostgresTestBase {

    @Autowired BootstrapService bootstrap;
    @Autowired ClinicSeed clinic;
    @Autowired TenantLicensing licensing;
    @Autowired AccessControl access;
    @Autowired OrgService org;
    @Autowired JdbcClient jdbc;
    @MockitoBean IdentityProvisioner provisioner;

    @Test
    void chainsAreSeparateAndLicencesAreEnforced() {
        when(provisioner.create(any(), any(), any(), any(), anyBoolean()))
                .thenAnswer(inv -> new IdentityProvisioner.ProvisionedIdentity("kc-" + UUID.randomUUID(), "Temporary1234Ab"));
        String a = chain("hospital-a");
        String b = chain("hospital-b");
        UUID tenantA = licensing.tenantId(a);
        UUID tenantB = licensing.tenantId(b);
        UUID centreA = clinic.load(a).centreId();

        // Isolation: chain B sees none of chain A's rows, and the reverse.
        String[] tables = {"patient.patient", "eyecare.visit", "billing.bill", "org.centre", "workforce.person", "iam.user_account",
                "iam.role", "audit.audit_event"};
        for (String t : tables) {
            long inA = TenantContext.callAs(tenantA, () -> count("select count(*) from " + t));
            long aRowsSeenFromB = TenantContext.callAs(tenantB, () -> count("select count(*) from " + t + " where tenant_id = '" + tenantA + "'"));
            assertThat(inA).as(t + " in chain A").isPositive();
            assertThat(aRowsSeenFromB).as(t + " of chain A seen from chain B").isZero();
        }
        // Login ids are unique across chains, so the identity provider never mixes accounts.
        assertThat(count("select count(*) - count(distinct username) from iam.user_account")).isZero();

        Actor consultantA = TenantContext.callAs(tenantA, () -> actorByPosition("CONSULTANT_OPHTHALMOLOGIST"));
        Actor adminA = TenantContext.callAs(tenantA, () -> firstAdmin());
        TenantContext.callAs(tenantA, () -> {
            assertThat(access.holds(consultantA, Permission.EYE_CLINICAL_READ, Scope.centre(centreA))).isTrue();
            return null;
        });

        // The vendor removes eye care from chain A's licence: the consultant's role no longer grants it.
        licensing.setLicence(a, change(a, List.of("BILLING"), null, "Eye care not renewed"));
        TenantContext.callAs(tenantA, () -> {
            String modules = jdbc.sql("select coalesce(string_agg(module_code, ','), '') from org.tenant_module").query(String.class).single();
            Boolean licensed = jdbc.sql("select org.permission_licensed(:t, 'eye.clinical.read')").param("t", tenantA).query(Boolean.class).single();
            String grants = jdbc.sql("""
                    select string_agg(g.permission_code || '@' || org.permission_licensed(u.tenant_id, g.permission_code), ',')
                    from iam.role_assignment ra join iam.user_account u on u.id = ra.user_id
                    join iam.role_version rv on rv.role_id = ra.role_id and rv.status = 'PUBLISHED'
                    join iam.role_version_grant g on g.role_version_id = rv.id where ra.user_id = :u""")
                    .param("u", consultantA.userId()).query(String.class).single();
            assertThat(modules).as("licensed modules after the change").isEqualTo("BILLING");
            assertThat(licensed).as("eye licensed? grants=" + grants).isFalse();
            assertThat(access.holds(consultantA, Permission.EYE_CLINICAL_READ, Scope.centre(centreA))).as("grants=" + grants).isFalse();
            assertThat(access.holds(consultantA, Permission.PATIENT_READ, Scope.centre(centreA))).as("core stays").isTrue();
            assertThat(licensing.licence(adminA).modules()).containsExactly("BILLING");
            return null;
        });
        licensing.setLicence(a, change(a, TenantLicensing.ALL_MODULES, null, "Renewed"));

        // Centre limit: chain A has one centre; a licence for one centre refuses a second.
        licensing.setLicence(a, change(a, TenantLicensing.ALL_MODULES, 1, "Single-centre plan"));
        TenantContext.callAs(tenantA, () -> {
            UUID node = jdbc.sql("select node_id from org.centre where id = :c").param("c", centreA).query(UUID.class).single();
            UUID le = jdbc.sql("select legal_entity_id from org.centre where id = :c").param("c", centreA).query(UUID.class).single();
            assertThatThrownBy(() -> org.createCentre(adminA, new OrgModels.CreateCentre(node, le, "SECOND", "Second centre", "EYE_CLINIC", List.of())))
                    .isInstanceOf(RuleViolationException.class).hasMessageContaining("licence allows 1 centres");
            return null;
        });

        // Suspension: nobody in chain A holds anything, not even the administrator; chain B is unaffected.
        licensing.setStatus(a, false, "Unpaid invoice");
        TenantContext.callAs(tenantA, () -> {
            assertThat(access.holds(adminA, Permission.ORG_STRUCTURE_READ, null)).isFalse();
            assertThat(access.holds(consultantA, Permission.PATIENT_READ, Scope.centre(centreA))).isFalse();
            return null;
        });
        Actor adminB = TenantContext.callAs(tenantB, () -> firstAdmin());
        TenantContext.callAs(tenantB, () -> {
            assertThat(access.holds(adminB, Permission.ORG_STRUCTURE_READ, null)).isTrue();
            return null;
        });
        licensing.setStatus(a, true, "Paid");
        TenantContext.callAs(tenantA, () -> {
            assertThat(access.holds(adminA, Permission.ORG_STRUCTURE_READ, null)).isTrue();
            return null;
        });

        // The vendor's list shows both chains with licence and size only.
        assertThat(licensing.tenants()).extracting(TenantLicensing.TenantSummary::code).contains(a, b);
        // Every vendor change is in the chain's own audit trail.
        long vendorEvents = TenantContext.callAs(tenantA, () -> count("select count(*) from audit.audit_event where action like 'platform.%'"));
        assertThat(vendorEvents).isGreaterThanOrEqualTo(5);
    }

    private String chain(String prefix) {
        String code = prefix + "-" + UUID.randomUUID().toString().substring(0, 6);
        bootstrap.bootstrap(code, prefix + " test chain", true, List.of(
                new BootstrapService.Administrator("Asha", "Admin", "asha@" + code + ".test", null, null),
                new BootstrapService.Administrator("Bala", "Approver", "bala@" + code + ".test", null, null)));
        return code;
    }

    private TenantLicensing.LicenceChange change(String code, List<String> modules, Integer maxCentres, String reason) {
        return new TenantLicensing.LicenceChange("LIC-" + code.toUpperCase(), code + " Hospitals Pvt Ltd", "STANDARD",
                LocalDate.now().minusDays(1), LocalDate.now().plusYears(1), maxCentres, modules, reason);
    }

    private long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }

    private Actor firstAdmin() {
        UUID user = jdbc.sql("select user_id from iam.role_assignment where via_bootstrap order by requested_at limit 1")
                .query(UUID.class).single();
        return actor(user);
    }

    private Actor actorByPosition(String positionCode) {
        UUID user = jdbc.sql("""
                select u.id from iam.user_account u
                join workforce.posting p on p.person_id = u.person_id
                join workforce.establishment e on e.id = p.establishment_id
                join workforce.position_definition pos on pos.id = e.position_id
                where pos.code = :c limit 1""").param("c", positionCode).query(UUID.class).single();
        return actor(user);
    }

    private Actor actor(UUID userId) {
        return jdbc.sql("select id, tenant_id, person_id, username, access_version from iam.user_account where id = :u").param("u", userId)
                .query((rs, i) -> new Actor(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                        rs.getObject("person_id", UUID.class), rs.getString("username"), rs.getInt("access_version"))).single();
    }
}
