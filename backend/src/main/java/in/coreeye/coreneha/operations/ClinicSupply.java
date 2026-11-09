package in.coreeye.coreneha.operations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import in.coreeye.coreneha.inventory.InventoryModels;
import in.coreeye.coreneha.inventory.InventoryService;
import in.coreeye.coreneha.kernel.Actor;
import in.coreeye.coreneha.kernel.BusinessTime;
import in.coreeye.coreneha.kernel.NotFoundException;
import in.coreeye.coreneha.kernel.TenantContext;
import in.coreeye.coreneha.optical.OpticalModels;
import in.coreeye.coreneha.optical.OpticalService;
import in.coreeye.coreneha.org.OrgDirectory;
import in.coreeye.coreneha.pharmacy.PharmacyModels;
import in.coreeye.coreneha.pharmacy.PharmacyService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Pharmacy, optical shop, optical lab and stores for the Vijaya Eye Clinic demonstration: the
 * stores, an item master, opening stock received from suppliers, and a working day done by the
 * clinic's own staff through the normal rules (prescription and dispensing, an over-the-counter
 * sale, spectacle orders through the lab with quality check, a stock write-off awaiting approval).
 * Run after clinic-staff; refuses if the clinic already has stores.
 */
@Component
class ClinicSupply {

    record Summary(int stores, int items, int receipts, int prescriptions, int sales, int spectacleOrders, int labJobs) {
    }

    /** Code, name, category, unit, MRP, schedule (drugs), generic name, lens type, lens index, coating. GST rates are illustrative. */
    static final String[][] ITEMS = {
            {"MOXI-ED", "Moxifloxacin 0.5% eye drops, 5 ml", "DRUG", "bottle", "150", "H", "Moxifloxacin"},
            {"TOBRADEXA-ED", "Tobramycin 0.3% and dexamethasone 0.1% eye drops, 5 ml", "DRUG", "bottle", "120", "H", "Tobramycin + dexamethasone"},
            {"PRED-ED", "Prednisolone acetate 1% eye drops, 10 ml", "DRUG", "bottle", "95", "H", "Prednisolone acetate"},
            {"NEPAF-ED", "Nepafenac 0.1% eye drops, 5 ml", "DRUG", "bottle", "260", "H", "Nepafenac"},
            {"CMC-ED", "Carboxymethylcellulose 0.5% lubricant eye drops, 10 ml", "DRUG", "bottle", "180", "OTC", "Carboxymethylcellulose"},
            {"HYAL-ED", "Sodium hyaluronate 0.1% eye drops, 10 ml", "DRUG", "bottle", "320", "OTC", "Sodium hyaluronate"},
            {"TIMOLOL-ED", "Timolol 0.5% eye drops, 5 ml", "DRUG", "bottle", "60", "H", "Timolol maleate"},
            {"LATANO-ED", "Latanoprost 0.005% eye drops, 2.5 ml", "DRUG", "bottle", "450", "H", "Latanoprost"},
            {"BRIMO-ED", "Brimonidine 0.2% eye drops, 5 ml", "DRUG", "bottle", "210", "H", "Brimonidine tartrate"},
            {"TROPI-ED", "Tropicamide 1% and phenylephrine 5% eye drops, 5 ml", "DRUG", "bottle", "90", "H", "Tropicamide + phenylephrine"},
            {"ATROPINE-ED", "Atropine 1% eye drops, 5 ml", "DRUG", "bottle", "45", "H", "Atropine sulphate"},
            {"ACETAZ-TAB", "Acetazolamide 250 mg tablets, strip of 10", "DRUG", "strip", "70", "H", "Acetazolamide"},
            {"PARA-TAB", "Paracetamol 500 mg tablets, strip of 10", "DRUG", "strip", "25", "OTC", "Paracetamol"},
            {"CIPRO-OINT", "Ciprofloxacin 0.3% eye ointment, 5 g", "DRUG", "tube", "55", "H", "Ciprofloxacin"},
            {"EYE-SHIELD", "Eye shield, post-operative", "CONSUMABLE", "piece", "40"},
            {"EYE-PAD", "Sterile eye pads, pack of 10", "CONSUMABLE", "pack", "60"},
            {"FR-TITAN-01", "Titanium full-rim frame, black, 52-18", "FRAME", "piece", "2500"},
            {"FR-TR90-02", "TR90 half-rim frame, gunmetal, 54-17", "FRAME", "piece", "1800"},
            {"FR-ACET-03", "Acetate full-rim frame, tortoise, 50-19", "FRAME", "piece", "2200"},
            {"FR-KIDS-04", "Children's flexible frame, blue, 44-16", "FRAME", "piece", "1200"},
            {"FR-RIMLESS-05", "Rimless frame, silver, 53-17", "FRAME", "piece", "3200"},
            {"LN-SV156-HMC", "Single vision 1.56, hard multi-coated (pair)", "SPECTACLE_LENS", "pair", "1200", null, null, "SINGLE_VISION", "1.56", "HMC"},
            {"LN-SV160-BLUE", "Single vision 1.60, blue-cut coating (pair)", "SPECTACLE_LENS", "pair", "2400", null, null, "SINGLE_VISION", "1.60", "Blue-cut"},
            {"LN-SV167-HMC", "Single vision 1.67 thin, multi-coated (pair)", "SPECTACLE_LENS", "pair", "3800", null, null, "SINGLE_VISION", "1.67", "HMC"},
            {"LN-BF156-HMC", "Bifocal D-top 1.56, multi-coated (pair)", "SPECTACLE_LENS", "pair", "1800", null, null, "BIFOCAL", "1.56", "HMC"},
            {"LN-PAL160-HMC", "Progressive 1.60, multi-coated (pair)", "SPECTACLE_LENS", "pair", "7500", null, null, "PROGRESSIVE", "1.60", "HMC"},
            {"LN-SV150-STK", "Single vision 1.50 stock lenses, -2.00 to +2.00 (pair)", "SPECTACLE_LENS", "pair", "600", null, null, "SINGLE_VISION", "1.50", "Hard coat"},
            {"BL-156-SV", "Semi-finished lens blank 1.56, single vision", "LENS_BLANK", "piece", "180"},
            {"BL-160-SV", "Semi-finished lens blank 1.60, single vision", "LENS_BLANK", "piece", "260"},
            {"BL-167-SV", "Semi-finished lens blank 1.67, single vision", "LENS_BLANK", "piece", "420"},
            {"BL-156-PAL", "Semi-finished lens blank 1.56, progressive", "LENS_BLANK", "piece", "650"},
    };

