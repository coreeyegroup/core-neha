package in.coreeye.coreneha.operations;

import java.nio.file.Path;
import java.util.List;

import in.coreeye.coreneha.iam.BootstrapService;
import in.coreeye.coreneha.kernel.CoreNehaProperties;
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
    private final ConfigurableApplicationContext context;

    OperationsRunner(CoreNehaProperties props, Environment env, BootstrapService bootstrap, DemoSeed demoSeed,
                     ClinicSeed clinicSeed, ClinicStaff clinicStaff, ConfigurableApplicationContext context) {
        this.props = props;
        this.env = env;
        this.bootstrap = bootstrap;
        this.demoSeed = demoSeed;
        this.clinicSeed = clinicSeed;
        this.clinicStaff = clinicStaff;
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
}
