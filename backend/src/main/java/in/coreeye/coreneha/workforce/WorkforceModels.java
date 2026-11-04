package in.coreeye.coreneha.workforce;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class WorkforceModels {

    private WorkforceModels() {
    }

    public record PositionView(UUID id, String code, String title, String category, String grade, String status, int version) {
    }

    public record EstablishmentView(UUID id, UUID positionId, String positionCode, String positionTitle, UUID centreId,
                                    UUID centreDepartmentId, String departmentName, int approvedHeadcount,
                                    int activePostings, LocalDate validFrom, LocalDate validTo, int version) {
    }

    public record PersonView(UUID id, String employeeNo, String givenName, String familyName, String email, boolean demo,
                             String status, int version) {
    }

    public record PostingView(UUID id, UUID personId, UUID establishmentId, UUID centreId, String centreName,
                              String positionTitle, String departmentName, BigDecimal fte, LocalDate validFrom,
                              LocalDate validTo, int version) {
    }

    public record PersonDetail(PersonView person, List<PostingView> postings) {
    }

    public record DefinePosition(@NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,39}$") String code,
                                 @NotBlank @Size(max = 200) String title,
                                 @NotBlank @Pattern(regexp = "CLINICAL|NURSING|ALLIED_HEALTH|TECHNICAL|ADMINISTRATIVE|MANAGEMENT|SUPPORT") String category,
                                 @Size(max = 40) String grade) {
    }

    public record Establish(@NotNull UUID positionId, UUID centreDepartmentId,
                            @NotNull @Min(1) @Max(10000) Integer approvedHeadcount, LocalDate validFrom) {
    }

    public record CreatePerson(@NotBlank @Size(max = 100) String givenName, @NotBlank @Size(max = 100) String familyName,
                               @NotBlank @Email @Size(max = 200) String email) {
    }

    public record AddPosting(@NotNull UUID establishmentId,
                             @DecimalMin(value = "0.01") @DecimalMax(value = "1.00") BigDecimal fte,
                             LocalDate validFrom, LocalDate validTo) {
    }

    public record EndPosting(@NotNull LocalDate validTo, @NotNull Integer expectedVersion) {
    }
}
