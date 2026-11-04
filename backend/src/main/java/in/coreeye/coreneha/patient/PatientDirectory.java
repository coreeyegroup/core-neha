package in.coreeye.coreneha.patient;

import java.util.Optional;
import java.util.UUID;

/** Patient lookups for other modules (no access checks; callers check their own permission). */
public interface PatientDirectory {

    record PatientRef(UUID id, UUID tenantId, String uhid, String fullName, String sex, Integer ageYears, String mobile,
                      String status) {
    }

    Optional<PatientRef> patient(UUID patientId);
}
