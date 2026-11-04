package in.coreeye.coreneha.iam;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import in.coreeye.coreneha.kernel.CoreNehaProperties;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Keycloak admin API client, authenticated as the core-neha-provisioner
 * service account (manage-users in the core-neha realm only). Called over
 * the internal network; no administrator password is used.
 */
@Component
class KeycloakProvisioner implements IdentityProvisioner {

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RestClient http;
    private final CoreNehaProperties.Identity identity;
    private String token;
    private Instant tokenExpiry = Instant.EPOCH;

    KeycloakProvisioner(CoreNehaProperties props) {
        this.http = RestClient.builder().build();
        this.identity = props.identity();
    }

    @Override
    public ProvisionedIdentity create(String username, String email, String givenName, String familyName, boolean demo) {
        String password = temporaryPassword();
        Map<String, Object> body = Map.of(
                "username", username,
                "email", email,
                "firstName", givenName,
                "lastName", familyName,
                "enabled", true,
                "emailVerified", true,
                "requiredActions", List.of("UPDATE_PASSWORD", "CONFIGURE_TOTP"),
                "credentials", List.of(Map.of("type", "password", "value", password, "temporary", true)));
        // The demonstration flag is kept in core-neha (person.is_demo); Keycloak's user profile
        // rejects unmanaged attributes, so none are sent.
        ResponseEntity<Void> response = http.post().uri(admin("/users")).headers(h -> h.setBearerAuth(token()))
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity();
        URI location = response.getHeaders().getLocation();
        if (location == null) {
            throw new IllegalStateException("Keycloak did not return the new user's id");
        }
        String path = location.getPath();
        return new ProvisionedIdentity(path.substring(path.lastIndexOf('/') + 1), password);
    }

    @Override
    public void disable(String subject) {
        http.put().uri(admin("/users/" + subject)).headers(h -> h.setBearerAuth(token()))
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("enabled", false)).retrieve().toBodilessEntity();
        http.post().uri(admin("/users/" + subject + "/logout")).headers(h -> h.setBearerAuth(token()))
                .retrieve().toBodilessEntity();
    }

    @Override
    public void enable(String subject) {
        http.put().uri(admin("/users/" + subject)).headers(h -> h.setBearerAuth(token()))
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("enabled", true)).retrieve().toBodilessEntity();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void setOneTimeCodeRequired(String subject, boolean required) {
        Map<String, Object> user = http.get().uri(admin("/users/" + subject)).headers(h -> h.setBearerAuth(token()))
                .retrieve().body(Map.class);
        List<Map<String, Object>> credentials = http.get().uri(admin("/users/" + subject + "/credentials"))
                .headers(h -> h.setBearerAuth(token())).retrieve().body(List.class);
        java.util.Set<String> actions = new java.util.LinkedHashSet<>(
                user == null || user.get("requiredActions") == null ? List.of() : (List<String>) user.get("requiredActions"));
        boolean hasOtp = credentials != null && credentials.stream().anyMatch(c -> "otp".equals(c.get("type")));
        if (required) {
            if (!hasOtp) {
                actions.add("CONFIGURE_TOTP");
            }
        } else {
            actions.remove("CONFIGURE_TOTP");
            if (credentials != null) {
                for (Map<String, Object> c : credentials) {
                    if ("otp".equals(c.get("type"))) {
                        http.delete().uri(admin("/users/" + subject + "/credentials/" + c.get("id")))
                                .headers(h -> h.setBearerAuth(token())).retrieve().toBodilessEntity();
                    }
                }
            }
        }
        http.put().uri(admin("/users/" + subject)).headers(h -> h.setBearerAuth(token()))
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("requiredActions", List.copyOf(actions)))
                .retrieve().toBodilessEntity();
    }

    @Override
    public void delete(String subject) {
        http.delete().uri(admin("/users/" + subject)).headers(h -> h.setBearerAuth(token())).retrieve().toBodilessEntity();
    }

    private String admin(String path) {
        return identity.internalUrl() + "/admin/realms/" + identity.realm() + path;
    }

    @SuppressWarnings("unchecked")
    private synchronized String token() {
        if (token != null && Instant.now().isBefore(tokenExpiry)) {
            return token;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", identity.provisionerClientId());
        form.add("client_secret", identity.provisionerClientSecret());
        Map<String, Object> result = http.post().uri(identity.internalRealmUrl() + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(Map.class);
        if (result == null || result.get("access_token") == null) {
            throw new IllegalStateException("Keycloak did not issue a provisioning token");
        }
        token = (String) result.get("access_token");
        long expiresIn = ((Number) result.getOrDefault("expires_in", 60)).longValue();
        tokenExpiry = Instant.now().plusSeconds(Math.max(10, expiresIn - 30));
        return token;
    }

    /** 16 characters meeting the realm policy (upper, lower, digit, length 12+). */
    static String temporaryPassword() {
        StringBuilder sb = new StringBuilder();
        String all = UPPER + LOWER + DIGITS;
        sb.append(UPPER.charAt(RANDOM.nextInt(UPPER.length())));
        sb.append(LOWER.charAt(RANDOM.nextInt(LOWER.length())));
        sb.append(DIGITS.charAt(RANDOM.nextInt(DIGITS.length())));
        for (int i = 0; i < 13; i++) {
            sb.append(all.charAt(RANDOM.nextInt(all.length())));
        }
        return sb.toString();
    }
}
