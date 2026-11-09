package in.coreeye.coreneha.operations;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import in.coreeye.coreneha.diagnostics.DiagnosticsModels;
import in.coreeye.coreneha.diagnostics.DiagnosticsModels.DiagnosticOrderView;
import in.coreeye.coreneha.diagnostics.DiagnosticsModels.ParameterView;
import in.coreeye.coreneha.diagnostics.DiagnosticsService;
import in.coreeye.coreneha.diagnostics.ImagingArchive;
import in.coreeye.coreneha.kernel.Actor;
import in.coreeye.coreneha.kernel.NotFoundException;
import in.coreeye.coreneha.kernel.TenantContext;
import in.coreeye.coreneha.org.OrgDirectory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Laboratory and imaging for the Vijaya Eye Clinic demonstration: the catalogue (LOINC-coded tests,
 * eye and general imaging), and a day of orders worked by the clinic's staff (collection, results,
 * validation by a second person, imaging with synthetic DICOM images stored in the archive when one is
 * configured, reports and signatures). Run after clinic-staff; once.
 */
@Component
class ClinicDiagnostics {

    record Summary(int labTests, int procedures, int orders, int validated, int verified, boolean images) {
    }

    private static ParameterView num(String code, String name, String unit, String loinc, String low, String high, String critLow, String critHigh) {
        return new ParameterView(null, code, name, unit, loinc, "NUMERIC", dec(low), dec(high), dec(critLow), dec(critHigh), null, 0);
    }

    private static ParameterView txt(String code, String name, String loinc, String normal) {
        return new ParameterView(null, code, name, null, loinc, "TEXT", null, null, null, null, normal, 0);
    }

    private static BigDecimal dec(String s) {
        return s == null ? null : new BigDecimal(s);
    }

    private final JdbcClient jdbc;
    private final OrgDirectory directory;
    private final DiagnosticsService diagnostics;
    private final ImagingArchive archive;
    private final TransactionTemplate tx;

    ClinicDiagnostics(JdbcClient jdbc, OrgDirectory directory, DiagnosticsService diagnostics, ImagingArchive archive,
                      PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.directory = directory;
        this.diagnostics = diagnostics;
        this.archive = archive;
        this.tx = new TransactionTemplate(transactionManager);
    }

    Summary load(String tenantCode) {
        UUID tenant = directory.tenantIdByCode(tenantCode).orElseThrow(() -> new NotFoundException("Tenant '" + tenantCode + "' not found"));
        return TenantContext.callAs(tenant, () -> tx.execute(status -> build()));
    }

