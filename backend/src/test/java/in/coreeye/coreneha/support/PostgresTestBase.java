package in.coreeye.coreneha.support;

import java.time.Duration;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * One PostgreSQL 18.6 container (same image as production) shared by all
 * integration tests, with the production role model: Flyway runs as the
 * owner role and the application as the restricted runtime role.
 */
public abstract class PostgresTestBase {

    public static final String APP_PASSWORD = "test-app";
    public static final String OWNER_PASSWORD = "test-owner";

    @SuppressWarnings("resource")
    protected static final GenericContainer<?> POSTGRES =
            new GenericContainer<>(DockerImageName.parse("postgres:18.6-alpine3.24"))
                    .withEnv("POSTGRES_PASSWORD", "test-superuser")
                    .withCopyToContainer(MountableFile.forClasspathResource("db/test-initdb.sql"),
                            "/docker-entrypoint-initdb.d/10-test-init.sql")
                    .withExposedPorts(5432)
                    .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\s", 2)
                            .withStartupTimeout(Duration.ofSeconds(120)));

    static {
        POSTGRES.start();
    }

    public static String jdbcUrl() {
        return "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/core_neha";
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PostgresTestBase::jdbcUrl);
        registry.add("core_neha_app_password", () -> APP_PASSWORD);
        registry.add("core_neha_owner_password", () -> OWNER_PASSWORD);
    }
}
