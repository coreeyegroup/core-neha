package in.coreeye.coreneha.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import in.coreeye.coreneha.diagnostics.DiagnosticsModels;
import in.coreeye.coreneha.diagnostics.DiagnosticsService;
import in.coreeye.coreneha.diagnostics.ImagingAccess;
import in.coreeye.coreneha.iam.AccessGuideService;
import in.coreeye.coreneha.iam.BootstrapService;
import in.coreeye.coreneha.iam.IdentityProvisioner;
import in.coreeye.coreneha.kernel.AccessDeniedException;
import in.coreeye.coreneha.kernel.Actor;
import in.coreeye.coreneha.kernel.CoreNehaProperties;
import in.coreeye.coreneha.kernel.RuleViolationException;
import in.coreeye.coreneha.kernel.TenantContext;
import in.coreeye.coreneha.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Laboratory and imaging at the eye clinic: flags, release, validation by a second person, and who may open which images. */
@SpringBootTest(properties = "CORE_NEHA_ENVIRONMENT=demo")
class DiagnosticsTest extends PostgresTestBase {

    @Autowired BootstrapService bootstrap;
    @Autowired ClinicSeed clinic;
    @Autowired ClinicStaff staff;
    @Autowired ClinicDiagnostics demo;
    @Autowired DiagnosticsService diagnostics;
    @Autowired ImagingAccess imaging;
    @Autowired AccessGuideService guide;
    @Autowired DemoInspector inspector;
    @Autowired CoreNehaProperties props;
    @Autowired JdbcClient jdbc;
    @MockitoBean IdentityProvisioner provisioner;