    private Summary build() {
        UUID centre = jdbc.sql("select id from org.centre where code = :c").param("c", ClinicSeed.CENTRE_CODE).query(UUID.class).optional()
                .orElseThrow(() -> new NotFoundException(ClinicSeed.CENTRE_NAME + " not found; run ops/clinic-seed.sh first"));
        if (jdbc.sql("select exists(select 1 from diagnostics.lab_test)").query(Boolean.class).single()) {
            throw new IllegalStateException("The laboratory catalogue already exists; the diagnostics demonstration loads only once");
        }
        Actor pathologist = staff("PATHOLOGIST");
        Actor radiologist = staff("RADIOLOGIST");

        // Catalogue (kept by the pathologist and radiologist, who hold the organisation-wide catalogue role).
        Map<String, UUID> t = new HashMap<>();
        t.put("FBS", diagnostics.addLabTest(pathologist, "FBS", "Fasting blood sugar", "BIOCHEMISTRY", "PLASMA", "Fluoride (grey top)", "1558-6",
                new BigDecimal("80"), 4, List.of(num("GLU", "Glucose, fasting", "mg/dL", "1558-6", "70", "100", "40", "400"))));
        t.put("RBS", diagnostics.addLabTest(pathologist, "RBS", "Random blood sugar", "BIOCHEMISTRY", "PLASMA", "Fluoride (grey top)", "2345-7",
                new BigDecimal("80"), 2, List.of(num("GLU", "Glucose, random", "mg/dL", "2345-7", "70", "140", "40", "450"))));
        t.put("HBA1C", diagnostics.addLabTest(pathologist, "HBA1C", "Glycated haemoglobin (HbA1c)", "BIOCHEMISTRY", "BLOOD", "EDTA (lavender top)",
                "4548-4", new BigDecimal("450"), 24, List.of(num("HBA1C", "HbA1c", "%", "4548-4", "4.0", "5.6", null, "14"))));
        t.put("CBC", diagnostics.addLabTest(pathologist, "CBC", "Complete blood count", "HAEMATOLOGY", "BLOOD", "EDTA (lavender top)", "58410-2",
                new BigDecimal("300"), 6, List.of(num("HB", "Haemoglobin", "g/dL", "718-7", "12.0", "16.0", "7.0", "20.0"),
                        num("WBC", "Total leucocyte count", "10^3/µL", "6690-2", "4.0", "11.0", "2.0", "30.0"),
                        num("PLT", "Platelet count", "10^3/µL", "777-3", "150", "410", "50", "1000"))));
        t.put("CREAT", diagnostics.addLabTest(pathologist, "CREAT", "Serum creatinine", "BIOCHEMISTRY", "SERUM", "Plain (red top)", "2160-0",
                new BigDecimal("200"), 6, List.of(num("CREAT", "Creatinine", "mg/dL", "2160-0", "0.6", "1.2", null, "5.0"))));
        t.put("PTINR", diagnostics.addLabTest(pathologist, "PTINR", "Prothrombin time with INR", "HAEMATOLOGY", "PLASMA", "Citrate (blue top)",
                "6301-6", new BigDecimal("350"), 6, List.of(num("INR", "INR", null, "6301-6", "0.8", "1.2", null, "4.0"))));
        t.put("HBSAG", diagnostics.addLabTest(pathologist, "HBSAG", "Hepatitis B surface antigen", "SEROLOGY", "SERUM", "Plain (red top)", "5196-1",
                new BigDecimal("300"), 24, List.of(txt("HBSAG", "HBsAg", "5196-1", "Non-reactive"))));
        t.put("HCV", diagnostics.addLabTest(pathologist, "HCV", "Hepatitis C antibody", "SEROLOGY", "SERUM", "Plain (red top)", "16128-1",
                new BigDecimal("450"), 24, List.of(txt("HCVAB", "Anti-HCV", "16128-1", "Non-reactive"))));
        t.put("URINE", diagnostics.addLabTest(pathologist, "URINE", "Urine routine examination", "CLINICAL_PATHOLOGY", "URINE", "Sterile container",
                "24356-8", new BigDecimal("150"), 4, List.of(txt("UGLU", "Glucose", null, "Nil"), txt("UPRO", "Protein", null, "Nil"),
                        num("PUS", "Pus cells", "/hpf", null, "0", "5", null, null))));
        t.put("CONJ", diagnostics.addLabTest(pathologist, "CONJ", "Conjunctival swab culture and sensitivity", "MICROBIOLOGY", "SWAB",
                "Sterile swab in transport medium", null, new BigDecimal("600"), 72,
                List.of(txt("ORG", "Organism isolated", null, "No growth"), txt("SENS", "Antibiotic sensitivity", null, null))));
        Map<String, UUID> p = new HashMap<>();
        p.put("OCTMAC", diagnostics.addProcedure(radiologist, "OCTMAC", "OCT macula", "OPT", "Eye", null, new BigDecimal("1500")));
        p.put("OCTRNFL", diagnostics.addProcedure(radiologist, "OCTRNFL", "OCT optic nerve head (RNFL)", "OPT", "Eye", null, new BigDecimal("1500")));
        p.put("FUNDUS", diagnostics.addProcedure(radiologist, "FUNDUS", "Fundus photography", "OP", "Eye", "Pupils are dilated; bring sunglasses",
                new BigDecimal("600")));
        p.put("FFA", diagnostics.addProcedure(radiologist, "FFA", "Fundus fluorescein angiography", "OP", "Eye",
                "Tell the staff about allergies, asthma and kidney disease; come with a companion", new BigDecimal("3000")));
        p.put("BSCAN", diagnostics.addProcedure(radiologist, "BSCAN", "B-scan ultrasound of the eye", "US", "Eye", null, new BigDecimal("1200")));
        p.put("CTORBIT", diagnostics.addProcedure(radiologist, "CTORBIT", "CT orbits (plain)", "CT", "Orbit", null, new BigDecimal("3500")));
        p.put("MRIORBIT", diagnostics.addProcedure(radiologist, "MRIORBIT", "MRI orbits and brain", "MR", "Orbit and brain",
                "Remove all metal; tell the staff about implants, pacemakers or claustrophobia", new BigDecimal("7500")));
        p.put("CXR", diagnostics.addProcedure(radiologist, "CXR", "Chest X-ray PA (pre-operative)", "DX", "Chest", null, new BigDecimal("350")));

        // The day's orders.
        List<UUID> patients = jdbc.sql("select distinct patient_id from eyecare.visit where centre_id = :c limit 6").param("c", centre)
                .query(UUID.class).list();
        Actor doctor = staff("CONSULTANT_OPHTHALMOLOGIST");
        Actor phlebotomist = staff("PHLEBOTOMIST");
        Actor technician = staff("LAB_TECHNICIAN");
        Actor radiographer = staff("RADIOGRAPHER");

        List<DiagnosticOrderView> preOp = diagnostics.order(doctor, centre, new DiagnosticsModels.CreateOrders(patients.get(0), null, "ROUTINE",
                "Pre-operative work-up for cataract surgery", List.of(t.get("FBS"), t.get("CBC"), t.get("HBSAG")), List.of(p.get("CXR"))));
        List<DiagnosticOrderView> diabetic = diagnostics.order(doctor, centre, new DiagnosticsModels.CreateOrders(patients.get(1), null, "URGENT",
                "Diabetic retinopathy review", List.of(t.get("HBA1C"), t.get("RBS")), List.of(p.get("OCTMAC"), p.get("FUNDUS"))));
        List<DiagnosticOrderView> orbit = diagnostics.order(staff("ASSOCIATE_OPHTHALMOLOGIST"), centre, new DiagnosticsModels.CreateOrders(patients.get(2),
                null, "ROUTINE", "Proptosis of the left eye", List.of(t.get("URINE"), t.get("CONJ")), List.of(p.get("CTORBIT"))));

        // Laboratory: collect, receive, result, and validate some (by the pathologist, not the technician).
        Map<String, Object[]> values = Map.of(
                "FBS", new Object[] {"168"}, "CBC", new Object[] {"13.1", "7.8", "265"}, "HBSAG", new Object[] {"Non-reactive"},
                "HBA1C", new Object[] {"8.4"}, "RBS", new Object[] {"462"});
        int validated = 0;
        for (DiagnosticOrderView o : concat(preOp, diabetic)) {
            if (!"LAB".equals(o.kind())) {
                continue;
            }
            o = diagnostics.collect(phlebotomist, o.id(), new DiagnosticsModels.Step(o.version(), null));
            o = diagnostics.receive(technician, o.id(), new DiagnosticsModels.Step(o.version(), null));
            Object[] v = values.get(o.testCode());
            List<ParameterView> params = jdbc.sql("""
                    select p.id, p.code, p.name, p.unit, p.loinc_code, p.value_type, p.ref_low, p.ref_high, p.critical_low, p.critical_high,
                           p.ref_text, p.sort_order
                    from diagnostics.lab_parameter p join diagnostics.lab_test t on t.id = p.test_id where t.code = :c order by p.sort_order""")
                    .param("c", o.testCode()).query(ParameterView.class).list();
            List<DiagnosticsModels.ResultInput> results = new java.util.ArrayList<>();
            for (int i = 0; i < params.size(); i++) {
                ParameterView pv = params.get(i);
                String raw = v[i].toString();
                results.add("NUMERIC".equals(pv.valueType()) ? new DiagnosticsModels.ResultInput(pv.id(), new BigDecimal(raw), null)
                        : new DiagnosticsModels.ResultInput(pv.id(), null, raw));
            }
            o = diagnostics.enterResults(technician, o.id(), new DiagnosticsModels.EnterResults(o.version(), results));
            if (List.of("FBS", "HBSAG", "HBA1C").contains(o.testCode())) {
                diagnostics.validate(pathologist, o.id(), new DiagnosticsModels.Step(o.version(), null));
                validated++;
            }
        }
        DiagnosticOrderView conj = orbit.stream().filter(o -> "CONJ".equals(o.testCode())).findFirst().orElseThrow();
        diagnostics.collect(phlebotomist, conj.id(), new DiagnosticsModels.Step(conj.version(), null));

        // Imaging: schedule and perform; images go to the archive when one is connected.
        int verified = 0;
        Instant now = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        for (DiagnosticOrderView o : concat(preOp, concat(diabetic, orbit))) {
            if (!"IMAGING".equals(o.kind())) {
                continue;
            }
            boolean later = "CTORBIT".equals(o.testCode());
            o = diagnostics.schedule(radiographer, o.id(), new DiagnosticsModels.Schedule(o.version(), later ? now.plus(1, ChronoUnit.DAYS) : now.minus(2, ChronoUnit.HOURS)));
            if (later) {
                continue;
            }
            String uid = null;
            Integer count = null;
            if (archive.enabled()) {
                storeImages(o);
            } else {
                uid = SyntheticDicom.uid();
                count = 1;
            }
            o = diagnostics.acquire(radiographer, o.id(), new DiagnosticsModels.Acquire(o.version(), uid, count));
            switch (o.testCode()) {
                case "CXR" -> {
                    o = diagnostics.report(radiologist, o.id(), new DiagnosticsModels.Report(o.version(),
                            "Lung fields are clear. Cardiac size is normal. Costophrenic angles are sharp. No bony abnormality.",
                            "Normal chest radiograph. Fit for surgery from the chest point of view."));
                    diagnostics.verify(radiologist, o.id(), new DiagnosticsModels.Step(o.version(), null));
                    verified++;
                }
                case "FUNDUS" -> diagnostics.report(radiologist, o.id(), new DiagnosticsModels.Report(o.version(),
                        "Both eyes: scattered dot-blot haemorrhages and hard exudates in all quadrants; no new vessels seen.",
                        "Moderate non-proliferative diabetic retinopathy, both eyes."));
                default -> {
                }
            }
        }
        int orders = jdbc.sql("select count(*) from diagnostics.diagnostic_order where centre_id = :c").param("c", centre).query(Integer.class).single();
        return new Summary(t.size(), p.size(), orders, validated, verified, archive.enabled());
    }

    /** Synthetic images for an imaging order, stored in the archive under the order's accession number. */
    private void storeImages(DiagnosticOrderView o) {
        SyntheticDicom.Study study = SyntheticDicom.newStudy();
        String kind = switch (o.testCode()) {
            case "OCTMAC", "OCTRNFL" -> "OCT";
            case "FUNDUS", "FFA" -> "FUNDUS";
            case "CTORBIT", "MRIORBIT" -> "ORBIT";
            case "CXR" -> "CHEST";
            default -> "BSCAN";
        };
        int images = "ORBIT".equals(kind) ? 12 : "OCT".equals(kind) ? 6 : 2;
        String modality = o.modality() == null ? "OT" : o.modality();
        LocalDateTime when = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
        for (int i = 1; i <= images; i++) {
            archive.store(SyntheticDicom.image(study, i, images, modality, o.testName(), o.accessionNo(), o.patientName(), o.patientUhid(),
                    o.patientSex() == null ? "O" : o.patientSex(), kind, when));
        }
    }

    private static List<DiagnosticOrderView> concat(List<DiagnosticOrderView> a, List<DiagnosticOrderView> b) {
        List<DiagnosticOrderView> all = new java.util.ArrayList<>(a);
        all.addAll(b);
        return all;
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
