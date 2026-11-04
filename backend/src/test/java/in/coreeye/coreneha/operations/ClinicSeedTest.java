package in.coreeye.coreneha.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import in.coreeye.coreneha.billing.AccountsService;
import in.coreeye.coreneha.billing.BillingModels;
import in.coreeye.coreneha.billing.BillingService;
import in.coreeye.coreneha.eyecare.EyeModels;
import in.coreeye.coreneha.eyecare.SurgeryService;
import in.coreeye.coreneha.eyecare.VisitService;
import in.coreeye.coreneha.iam.BootstrapService;
import in.coreeye.coreneha.iam.IdentityProvisioner;
import in.coreeye.coreneha.kernel.AccessDeniedException;
import in.coreeye.coreneha.kernel.Actor;
import in.coreeye.coreneha.kernel.BusinessTime;
import in.coreeye.coreneha.kernel.RuleViolationException;
import in.coreeye.coreneha.kernel.TenantContext;
import in.coreeye.coreneha.patient.PatientService;
import in.coreeye.coreneha.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * The eye clinic demonstration: six departments, two positions each, twelve logins,
 * and a working day built by the clinic's own staff through the normal rules. Then the
 * department boundaries and segregation of duties are checked on that data.
 */
@SpringBootTest(properties = "CORE_NEHA_ENVIRONMENT=demo")
class ClinicSeedTest extends PostgresTestBase {

    @Autowired ClinicSeed seed;
    @Autowired BootstrapService bootstrap;
    @Autowired JdbcClient jdbc;
    @Autowired PatientService patients;
    @Autowired VisitService visits;
    @Autowired SurgeryService surgeries;
    @Autowired BillingService billing;
    @Autowired AccountsService accounts;
    @MockitoBean IdentityProvisioner provisioner;