    @Test
    void laboratoryAndImagingFollowTheRules() {
        when(provisioner.create(any(), any(), any(), any(), anyBoolean()))
                .thenAnswer(inv -> new IdentityProvisioner.ProvisionedIdentity("kc-" + UUID.randomUUID(), "Temporary1234Ab"));
        String code = "dx-" + UUID.randomUUID().toString().substring(0, 8);
        UUID tenant = bootstrap.bootstrap(code, "Diagnostics test group", true, List.of(
                new BootstrapService.Administrator("Asha", "Admin", "asha@" + code + ".test", null, null),
                new BootstrapService.Administrator("Bala", "Approver", "bala@" + code + ".test", null, null))).tenantId();
        UUID centre = clinic.load(code).centreId();
        staff.extend(code);
        ClinicDiagnostics.Summary s = demo.load(code);
        assertThat(s.labTests()).isEqualTo(10);
        assertThat(s.procedures()).isEqualTo(8);
        assertThat(s.orders()).isEqualTo(11);
        assertThat(s.validated()).isEqualTo(3);
        assertThat(s.verified()).isEqualTo(1);
        assertThat(s.images()).isFalse();

        TenantContext.callAs(tenant, () -> {
            // Flags from reference and critical ranges.
            assertThat(flag("FBS")).isEqualTo("H");
            assertThat(flag("RBS")).isEqualTo("HH");
            assertThat(count("select count(*) from diagnostics.diagnostic_order where has_critical")).isEqualTo(1);
            // Every order of a request is on one bill.
            assertThat(count("select count(distinct bill_id) from diagnostics.diagnostic_order")).isEqualTo(3);

            // The doctor sees released results only.
            Actor doctor = staff("CONSULTANT_OPHTHALMOLOGIST");
            UUID preOpPatient = id("select patient_id from diagnostics.diagnostic_order o join diagnostics.lab_test t on t.id = o.lab_test_id where t.code = 'FBS'");
            var mine = diagnostics.forPatient(doctor, preOpPatient);
            assertThat(mine).filteredOn(o -> "FBS".equals(o.testCode())).first().satisfies(o -> assertThat(o.results()).isNotEmpty());
            assertThat(mine).filteredOn(o -> "CBC".equals(o.testCode())).first().satisfies(o -> {
                assertThat(o.released()).isFalse();
                assertThat(o.results()).isEmpty();
            });

            // Results are validated by someone other than the person who entered them.
            Actor pathologist = staff("PATHOLOGIST");
            Actor phlebotomist = staff("PHLEBOTOMIST");
            UUID urine = id("select o.id from diagnostics.diagnostic_order o join diagnostics.lab_test t on t.id = o.lab_test_id where t.code = 'URINE'");
            var u = diagnostics.collect(phlebotomist, urine, new DiagnosticsModels.Step(version(urine), null));
            u = diagnostics.receive(pathologist, u.id(), new DiagnosticsModels.Step(u.version(), null));
            var params = u.results();
            u = diagnostics.enterResults(pathologist, u.id(), new DiagnosticsModels.EnterResults(u.version(), List.of(
                    new DiagnosticsModels.ResultInput(params.get(0).parameterId(), null, "Nil"),
                    new DiagnosticsModels.ResultInput(params.get(1).parameterId(), null, "Trace"),
                    new DiagnosticsModels.ResultInput(params.get(2).parameterId(), new BigDecimal("2"), null))));
            UUID urineId = u.id();
            int v = u.version();
            assertThatThrownBy(() -> diagnostics.validate(pathologist, urineId, new DiagnosticsModels.Step(v, null)))
                    .isInstanceOf(AccessDeniedException.class).hasMessageContaining("another person");
            assertThat(u.results()).filteredOn(r -> "UPRO".equals(r.code())).first().satisfies(r -> assertThat(r.flag()).isEqualTo("A"));
            // The phlebotomist cannot validate at all.
            assertThatThrownBy(() -> diagnostics.validate(phlebotomist, urineId, new DiagnosticsModels.Step(v, null)))
                    .isInstanceOf(AccessDeniedException.class);

            // Without an image archive, the technician must give the Study Instance UID.
            UUID ct = id("select o.id from diagnostics.diagnostic_order o join diagnostics.imaging_procedure p on p.id = o.procedure_id where p.code = 'CTORBIT'");
            assertThatThrownBy(() -> diagnostics.acquire(staff("RADIOGRAPHER"), ct, new DiagnosticsModels.Acquire(version(ct), null, null)))
                    .isInstanceOf(RuleViolationException.class).hasMessageContaining("Study Instance UID");

            // Who may open which images (the gateway asks before every request).
            String uid = jdbc.sql("""
                    select o.study_instance_uid from diagnostics.diagnostic_order o join diagnostics.imaging_procedure p on p.id = o.procedure_id
                    where p.code = 'CXR'""").query(String.class).single();
            String pacs = props.basePath() + "/api/pacs/";
            Actor radiologist = staff("RADIOLOGIST");
            Actor pharmacist = staff("PHARMACIST");
            assertThat(imaging.allows(radiologist, pacs + "dicom-web/studies/" + uid + "/series")).isTrue();
            assertThat(imaging.allows(radiologist, pacs + "dicom-web/studies?StudyInstanceUID=" + uid)).isTrue();
            assertThat(imaging.allows(doctor, pacs + "dicom-web/studies/" + uid + "/metadata")).isTrue();
            assertThat(imaging.allows(radiologist, pacs + "ohif/viewer")).isTrue();
            assertThat(imaging.allows(pharmacist, pacs + "dicom-web/studies/" + uid + "/series")).isFalse();
            assertThat(imaging.allows(pharmacist, pacs + "ohif/viewer")).isFalse();
            assertThat(imaging.allows(radiologist, pacs + "dicom-web/studies")).as("no listing of the archive").isFalse();
            assertThat(imaging.allows(radiologist, pacs + "dicom-web/studies/1.2.3.4/series")).as("a study with no order").isFalse();
            assertThat(imaging.allows(radiologist, pacs + "app/explorer.html")).as("archive's own pages").isFalse();
            assertThat(imaging.allows(radiologist, pacs + "patients")).as("archive REST API").isFalse();
            assertThat(imaging.allows(radiologist, pacs + "dicom-web/studies/" + uid + "/../../patients")).isFalse();

            // The access guide: a pharmacist sees their own access and the catalogue, not the roles of others.
            var mineGuide = guide.guide(pharmacist);
            assertThat(mineGuide.full()).isFalse();
            assertThat(mineGuide.roles()).isEmpty();
            assertThat(mineGuide.positions()).isEmpty();
            assertThat(mineGuide.permissions()).hasSize(68).allSatisfy(p -> assertThat(p.roles()).isEmpty());
            assertThat(mineGuide.permissions()).filteredOn(p -> p.code().equals("pharmacy.dispense")).first()
                    .satisfies(p -> assertThat(p.heldAt()).extracting(AccessGuideService.HeldAt::role).contains("EYE_PHARMACIST"));
            assertThat(mineGuide.permissions()).filteredOn(p -> p.code().equals("lab.validate")).first()
                    .satisfies(p -> assertThat(p.heldAt()).isEmpty());
            assertThat(mineGuide.modules()).extracting(AccessGuideService.ModuleInfo::code).startsWith("CORE").contains("RADIOLOGY");
            // A group-wide administrator sees the whole model: roles, positions and who holds what.
            Actor admin = admin();
            var all = guide.guide(admin);
            assertThat(all.full()).isTrue();
            assertThat(all.roles()).filteredOn(r -> r.code().equals("EYE_PHARMACIST")).first().satisfies(r -> {
                assertThat(r.permissions()).contains("pharmacy.dispense");
                assertThat(r.positions()).contains("Pharmacist");
                assertThat(r.activeAssignments()).isGreaterThanOrEqualTo(2);
            });
            assertThat(all.positions()).filteredOn(p -> p.code().equals("RADIOLOGIST")).first()
                    .satisfies(p -> assertThat(p.roles()).contains("EYE_RADIOLOGIST", "DIAGNOSTICS_CATALOGUE"));
            assertThat(all.permissions()).filteredOn(p -> p.code().equals("lab.validate")).first()
                    .satisfies(p -> assertThat(p.roles()).contains("EYE_PATHOLOGIST"));
            return null;
        });

        // The developer's demonstration inspector: every permission at whole-organisation scope, through the normal controls.
        DemoInspector.Summary first = inspector.load(code);
        assertThat(first.created()).isTrue();
        assertThat(first.permissions()).isEqualTo(68);
        assertThat(inspector.load(code).created()).as("safe to run again").isFalse();
        TenantContext.callAs(tenant, () -> {
            Actor dev = actorOf(id("select id from iam.user_account where username = '" + first.username() + "'"));
            var g = guide.guide(dev);
            assertThat(g.full()).isTrue();
            assertThat(g.permissions()).allSatisfy(p -> assertThat(p.heldAt()).extracting(AccessGuideService.HeldAt::scopeType).containsOnly("TENANT"));
            assertThat(imaging.allows(dev, props.basePath() + "/api/pacs/ohif/viewer")).isTrue();
            assertThat(count("select count(*) from iam.role_assignment a join iam.role r on r.id = a.role_id where r.code = 'DEMO_INSPECTOR' and a.status = 'ACTIVE'"))
                    .isEqualTo(1);
            return null;
        });
    }

