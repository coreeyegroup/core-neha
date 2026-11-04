package in.coreeye.coreneha.workforce;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-only workforce lookups for other modules (no access checks; callers check). */
public interface WorkforceDirectory {

    record PersonRef(UUID id, UUID tenantId, String givenName, String familyName, String email, String status) {
    }

    record PostingRef(UUID id, UUID personId, UUID centreId, UUID positionId, String positionTitle, String departmentName,
                      boolean active) {
    }

    /** A person currently posted at a centre, with their position and department. */
    record StaffRef(UUID personId, String name, String positionTitle, String departmentCode, String departmentName) {
    }

    Optional<PersonRef> person(UUID personId);

    Optional<PostingRef> posting(UUID postingId);

    List<PostingRef> activePostings(UUID personId);

    /** People with a current posting at the centre, optionally only in one department (by library code). */
    List<StaffRef> staffAt(UUID centreId, String departmentCode);
}