    private final JdbcClient jdbc;
    private final OrgDirectory directory;
    private final InventoryService inventory;
    private final PharmacyService pharmacy;
    private final OpticalService optical;
    private final TransactionTemplate tx;

    ClinicSupply(JdbcClient jdbc, OrgDirectory directory, InventoryService inventory, PharmacyService pharmacy, OpticalService optical,
                 PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.directory = directory;
        this.inventory = inventory;
        this.pharmacy = pharmacy;
        this.optical = optical;
        this.tx = new TransactionTemplate(transactionManager);
    }

    Summary load(String tenantCode) {
        UUID tenant = directory.tenantIdByCode(tenantCode)
                .orElseThrow(() -> new NotFoundException("Tenant '" + tenantCode + "' not found"));
        return TenantContext.callAs(tenant, () -> tx.execute(status -> build(tenant)));
    }

    private Summary build(UUID tenant) {
        UUID centre = jdbc.sql("select id from org.centre where code = :c").param("c", ClinicSeed.CENTRE_CODE).query(UUID.class).optional()
                .orElseThrow(() -> new NotFoundException(ClinicSeed.CENTRE_NAME + " not found; run ops/clinic-seed.sh first"));
        boolean exists = jdbc.sql("select exists(select 1 from inventory.store where centre_id = :c)").param("c", centre)
                .query(Boolean.class).single();
        if (exists) {
            throw new IllegalStateException(ClinicSeed.CENTRE_NAME + " already has stores; the supply demonstration loads only once");
        }
        Actor storesManager = staff("STORES_MANAGER");
        Actor storeKeeper = staff("STORE_KEEPER");

        // Stores, by the stores manager.
        Map<String, UUID> stores = new HashMap<>();
        for (String[] s : new String[][] {{"PHARM", "Pharmacy", "PHARMACY"}, {"OPTICAL", "Optical shop", "OPTICAL"},
                {"OPTLAB", "Optical lab", "OPTICAL_LAB"}, {"MAIN", "Main store", "CENTRAL"}}) {
            stores.put(s[0], inventory.createStore(storesManager, centre, new InventoryModels.CreateStore(s[0], s[1], s[2])).id());
        }

        // Item master, by the stores manager (who also holds the organisation-wide item master role).
        Map<String, UUID> items = new HashMap<>();
        Map<String, BigDecimal> cost = new HashMap<>();
        for (String[] i : ITEMS) {
            boolean drug = "DRUG".equals(i[2]);
            BigDecimal mrp = new BigDecimal(i[4]);
            var created = inventory.createItem(storesManager, new InventoryModels.SaveItem(i[0], i[1], i[2], i[3], null, new BigDecimal("5"), mrp,
                    drug ? i[6] : null, null, drug ? (i[1].contains("tablets") ? "Tablet" : i[1].contains("ointment") ? "Ointment" : "Eye drops") : null,
                    drug ? i[5] : null, drug ? "Demo Pharma Ltd" : null, "FRAME".equals(i[2]) ? "Vijaya house brand" : null, null, null, null,
                    i.length > 7 ? i[7] : null, i.length > 8 ? new BigDecimal(i[8]) : null, i.length > 9 ? i[9] : null, null,
                    drug ? 10 : 2, null));
            items.put(i[0], created.id());
            cost.put(i[0], mrp.multiply(new BigDecimal("0.62")).setScale(2, java.math.RoundingMode.HALF_UP));
        }

        // Opening stock received from suppliers, by the store keeper.
        LocalDate today = BusinessTime.today();
        int receipts = 0;
        List<InventoryModels.ReceiptLineInput> drugs = new ArrayList<>();
        for (String[] i : ITEMS) {
            if ("DRUG".equals(i[2]) || "CONSUMABLE".equals(i[2])) {
                boolean drug = "DRUG".equals(i[2]);
                // Two batches: one expiring soon (shows on the expiry report and is used first), one long-dated.
                drugs.add(new InventoryModels.ReceiptLineInput(items.get(i[0]), "B" + i[0].hashCode() % 1000 + "A",
                        drug ? today.plusDays(45) : null, 12, cost.get(i[0]), new BigDecimal(i[4])));
                drugs.add(new InventoryModels.ReceiptLineInput(items.get(i[0]), "B" + i[0].hashCode() % 1000 + "B",
                        drug ? today.plusMonths(20) : null, 40, cost.get(i[0]), new BigDecimal(i[4])));
            }
        }
        inventory.receive(storeKeeper, new InventoryModels.CreateReceipt(stores.get("PHARM"), "Demo Medical Distributors", "DMD/26/4471",
                today.minusDays(3), drugs));
        receipts++;
        List<InventoryModels.ReceiptLineInput> shop = new ArrayList<>();
        List<InventoryModels.ReceiptLineInput> lab = new ArrayList<>();
        for (String[] i : ITEMS) {
            switch (i[2]) {
                case "FRAME" -> shop.add(new InventoryModels.ReceiptLineInput(items.get(i[0]), "FR2611", null, 6, cost.get(i[0]), new BigDecimal(i[4])));
                case "SPECTACLE_LENS" -> {
                    if (i[0].endsWith("STK")) {
                        shop.add(new InventoryModels.ReceiptLineInput(items.get(i[0]), "SL2611", null, 20, cost.get(i[0]), new BigDecimal(i[4])));
                    }
                }
                case "LENS_BLANK" -> lab.add(new InventoryModels.ReceiptLineInput(items.get(i[0]), "BK2611", null, 40, cost.get(i[0]), new BigDecimal(i[4])));
                default -> {
                }
            }
        }
        inventory.receive(storeKeeper, new InventoryModels.CreateReceipt(stores.get("OPTICAL"), "Demo Optical Supplies", "DOS-8812",
                today.minusDays(2), shop));
        inventory.receive(storeKeeper, new InventoryModels.CreateReceipt(stores.get("OPTLAB"), "Demo Lens Blanks Co", "LB/2611/093",
                today.minusDays(2), lab));
        receipts += 2;

        // The day: prescriptions and dispensing.
        List<UUID> patients = jdbc.sql("""
                select v.patient_id from eyecare.visit v where v.centre_id = :c order by v.visit_date desc, v.token_no limit 12""")
                .param("c", centre).query(UUID.class).list();
        Actor doctor = staff("CONSULTANT_OPHTHALMOLOGIST");
        Actor pharmacist = staff("PHARMACIST");
        var postOp = pharmacy.prescribe(doctor, centre, new PharmacyModels.CreatePrescription(patients.get(0), null, "After cataract surgery",
                List.of(rxLine(items.get("MOXI-ED"), "1 drop", "4 times a day", 7, "RE", 1),
                        rxLine(items.get("PRED-ED"), "1 drop", "6 times a day, tapering weekly", 28, "RE", 1),
                        rxLine(items.get("NEPAF-ED"), "1 drop", "3 times a day", 28, "RE", 1))));
        pharmacy.dispense(pharmacist, new PharmacyModels.Dispense(stores.get("PHARM"), postOp.patientId(), postOp.id(),
                postOp.lines().stream().map(l -> new PharmacyModels.DispenseLineInput(l.itemId(), l.quantity(), l.id())).toList()));
        var glaucoma = pharmacy.prescribe(doctor, centre, new PharmacyModels.CreatePrescription(patients.get(1), null, "Primary open-angle glaucoma",
                List.of(rxLine(items.get("LATANO-ED"), "1 drop", "at bedtime", 30, "BE", 1),
                        rxLine(items.get("TIMOLOL-ED"), "1 drop", "twice a day", 30, "BE", 1))));
        pharmacy.prescribe(staff("ASSOCIATE_OPHTHALMOLOGIST"), centre, new PharmacyModels.CreatePrescription(patients.get(2), null, "Dry eye",
                List.of(rxLine(items.get("CMC-ED"), "1 drop", "4 times a day", 30, "BE", 2))));
        pharmacy.dispense(staff("PHARMACY_ASSISTANT"), new PharmacyModels.Dispense(stores.get("PHARM"), patients.get(3), null,
                List.of(new PharmacyModels.DispenseLineInput(items.get("HYAL-ED"), 1, null),
                        new PharmacyModels.DispenseLineInput(items.get("PARA-TAB"), 1, null))));

        // Spectacle orders: lenses made in the lab, and lenses fitted in the shop from stock.
        Actor sales = staff("OPTICAL_SALES_EXECUTIVE");
        UUID refracted = jdbc.sql("""
                select v.id from eyecare.visit v join eyecare.optometry_record o on o.visit_id = v.id
                where v.centre_id = :c and (o.sph_re is not null or o.sph_le is not null) order by v.visit_date desc limit 1""")
                .param("c", centre).query(UUID.class).optional().orElse(null);
        UUID refractedPatient = refracted == null ? patients.get(4)
                : jdbc.sql("select patient_id from eyecare.visit where id = :v").param("v", refracted).query(UUID.class).single();
        UUID frameBatch = batch(stores.get("OPTICAL"), items.get("FR-TITAN-01"));
        var labOrder = optical.createOrder(sales, cmd(order(stores.get("OPTICAL"), refractedPatient, refracted, "STOCK", frameBatch, "LAB",
                items.get("LN-SV160-BLUE"), refracted == null ? new BigDecimal("-2.25") : null, refracted == null ? new BigDecimal("-2.00") : null)));
        var job = optical.jobs(staff("OPTICAL_LAB_SUPERVISOR"), centre, null).stream().filter(j -> j.orderId().equals(labOrder.id()))
                .findFirst().orElseThrow();
        Actor surfacer = staff("SURFACING_TECHNICIAN");
        Actor fitter = staff("FITTING_TECHNICIAN");
        Actor inspector = staff("OPTICAL_QUALITY_INSPECTOR");
        var j = optical.advance(surfacer, job.id(), new OpticalModels.AdvanceJob("SURFACING", items.get("BL-160-SV"), "Generated and polished", 0));
        j = optical.advance(surfacer, j.id(), new OpticalModels.AdvanceJob("COATING", null, "Blue-cut coating", j.version()));
        j = optical.advance(fitter, j.id(), new OpticalModels.AdvanceJob("EDGING", null, null, j.version()));
        j = optical.advance(fitter, j.id(), new OpticalModels.AdvanceJob("FITTING", null, null, j.version()));
        j = optical.advance(fitter, j.id(), new OpticalModels.AdvanceJob("QC", null, "Fitted in the titanium frame", j.version()));
        var placed = optical.getOrder(sales, labOrder.id());
        j = optical.qualityCheck(inspector, j.id(), new OpticalModels.QualityCheck(true, placed.sphRe(), placed.cylRe(), placed.axisRe(),
                placed.sphLe(), placed.cylLe(), placed.axisLe(), "Powers, axis and centration within tolerance", j.version()));
        optical.advance(inspector, j.id(), new OpticalModels.AdvanceJob("DISPATCHED", null, "Sent to the shop", j.version()));

        var second = optical.createOrder(sales, cmd(order(stores.get("OPTICAL"), patients.get(5), null, "STOCK",
                batch(stores.get("OPTICAL"), items.get("FR-KIDS-04")), "LAB", items.get("LN-SV156-HMC"), new BigDecimal("-1.25"),
                new BigDecimal("-1.50"))));
        var secondJob = optical.jobs(surfacer, centre, null).stream().filter(x -> x.orderId().equals(second.id())).findFirst().orElseThrow();
        optical.advance(surfacer, secondJob.id(), new OpticalModels.AdvanceJob("SURFACING", items.get("BL-156-SV"), null, secondJob.version()));

        optical.createOrder(sales, cmd(order(stores.get("OPTICAL"), patients.get(6), null, "PATIENT_OWN", null, "STOCK",
                items.get("LN-SV150-STK"), new BigDecimal("1.00"), new BigDecimal("1.25"))
                .withLensBatch(batch(stores.get("OPTICAL"), items.get("LN-SV150-STK")))));

        // A damaged bottle written off: requested by the store keeper, waiting for the stores manager.
        UUID damaged = jdbc.sql("select id from inventory.stock_batch where store_id = :s and item_id = :i order by expiry_date desc limit 1")
                .param("s", stores.get("PHARM")).param("i", items.get("TIMOLOL-ED")).query(UUID.class).single();
        inventory.requestAdjustment(storeKeeper, new InventoryModels.RequestAdjustment(damaged, -1, "DAMAGED", "Bottle cracked on the shelf"));

        int prescriptions = jdbc.sql("select count(*) from pharmacy.prescription where centre_id = :c").param("c", centre).query(Integer.class).single();
        int sales_ = jdbc.sql("select count(*) from pharmacy.sale where centre_id = :c").param("c", centre).query(Integer.class).single();
        int orders = jdbc.sql("select count(*) from optical.spectacle_order where centre_id = :c").param("c", centre).query(Integer.class).single();
        int jobs = jdbc.sql("select count(*) from optical.lab_job where lab_centre_id = :c").param("c", centre).query(Integer.class).single();
        if (glaucoma == null) {
            throw new IllegalStateException("prescription not created");
        }
        return new Summary(stores.size(), ITEMS.length, receipts, prescriptions, sales_, orders, jobs);
    }

