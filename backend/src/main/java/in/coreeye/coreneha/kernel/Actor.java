package in.coreeye.coreneha.kernel;

import java.util.UUID;

/** The authenticated staff user on whose behalf a request runs. */
public record Actor(UUID userId, UUID tenantId, UUID personId, String username, int accessVersion) {
}
