package in.coreeye.coreneha.optical;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class OpticalModels {

    private OpticalModels() {
    }

    public record CreateOrder(@NotNull UUID storeId, @NotNull UUID patientId, UUID visitRef,
                              @DecimalMin("-30") @DecimalMax("30") BigDecimal sphRe, @DecimalMin("-10") @DecimalMax("10") BigDecimal cylRe,
                              @Min(0) @Max(180) Integer axisRe, @DecimalMin("0") @DecimalMax("4") BigDecimal addRe,
                              @DecimalMin("-30") @DecimalMax("30") BigDecimal sphLe, @DecimalMin("-10") @DecimalMax("10") BigDecimal cylLe,
                              @Min(0) @Max(180) Integer axisLe, @DecimalMin("0") @DecimalMax("4") BigDecimal addLe,
                              @NotNull @DecimalMin("20") @DecimalMax("45") BigDecimal pdRe, @NotNull @DecimalMin("20") @DecimalMax("45") BigDecimal pdLe,
                              @DecimalMin("10") @DecimalMax("40") BigDecimal fittingHeight,
                              @NotBlank @Pattern(regexp = "STOCK|PATIENT_OWN") String frameSource, UUID frameBatchId,
                              @NotBlank @Pattern(regexp = "STOCK|LAB") String lensSource, @NotNull UUID lensItemId, UUID lensBatchId,
                              @Size(max = 60) String tint, LocalDate promisedDate, @Size(max = 500) String notes) {
    }

    public record OrderView(UUID id, UUID centreId, UUID storeId, String orderNo, UUID patientId, String patientUhid, String patientName,
                            UUID visitRef, BigDecimal sphRe, BigDecimal cylRe, Integer axisRe, BigDecimal addRe, BigDecimal sphLe,
                            BigDecimal cylLe, Integer axisLe, BigDecimal addLe, BigDecimal pdRe, BigDecimal pdLe, BigDecimal fittingHeight,
                            String lensType, BigDecimal lensIndex, String coating, String tint, String frameSource, String frameName,
                            String lensSource, String lensName, BigDecimal framePrice, BigDecimal lensPrice, BigDecimal totalAmount,
                            UUID billId, String billNo, String billStatus, BigDecimal billBalance, LocalDate promisedDate, String status,
                            String notes, String orderedByName, Instant orderedAt, String deliveredByName, Instant deliveredAt,
                            String cancelReason, UUID labJobId, String labJobNo, String labJobStatus, int version) {
    }

    public record OrderAction(@NotNull Integer expectedVersion, @Size(max = 300) String reason) {
    }

    public record JobView(UUID id, UUID labCentreId, String labCentreName, UUID labStoreId, String jobNo, UUID orderId, String orderNo,
                          String shopCentreName, String patientName, Map<String, Object> specification, String status, String blankItemName,
                          int remakeCount, String fittedByName, String qcByName, String qcResult, Map<String, Object> qcMeasured,
                          String qcNote, Instant receivedAt, Instant dispatchedAt, int version, List<JobEvent> events) {
    }

    public record JobEvent(String fromStatus, String toStatus, String note, String actorName, Instant at) {
    }

    public record AdvanceJob(@NotBlank @Pattern(regexp = "SURFACING|COATING|EDGING|FITTING|QC|DISPATCHED") String toStatus,
                             UUID blankItemId, @Size(max = 300) String note, @NotNull Integer expectedVersion) {
    }

    public record QualityCheck(@NotNull Boolean pass, @DecimalMin("-30") @DecimalMax("30") BigDecimal measuredSphRe,
                               @DecimalMin("-10") @DecimalMax("10") BigDecimal measuredCylRe, @Min(0) @Max(180) Integer measuredAxisRe,
                               @DecimalMin("-30") @DecimalMax("30") BigDecimal measuredSphLe,
                               @DecimalMin("-10") @DecimalMax("10") BigDecimal measuredCylLe, @Min(0) @Max(180) Integer measuredAxisLe,
                               @NotBlank @Size(max = 300) String note, @NotNull Integer expectedVersion) {
    }
}
