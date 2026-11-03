package in.coreeye.coreneha.org;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request and response shapes of the organisation API. */
public final class OrgModels {

    private OrgModels() {
    }

    public record LegalEntityView(UUID id, String code, String name, String status, int version) {
    }

    public record LevelView(UUID id, String code, String name, int rank, String status, int version) {
    }

    public record NodeView(UUID id, UUID levelId, String levelCode, UUID parentId, String code, String name,
                           String path, String status, int version) {
    }

    public record DepartmentView(UUID id, String code, String name, String category, String status, int version) {
    }

    public record CentreView(UUID id, UUID nodeId, String nodeName, UUID legalEntityId, String legalEntityName,
                             String code, String name, String centreType, String status, OffsetDateTime createdAt,
                             int version) {
    }

    public record CentreDepartmentView(UUID id, UUID departmentId, String departmentCode, String departmentName,
                                       String category, String localName, LocalDate validFrom, LocalDate validTo,
                                       int version) {
    }

    public record RetireImpact(UUID centreDepartmentId, int openEstablishments, int activePostings,
                               List<String> blockingReasons) {
    }

    public record CreateLegalEntity(@NotBlank @Size(max = 40) String code, @NotBlank @Size(max = 200) String name) {
    }

    public record CreateLevel(@NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,29}$") String code,
                              @NotBlank @Size(max = 100) String name,
                              @NotNull @Min(1) @Max(20) Integer rank) {
    }

    public record CreateNode(@NotNull UUID levelId, UUID parentId, @NotBlank @Size(max = 40) String code,
                             @NotBlank @Size(max = 200) String name) {
    }

    public record CreateDepartment(@NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,39}$") String code,
                                   @NotBlank @Size(max = 200) String name,
                                   @NotBlank @Pattern(regexp = "CLINICAL|CLINICAL_SUPPORT|ADMINISTRATIVE|OPERATIONS") String category) {
    }

    public record CreateCentre(@NotNull UUID nodeId, @NotNull UUID legalEntityId,
                               @NotBlank @Size(max = 40) String code, @NotBlank @Size(max = 200) String name,
                               @NotBlank @Size(max = 60) String centreType,
                               List<UUID> departmentIds) {
    }

    public record EnableDepartment(@NotNull UUID departmentId, @Size(max = 200) String localName, LocalDate validFrom) {
    }

    public record RetireDepartment(@NotNull LocalDate validTo, @NotNull Integer expectedVersion) {
    }
}
