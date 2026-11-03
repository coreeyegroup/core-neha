package in.coreeye.coreneha.workforce;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-only workforce lookups for other modules (no access checks; callers check). */
public interface WorkforceDirectory {

    record PersonRef(UUID id, UUID tenantId, String givenName, String familyName, String email, String status) {
    }

    record PostingRef(UUID id, UUID personId, UUID centreId, UUID positionId, String positionTitle, boolean active) {
    }

    Optional<PersonRef> person(UUID personId);

    Optional<PostingRef> posting(UUID postingId);

    List<PostingRef> activePostings(UUID personId);
}
