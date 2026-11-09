package in.coreeye.coreneha.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import in.coreeye.coreneha.billing.BillingModels;
import in.coreeye.coreneha.billing.BillingService;
import in.coreeye.coreneha.iam.BootstrapService;
import in.coreeye.coreneha.iam.IdentityProvisioner;
import in.coreeye.coreneha.inventory.InventoryModels;
import in.coreeye.coreneha.inventory.InventoryService;
import in.coreeye.coreneha.kernel.AccessDeniedException;
import in.coreeye.coreneha.kernel.Actor;
import in.coreeye.coreneha.kernel.RuleViolationException;
import in.coreeye.coreneha.kernel.TenantContext;
import in.coreeye.coreneha.optical.OpticalModels;
import in.coreeye.coreneha.optical.OpticalService;
import in.coreeye.coreneha.org.TenantLicensing;
import in.coreeye.coreneha.pharmacy.PharmacyModels;
import in.coreeye.coreneha.pharmacy.PharmacyService;
import in.coreeye.coreneha.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.context.request.RequestContextHolder;

/** Pharmacy, optical shop, optical lab and stores at the eye clinic, built through the normal rules, then the rules checked. */
@SpringBootTest(properties = "CORE_NEHA_ENVIRONMENT=demo")
class SupplyTest extends PostgresTestBase {

    @Autowired BootstrapService bootstrap;
    @Autowired ClinicSeed clinic;
    @Autowired ClinicStaff staff;
    @Autowired ClinicSupply supply;
    @Autowired InventoryService inventory;
    @Autowired PharmacyService pharmacy;
    @Autowired OpticalService optical;
    @Autowired BillingService billing;
    @Autowired TenantLicensing licensing;
    @Autowired JdbcClient jdbc;
    @MockitoBean IdentityProvisioner provisioner;

