package in.coreeye.coreneha.patient;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request and response shapes of the patient API. */
public final class PatientModels {

    private PatientModels() {
    }

    public record PatientView(UUID id, String uhid, String givenName, String familyName, String fullName, String sex,
                              LocalDate birthDate, boolean birthDateEstimated, Integer ageYears, String mobile, String email,
                              String addressLine, String city, String state, String pincode, String abhaNumber,
                              String guardianName, String emergencyContactName, String emergencyContactPhone,
                              UUID registeredCentreId, String registeredCentreName, boolean demo, String status,
                              OffsetDateTime createdAt, int version) {
    }

    public record RegisterPatient(
            @NotNull UUID centreId,
            @NotBlank @Size(max = 100) String givenName,
            @Size(max = 100) String familyName,
            @NotBlank @Pattern(regexp = "FEMALE|MALE|OTHER|UNKNOWN") String sex,
            LocalDate birthDate,
            @Min(0) @Max(120) Integer ageYears,
            @Pattern(regexp = "^[0-9+][0-9 -]{6,19}$", message = "Enter a valid mobile number") String mobile,
            @Email @Size(max = 200) String email,
            @Size(max = 300) String addressLine,
            @Size(max = 100) String city,
            @Size(max = 100) String state,
            @Pattern(regexp = "^[0-9]{6}$", message = "PIN code has 6 digits") String pincode,
            @Pattern(regexp = "^[0-9]{2}-[0-9]{4}-[0-9]{4}-[0-9]{4}$", message = "ABHA number looks like 12-3456-7890-1234") String abhaNumber,
            @Size(max = 200) String guardianName,
            @Size(max = 200) String emergencyContactName,
            @Size(max = 20) String emergencyContactPhone,
            boolean confirmNotDuplicate) {
    }

    public record UpdatePatient(
            @NotBlank @Size(max = 100) String givenName,
            @Size(max = 100) String familyName,
            @NotBlank @Pattern(regexp = "FEMALE|MALE|OTHER|UNKNOWN") String sex,
            LocalDate birthDate,
            @Min(0) @Max(120) Integer ageYears,
            @Pattern(regexp = "^[0-9+][0-9 -]{6,19}$", message = "Enter a valid mobile number") String mobile,
            @Email @Size(max = 200) String email,
            @Size(max = 300) String addressLine,
            @Size(max = 100) String city,
            @Size(max = 100) String state,
            @Pattern(regexp = "^[0-9]{6}$", message = "PIN code has 6 digits") String pincode,
            @Pattern(regexp = "^[0-9]{2}-[0-9]{4}-[0-9]{4}-[0-9]{4}$", message = "ABHA number looks like 12-3456-7890-1234") String abhaNumber,
            @Size(max = 200) String guardianName,
            @Size(max = 200) String emergencyContactName,
            @Size(max = 20) String emergencyContactPhone,
            @NotNull Integer expectedVersion) {
    }
}