    @Test
    void buildsTheClinicAndKeepsDepartmentsApart() {
        when(provisioner.create(any(), any(), any(), any(), anyBoolean()))
                .thenAnswer(inv -> new IdentityProvisioner.ProvisionedIdentity("kc-" + UUID.randomUUID(), "Temporary1234Ab"));
        String code = "clinic-" + UUID.randomUUID().toString().substring(0, 8);
        UUID tenant = bootstrap.bootstrap(code, "Clinic test group", true, List.of(
                new BootstrapService.Administrator("Asha", "Admin", "asha@" + code + ".test", "asha-" + code, null),
                new BootstrapService.Administrator("Bala", "Approver", "bala@" + code + ".test", "bala-" + code, null))).tenantId();

        ClinicSeed.Summary summary = seed.load(code);
        assertThat(summary.departments()).isEqualTo(6);
        assertThat(summary.positions()).isEqualTo(12);
        assertThat(summary.logins()).hasSize(12);
        assertThat(summary.patients()).isEqualTo(22);

        TenantContext.callAs(tenant, () -> {
            UUID centre = summary.centreId();
            Actor reception = actor("priya.reception");
            Actor supervisor = actor("ramesh.frontoffice");
            Actor optometrist = actor("sunita.optometry");
            Actor surgeon = actor("dr.arjun");
            Actor accountant = actor("ganesh.accounts");
            Actor manager = actor("shobha.accounts");
            Actor admin = actor("asha-" + code);

            // A day at every stage
            EyeModels.ClinicBoard board = visits.board(reception, centre, BusinessTime.today());
            assertThat(board.visits()).isEqualTo(15);
            assertThat(board.waitingOptometry()).isEqualTo(3);
            assertThat(board.waitingConsultation()).isEqualTo(2);
            assertThat(board.waitingCounselling()).isEqualTo(1);
            assertThat(board.waitingCheckout()).isEqualTo(3);
            assertThat(board.completed()).isEqualTo(6);
            assertThat(board.surgeriesCompletedToday()).isEqualTo(1);
            assertThat(board.surgeriesReadyToday()).isEqualTo(1);
            assertThat(board.surgeriesAdvised()).isEqualTo(1);
            assertThat(board.surgeriesToSchedule()).isEqualTo(1);
            assertThat(board.surgeriesUpcoming()).isEqualTo(1);
            // Each of the twelve staff has exactly one centre-scoped, independently approved assignment
            assertThat(jdbc.sql("""
                    select count(*) from iam.role_assignment where scope_id = :c and status = 'ACTIVE'
                    and decided_by is not null and decided_by <> requested_by""").param("c", centre).query(Integer.class).single())
                    .isEqualTo(12);

            // Front office sees the stage but not the findings
            EyeModels.VisitView completed = visits.visits(reception, centre, null, "COMPLETED").getFirst();
            EyeModels.VisitChart frontDesk = visits.chart(reception, completed.id());
            assertThat(frontDesk.clinicalVisible()).isFalse();
            assertThat(frontDesk.optometry()).isNull();
            assertThat(visits.chart(optometrist, completed.id()).optometry()).isNotNull();
            EyeModels.VisitView waiting = visits.visits(reception, centre, null, "OPTOMETRY").getFirst();
            assertThatThrownBy(() -> visits.saveOptometry(reception, waiting.id(), emptyOptometry(true)))
                    .isInstanceOf(AccessDeniedException.class);

            // Accounts and administrators do not see patients
            assertThatThrownBy(() -> patients.search(accountant, null, 10)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> patients.search(admin, null, 10)).isInstanceOf(AccessDeniedException.class);

            // A discount needs someone other than the requester; payment waits for the decision
            BillingModels.BillView pending = billing.pendingDiscounts(supervisor, centre).getFirst();
            assertThatThrownBy(() -> billing.decideDiscount(reception, pending.id(), new BillingModels.Decision(true, null, pending.version())))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> billing.collect(reception, pending.id(),
                    new BillingModels.CollectPayment("CASH", pending.netAmount(), null, pending.version())))
                    .isInstanceOf(RuleViolationException.class);
            BillingModels.BillView approved = billing.decideDiscount(supervisor, pending.id(),
                    new BillingModels.Decision(true, "Senior citizen", pending.version())).bill();
            assertThat(approved.discountAmount()).isEqualByComparingTo("300");
            BillingModels.BillView paid = billing.collect(reception, approved.id(),
                    new BillingModels.CollectPayment("CASH", approved.balance(), null, approved.version())).bill();
            assertThat(paid.status()).isEqualTo("PAID");
            EyeModels.VisitView atCheckout = visits.chart(reception, paid.visitRef()).visit();
            assertThat(visits.checkout(reception, paid.visitRef(), new EyeModels.Checkout(null, atCheckout.version())).stage())
                    .isEqualTo("COMPLETED");

            // A visit with an unpaid bill cannot be checked out
            EyeModels.VisitView unpaid = visits.visits(reception, centre, null, "CHECKOUT").getFirst();
            assertThatThrownBy(() -> visits.checkout(reception, unpaid.id(), new EyeModels.Checkout(null, unpaid.version())))
                    .isInstanceOf(RuleViolationException.class);

            // Surgery cannot start before the pre-operative checklist
            EyeModels.SurgeryView scheduled = surgeries.list(surgeon, centre, "UPCOMING", BusinessTime.today()).stream()
                    .filter(s -> "SCHEDULED".equals(s.status())).findFirst().orElseThrow();
            assertThatThrownBy(() -> surgeries.operate(surgeon, scheduled.id(),
                    new EyeModels.Operation("TOPICAL", null, "notes", null, null, true, scheduled.version())))
                    .isInstanceOf(RuleViolationException.class);

            // Day close: the accountant submits, only the manager approves
            var preview = accounts.preview(accountant, centre, BusinessTime.today());
            assertThat(preview.systemCash()).isPositive();
            BillingModels.DayCloseView submitted = accounts.submit(accountant, centre,
                    new BillingModels.SubmitDayClose(BusinessTime.today(), preview.systemCash(), null));
            assertThat(submitted.variance()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThatThrownBy(() -> accounts.decide(accountant, submitted.id(), new BillingModels.Decision(true, null, submitted.version())))
                    .isInstanceOf(AccessDeniedException.class);
            assertThat(accounts.decide(manager, submitted.id(), new BillingModels.Decision(true, "Counted", submitted.version())).status())
                    .isEqualTo("APPROVED");
            assertThat(accounts.collections(manager, centre, null, null).collected()).isPositive();
            return null;
        });
    }

    private Actor actor(String username) {
        return jdbc.sql("select id, tenant_id, person_id, username, access_version from iam.user_account where username = :u")
                .param("u", username)
                .query((rs, i) -> new Actor(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                        rs.getObject("person_id", UUID.class), rs.getString("username"), rs.getInt("access_version")))
                .single();
    }

    private static EyeModels.SaveOptometry emptyOptometry(boolean complete) {
        return new EyeModels.SaveOptometry("6/6", "6/6", null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, complete, null);
    }
}