    @Test
    void stockPharmacyAndOpticalFollowTheRules() {
        when(provisioner.create(any(), any(), any(), any(), anyBoolean()))
                .thenAnswer(inv -> new IdentityProvisioner.ProvisionedIdentity("kc-" + UUID.randomUUID(), "Temporary1234Ab"));
        String code = "supply-" + UUID.randomUUID().toString().substring(0, 8);
        UUID tenant = bootstrap.bootstrap(code, "Supply test group", true, List.of(
                new BootstrapService.Administrator("Asha", "Admin", "asha@" + code + ".test", null, null),
                new BootstrapService.Administrator("Bala", "Approver", "bala@" + code + ".test", null, null))).tenantId();
        UUID centre = clinic.load(code).centreId();
        staff.extend(code);
        ClinicSupply.Summary s = supply.load(code);
        assertThat(s.stores()).isEqualTo(4);
        assertThat(s.receipts()).isEqualTo(3);
        assertThat(s.prescriptions()).isEqualTo(3);
        assertThat(s.sales()).isEqualTo(2);
        assertThat(s.spectacleOrders()).isEqualTo(3);
        assertThat(s.labJobs()).isEqualTo(2);
        assertThatThrownBy(() -> supply.load(code)).isInstanceOf(IllegalStateException.class);

        TenantContext.callAs(tenant, () -> {
            // The ledger explains every batch: received minus moved out equals what is on hand.
            assertThat(count("""
                    select count(*) from inventory.stock_batch b
                    where b.qty_on_hand <> (select coalesce(sum(m.quantity), 0) from inventory.stock_movement m where m.batch_id = b.id)""")).isZero();
            // First expiry first out: the post-operative drops came from the batch expiring soonest.
            assertThat(count("""
                    select count(*) from pharmacy.sale_line l join inventory.stock_batch b on b.id = l.batch_id
                    where b.batch_no like '%A'""")).isPositive();
            UUID pharm = id("select id from inventory.store where code = 'PHARM' and centre_id = '" + centre + "'");
            UUID timolol = id("select id from inventory.item where code = 'TIMOLOL-ED'");
            UUID patient = id("select patient_id from eyecare.visit where centre_id = '" + centre + "' limit 1");
            Actor pharmacist = staff("PHARMACIST");
            // A Schedule H drug is not sold without a prescription.
            assertThatThrownBy(() -> pharmacy.dispense(pharmacist, new PharmacyModels.Dispense(pharm, patient, null,
                    List.of(new PharmacyModels.DispenseLineInput(timolol, 1, null)))))
                    .isInstanceOf(RuleViolationException.class).hasMessageContaining("Schedule H");
            // A pharmacy charge cannot be removed from the bill at the billing counter.
            UUID sale = id("select id from pharmacy.sale where prescription_id is not null limit 1");
            var bill = billing.bill(staff("FRONT_OFFICE_SUPERVISOR"), id("select bill_id from pharmacy.sale where id = '" + sale + "'"));
            assertThatThrownBy(() -> billing.removeLine(staff("FRONT_OFFICE_SUPERVISOR"), bill.bill().id(),
                    new BillingModels.RemoveLine(bill.lines().getFirst().id(), bill.bill().version())))
                    .isInstanceOf(RuleViolationException.class).hasMessageContaining("pharmacy or optical");

            // Stock write-off: the store keeper cannot approve; the stores manager can.
            var pending = inventory.adjustments(staff("STORES_MANAGER"), centre, "PENDING").getFirst();
            assertThatThrownBy(() -> inventory.decideAdjustment(staff("STORE_KEEPER"), pending.id(),
                    new InventoryModels.DecideAdjustment(true, "ok", pending.version()))).isInstanceOf(AccessDeniedException.class);
            int before = qty(pending.batchId());
            inventory.decideAdjustment(staff("STORES_MANAGER"), pending.id(), new InventoryModels.DecideAdjustment(true, "Checked", pending.version()));
            assertThat(qty(pending.batchId())).isEqualTo(before - 1);

            // Spectacles: the order that came back from the lab is not delivered until the bill is paid.
            Actor sales = staff("OPTICAL_SALES_EXECUTIVE");
            var ready = optical.orders(sales, centre, "READY").getFirst();
            assertThat(ready.labJobStatus()).isEqualTo("DISPATCHED");
            assertThatThrownBy(() -> optical.deliver(sales, ready.id(), new OpticalModels.OrderAction(ready.version(), null)))
                    .isInstanceOf(RuleViolationException.class).hasMessageContaining("Collect the balance");
            var b = billing.bill(sales, ready.billId());
            billing.collect(sales, ready.billId(), new BillingModels.CollectPayment("UPI", b.bill().balance(), "UPI-DEMO-1", b.bill().version()));
            var delivered = optical.deliver(sales, ready.id(), new OpticalModels.OrderAction(optical.getOrder(sales, ready.id()).version(), null));
            assertThat(delivered.status()).isEqualTo("DELIVERED");
            // The lab job's quality check was done by someone other than the fitter.
            assertThat(count("select count(*) from optical.lab_job where qc_result = 'PASS' and qc_by <> fitted_by")).isEqualTo(1);
            // Lens blanks were consumed from the lab store (a pair per surfacing).
            assertThat(count("select -coalesce(sum(quantity), 0) from inventory.stock_movement where movement_type = 'CONSUMPTION'")).isEqualTo(4);
            // Cancelling an order puts the frame back into stock and cancels its unpaid bill.
            var inLab = optical.orders(sales, centre, "IN_LAB").getFirst();
            UUID frameBatch = id("select frame_batch_id from optical.spectacle_order where id = '" + inLab.id() + "'");
            int frames = qty(frameBatch);
            optical.cancel(sales, inLab.id(), new OpticalModels.OrderAction(inLab.version(), "Patient changed their mind"));
            assertThat(qty(frameBatch)).isEqualTo(frames + 1);
            assertThat(billing.bill(sales, inLab.billId()).bill().status()).isEqualTo("CANCELLED");
            return null;
        });

        // Without a pharmacy licence the pharmacist cannot dispense.
        licensing.setLicence(code, new TenantLicensing.LicenceChange("LIC-" + code.toUpperCase(), "Supply test group", "STANDARD",
                LocalDate.now().minusDays(1), null, null, List.of("EYE_CARE", "BILLING", "INVENTORY", "OPTICAL", "OPTICAL_LAB"), "No pharmacy"));
        RequestContextHolder.resetRequestAttributes();
        TenantContext.callAs(tenant, () -> {
            UUID pharm = id("select id from inventory.store where code = 'PHARM'");
            UUID cmc = id("select id from inventory.item where code = 'CMC-ED'");
            UUID patient = id("select patient_id from eyecare.visit limit 1");
            assertThatThrownBy(() -> pharmacy.dispense(staff("PHARMACIST"), new PharmacyModels.Dispense(pharm, patient, null,
                    List.of(new PharmacyModels.DispenseLineInput(cmc, 1, null))))).isInstanceOf(AccessDeniedException.class);
            return null;
        });
    }

    private int qty(UUID batchId) {
        return jdbc.sql("select qty_on_hand from inventory.stock_batch where id = :b").param("b", batchId).query(Integer.class).single();
    }

    private long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }

    private UUID id(String sql) {
        return jdbc.sql(sql).query(UUID.class).list().getFirst();
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