    /** A spectacle order request; {@link #withLensBatch} picks a stock lens pair. */
    record OrderDraft(UUID storeId, UUID patientId, UUID visitRef, String frameSource, UUID frameBatchId, String lensSource, UUID lensItemId,
                      UUID lensBatchId, BigDecimal sphRe, BigDecimal sphLe) {
        OrderDraft withLensBatch(UUID batch) {
            return new OrderDraft(storeId, patientId, visitRef, frameSource, frameBatchId, lensSource, lensItemId, batch, sphRe, sphLe);
        }
    }

    private OrderDraft order(UUID store, UUID patient, UUID visit, String frameSource, UUID frameBatch, String lensSource, UUID lensItem,
                             BigDecimal sphRe, BigDecimal sphLe) {
        return new OrderDraft(store, patient, visit, frameSource, frameBatch, lensSource, lensItem, null, sphRe, sphLe);
    }

    private static OpticalModels.CreateOrder cmd(OrderDraft d) {
        return new OpticalModels.CreateOrder(d.storeId(), d.patientId(), d.visitRef(), d.sphRe(), null, null, null, d.sphLe(), null, null, null,
                new BigDecimal("32"), new BigDecimal("31.5"), null, d.frameSource(), d.frameBatchId(), d.lensSource(), d.lensItemId(),
                d.lensBatchId(), null, BusinessTime.today().plusDays(3), null);
    }