    private String flag(String test) {
        return jdbc.sql("""
                select r.flag from diagnostics.lab_result r join diagnostics.diagnostic_order o on o.id = r.order_id
                join diagnostics.lab_test t on t.id = o.lab_test_id where t.code = :t""").param("t", test).query(String.class).single();
    }

    private int version(UUID id) {
        return jdbc.sql("select version from diagnostics.diagnostic_order where id = :id").param("id", id).query(Integer.class).single();
    }

    private long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }

    private UUID id(String sql) {
        return jdbc.sql(sql).query(UUID.class).list().getFirst();
    }

    private Actor admin() {
        UUID user = id("select user_id from iam.role_assignment where via_bootstrap and kind = 'ACCESS' order by requested_at, id");
        return actorOf(user);
    }

    private Actor actorOf(UUID user) {
        return jdbc.sql("select id, tenant_id, person_id, username, access_version from iam.user_account where id = :u").param("u", user)
                .query((rs, i) -> new Actor(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                        rs.getObject("person_id", UUID.class), rs.getString("username"), rs.getInt("access_version"))).single();
    }

    private Actor staff(String positionCode) {
        UUID user = id("""
                select u.id from iam.user_account u join workforce.posting p on p.person_id = u.person_id
                join workforce.establishment e on e.id = p.establishment_id join workforce.position_definition pos on pos.id = e.position_id
                where pos.code = '%s' order by u.username""".formatted(positionCode));
        return jdbc.sql("select id, tenant_id, person_id, username, access_version from iam.user_account where id = :u").param("u", user)
                .query((rs, i) -> new Actor(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                        rs.getObject("person_id", UUID.class), rs.getString("username"), rs.getInt("access_version"))).single();
    }
}
