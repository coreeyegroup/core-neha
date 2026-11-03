package in.coreeye.coreneha;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import in.coreeye.coreneha.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

/** Starts the real application against PostgreSQL and checks health and the default security posture. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"management.server.port=0", "CORE_NEHA_BASE_PATH=/core-neha"})
class ApplicationStartupTest extends PostgresTestBase {

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    Environment environment;

    private HttpResponse<String> get(String url) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void readinessIsUpOnManagementPort() throws Exception {
        int managementPort = environment.getRequiredProperty("local.management.port", Integer.class);
        HttpResponse<String> response = get("http://localhost:" + managementPort + "/actuator/health/readiness");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("UP");
    }

    @Test
    void apiRefusesUnauthenticatedRequests() throws Exception {
        int port = environment.getRequiredProperty("local.server.port", Integer.class);
        HttpResponse<String> response = get("http://localhost:" + port + "/core-neha/api/v1/me");
        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void actuatorIsNotServedOnApplicationPort() throws Exception {
        int port = environment.getRequiredProperty("local.server.port", Integer.class);
        HttpResponse<String> response = get("http://localhost:" + port + "/core-neha/api/actuator/health");
        assertThat(response.statusCode()).isIn(401, 404);
    }
}
