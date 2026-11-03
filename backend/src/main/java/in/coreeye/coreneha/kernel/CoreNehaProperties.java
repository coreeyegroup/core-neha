package in.coreeye.coreneha.kernel;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Deployment settings (application.yaml, environment). One public address drives every URL (ADR-0004). */
@ConfigurationProperties("core-neha")
public record CoreNehaProperties(String environment, String publicBaseUrl, String basePath, Identity identity,
                                 Session session, Operations operations) {

    public record Identity(String realm, String internalUrl, String publicUrl, String staffClientId,
                           String provisionerClientId, String provisionerClientSecret) {

        public String publicIssuer() {
            return publicUrl + "/realms/" + realm;
        }

        public String internalRealmUrl() {
            return internalUrl + "/realms/" + realm;
        }
    }

    public record Session(Duration absoluteTimeout) {
    }

    public record Operations(String command, String outputDir) {
    }

    public boolean demo() {
        return "demo".equals(environment);
    }
}
