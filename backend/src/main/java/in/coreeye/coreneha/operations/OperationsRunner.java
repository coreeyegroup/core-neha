package in.coreeye.coreneha.operations;

import java.nio.file.Path;
import java.util.List;

import in.coreeye.coreneha.iam.BootstrapService;
import in.coreeye.coreneha.kernel.CoreNehaProperties;
import in.coreeye.coreneha.org.TenantLicensing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Runs an operator command when CORE_NEHA_COMMAND is set (see ops/bootstrap.sh
 * and ops/demo-seed.sh), then exits. Does nothing in normal server mode.
 */
@Component
class OperationsRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OperationsRunner.class);

    private final CoreNehaProperties props;
    private final Environment env;
    private final BootstrapService bootstrap;
    private final DemoSeed demoSeed;
    private final ClinicSeed clinicSeed;
    private final ClinicStaff clinicStaff;
    private final ClinicSupply clinicSupply;
    private final ClinicDiagnostics clinicDiagnostics;
    private final DemoInspector demoInspector;
    private final TenantLicensing licensing;
    private final ConfigurableApplicationContext context;

    OperationsRunner(CoreNehaProperties props, Environment env, BootstrapService bootstrap, DemoSeed demoSeed,
                     ClinicSeed clinicSeed, ClinicStaff clinicStaff, ClinicSupply clinicSupply,
                     ClinicDiagnostics clinicDiagnostics, DemoInspector demoInspector, TenantLicensing licensing,
                     ConfigurableApplicationContext context) {
        this.props = props;
        this.env = env;
        this.bootstrap = bootstrap;
        this.demoSeed = demoSeed;
        this.clinicSeed = clinicSeed;
        this.clinicStaff = clinicStaff;
        this.clinicSupply = clinicSupply;
        this.clinicDiagnostics = clinicDiagnostics;
        this.demoInspector = demoInspector;
        this.licensing = licensing;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String command = props.operations() == null ? null : props.operations().command();
        if (command == null || command.isBlank()) {
            return;
        }
        int exit = 0;
        try {
            switch (command) {
                case "bootstrap" -> runBootstrap();
                case "demo-seed" -> runDemoSeed();
                case "clinic-seed" -> runClinicSeed();
                case "clinic-staff" -> runClinicStaff();
                case "clinic-supply" -> runClinicSupply();
                case "clinic-diagnostics" -> runClinicDiagnostics();
                case "demo-inspector" -> runDemoInspector();
                case "tenant-list" -> runTenantList();
                case "tenant-licence" -> runTenantLicence();
                case "tenant-suspend" -> licensing.setStatus(required("CORE_NEHA_TENANT_CODE"), false, required("CORE_NEHA_REASON"));
                case "tenant-activate" -> licensing.setStatus(required("CORE_NEHA_TENANT_CODE"), true, required("CORE_NEHA_REASON"));
                default -> throw new IllegalArgumentException("Unknown command: " + command);
            }
        } catch (Exception e) {
            log.error("Command {} failed: {}", command, e.getMessage(), e);
            exit = 1;
        }
        int code = exit;
        System.exit(SpringApplication.exit(context, () -> code));
    }

    private void runBootstrap() throws Exception {
        String code = env.getRequiredProperty("CORE_NEHA_BOOTSTRAP_TENANT_CODE");
        String name = env.getRequiredProperty("CORE_NEHA_BOOTSTRAP_TENANT_NAME");
        List<BootstrapService.Administrator> admins = List.of(admin("ADMIN1"), admin("ADMIN2"));
        var result = bootstrap.bootstrap(code, name, props.demo(), admins);
        Path file = CredentialsFile.write(props.operations().outputDir(), "bootstrap-" + code,
                "core-neha first administrators for tenant " + code + " (" + name + ")",
                result.credentials().stream().map(c -> new CredentialsFile.Entry(c.username(), c.temporaryPassword(),
                        "administrator")).toList());
        log.info("Bootstrap complete for tenant {}: 2 administrators; one-time passwords written to {}", code, file.getFileName());
    }

    private BootstrapService.Administrator admin(String prefix) {
        String p = "CORE_NEHA_BOOTSTRAP_" + prefix + "_";
        return new BootstrapService.Administrator(env.getRequiredProperty(p + "GIVEN_NAME"),
                env.getRequiredProperty(p + "FAMILY_NAME"), env.getRequiredProperty(p + "EMAIL"),
                env.getRequiredProperty(p + "USERNAME"), null);
    }

    private void runDemoSeed() throws Exception {
        if (!props.demo()) {
            throw new IllegalStateException("Demonstration data is loaded only when CORE_NEHA_ENVIRONMENT=demo");
        }
        DemoSeed.Summary summary = demoSeed.load();
        Path file = CredentialsFile.write(props.operations().outputDir(), "demo-logins",
                "core-neha DEMONSTRATION logins (synthetic people, tenant " + summary.tenantCode() + ")", summary.logins());
        log.info("Demonstration data loaded: {} centres, {} people, {} login accounts; one-time passwords written to {}",
                summary.centres(), summary.people(), summary.logins().size(), file.getFileName());
    }

    private void runClinicSeed() throws Exception {
        if (!props.demo()) {
            throw new IllegalStateException("Demonstration data is loaded only when CORE_NEHA_ENVIRONMENT=demo");
        }
        String tenant = env.getProperty("CORE_NEHA_SEED_TENANT", DemoSeed.TENANT_CODE);
        ClinicSeed.Summary summary = clinicSeed.load(tenant);
        Path file = CredentialsFile.write(props.operations().outputDir(), "clinic-logins",
                "core-neha DEMONSTRATION logins for " + ClinicSeed.CENTRE_NAME + " (synthetic staff and patients, tenant " + tenant + ")",
                summary.logins());
        log.info("{} loaded: {} departments, {} positions, {} synthetic patients, {} login accounts; one-time passwords written to {}",
                ClinicSeed.CENTRE_NAME, summary.departments(), summary.positions(), summary.patients(), summary.logins().size(),
                file.getFileName());
    }

    /** Adds the full staff of the eye clinic (every position, two people each); safe to run again. */
    private void runDemoInspector() throws Exception {
        if (!props.demo()) {
            throw new IllegalStateException("Demonstration data is loaded only when CORE_NEHA_ENVIRONMENT=demo");
        }
        String tenant = env.getProperty("CORE_NEHA_SEED_TENANT", DemoSeed.TENANT_CODE);
        DemoInspector.Summary s = demoInspector.load(tenant);
        if (!s.created()) {
            log.info("Demonstration inspector already exists: login id {}; its role now has all {} permissions", s.username(), s.permissions());
            return;
        }
        Path file = CredentialsFile.write(props.operations().outputDir(), "demo-inspector-login",
                "core-neha DEMONSTRATION inspector login: every permission, whole organisation (tenant " + tenant + ")", List.of(s.login()));
        log.info("Demonstration inspector created: login id {}, {} permissions; one-time password written to {}", s.username(), s.permissions(),
                file.getFileName());
    }

    private void runClinicStaff() throws Exception {
        if (!props.demo()) {
            throw new IllegalStateException("Demonstration data is loaded only when CORE_NEHA_ENVIRONMENT=demo");
        }
        String tenant = env.getProperty("CORE_NEHA_SEED_TENANT", DemoSeed.TENANT_CODE);
        ClinicStaff.Summary summary = clinicStaff.extend(tenant);
        if (summary.logins().isEmpty()) {
            log.info("{} staff: all {} positions already have two people; nothing added", ClinicSeed.CENTRE_NAME, summary.positions());
            return;
        }
        Path file = CredentialsFile.write(props.operations().outputDir(), "clinic-staff-logins",
                "core-neha DEMONSTRATION logins for the full staff of " + ClinicSeed.CENTRE_NAME + " (synthetic people, tenant " + tenant + ")",
                summary.logins());
        log.info("{} staff: {} positions, {} people added; one-time passwords written to {}", ClinicSeed.CENTRE_NAME,
                summary.positions(), summary.added(), file.getFileName());
    }

    /** Stores, item master, opening stock and a pharmacy and optical day for the eye clinic (demo, once). */
    private void runClinicSupply() {
        if (!props.demo()) {
            throw new IllegalStateException("Demonstration data is loaded only when CORE_NEHA_ENVIRONMENT=demo");
        }
        ClinicSupply.Summary s = clinicSupply.load(env.getProperty("CORE_NEHA_SEED_TENANT", DemoSeed.TENANT_CODE));
        log.info("{} supply: {} stores, {} items, {} receipts, {} prescriptions, {} pharmacy sales, {} spectacle orders, {} lab jobs",
                ClinicSeed.CENTRE_NAME, s.stores(), s.items(), s.receipts(), s.prescriptions(), s.sales(), s.spectacleOrders(), s.labJobs());
    }

    /** Laboratory and imaging catalogue and a day of orders for the eye clinic (demo, once). */
    private void runClinicDiagnostics() {
        if (!props.demo()) {
            throw new IllegalStateException("Demonstration data is loaded only when CORE_NEHA_ENVIRONMENT=demo");
        }
        ClinicDiagnostics.Summary s = clinicDiagnostics.load(env.getProperty("CORE_NEHA_SEED_TENANT", DemoSeed.TENANT_CODE));
        log.info("{} diagnostics: {} lab tests, {} imaging procedures, {} orders, {} validated, {} reports signed, images in archive: {}",
                ClinicSeed.CENTRE_NAME, s.labTests(), s.procedures(), s.orders(), s.validated(), s.verified(), s.images());
    }

    private String required(String name) {
        String v = env.getProperty(name);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return v.trim();
    }

    private String optional(String name) {
        String v = env.getProperty(name);
        return v == null || v.isBlank() ? null : v.trim();
    }

    /** Prints every hospital chain on this installation: licence and size only (no patient or staff data). */
    private void runTenantList() {
        System.out.printf("%-14s %-34s %-9s %-16s %-10s %-11s %-7s %-7s %s%n", "CODE", "NAME", "STATUS", "LICENCE", "PLAN", "VALID TO",
                "CENTRES", "USERS", "MODULES");
        for (TenantLicensing.TenantSummary t : licensing.tenants()) {
            System.out.printf("%-14s %-34s %-9s %-16s %-10s %-11s %-7s %-7d %s%n", t.code(), abbreviate(t.name(), 34), t.status(),
                    t.licenceNo(), t.plan(), t.validTo() == null ? "open" : t.validTo(),
                    t.centres() + (t.maxCentres() == null ? "" : "/" + t.maxCentres()), t.users(), String.join(",", t.modules()));
        }
    }

    /** Sets one chain's licence terms and modules (CORE_NEHA_TENANT_CODE, CORE_NEHA_LICENCE_*, CORE_NEHA_REASON). */
    private void runTenantLicence() {
        String code = required("CORE_NEHA_TENANT_CODE");
        TenantLicensing.TenantSummary current = licensing.tenants().stream().filter(t -> t.code().equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No tenant with code '" + code + "'"));
        String modules = optional("CORE_NEHA_LICENCE_MODULES");
        String validTo = optional("CORE_NEHA_LICENCE_VALID_TO");
        String validFrom = optional("CORE_NEHA_LICENCE_VALID_FROM");
        String maxCentres = optional("CORE_NEHA_LICENCE_MAX_CENTRES");
        licensing.setLicence(code, new TenantLicensing.LicenceChange(
                orElse(optional("CORE_NEHA_LICENCE_NO"), current.licenceNo()),
                orElse(optional("CORE_NEHA_LICENSEE"), current.licenseeName()),
                orElse(optional("CORE_NEHA_LICENCE_PLAN"), current.plan()),
                validFrom == null ? orElse(current.validFrom(), java.time.LocalDate.now()) : java.time.LocalDate.parse(validFrom),
                validTo == null ? current.validTo() : "open".equalsIgnoreCase(validTo) ? null : java.time.LocalDate.parse(validTo),
                maxCentres == null ? current.maxCentres() : "unlimited".equalsIgnoreCase(maxCentres) ? null : Integer.valueOf(maxCentres),
                modules == null ? current.modules() : "ALL".equalsIgnoreCase(modules) ? TenantLicensing.ALL_MODULES
                        : List.of(modules.toUpperCase(java.util.Locale.ROOT).split("\\s*,\\s*")),
                required("CORE_NEHA_REASON")));
        runTenantList();
    }

    private static <T> T orElse(T value, T fallback) {
        return value == null ? fallback : value;
    }

    private static String abbreviate(String s, int n) {
        return s == null || s.length() <= n ? s : s.substring(0, n - 1) + "…";
    }
}