    private static PharmacyModels.PrescriptionLineInput rxLine(UUID item, String dose, String frequency, int days, String eye, int qty) {
        return new PharmacyModels.PrescriptionLineInput(item, dose, frequency, days, eye, "TOPICAL", qty, null);
    }

    private UUID batch(UUID store, UUID item) {
        return jdbc.sql("select id from inventory.stock_batch where store_id = :s and item_id = :i and qty_on_hand > 0 limit 1")
                .param("s", store).param("i", item).query(UUID.class).single();
    }

    private Actor staff(String positionCode) {
        UUID user = jdbc.sql("""
                select u.id from iam.user_account u join workforce.posting p on p.person_id = u.person_id
                join workforce.establishment e on e.id = p.establishment_id join workforce.position_definition pos on pos.id = e.position_id
                where pos.code = :c order by u.username limit 1""").param("c", positionCode).query(UUID.class).optional()
                .orElseThrow(() -> new IllegalStateException("Nobody holds " + positionCode + "; run ops/clinic-staff.sh first"));
        return jdbc.sql("select id, tenant_id, person_id, username, access_version from iam.user_account where id = :u").param("u", user)
                .query((rs, i) -> new Actor(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                        rs.getObject("person_id", UUID.class), rs.getString("username"), rs.getInt("access_version"))).single();
    }
}
