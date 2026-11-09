package in.coreeye.coreneha.diagnostics;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class DiagnosticsModels {

    private DiagnosticsModels() {
    }

    public record ParameterView(UUID id, String code, String name, String unit, String loincCode, String valueType, BigDecimal refLow,
                                BigDecimal refHigh, BigDecimal criticalLow, BigDecimal criticalHigh, String refText, int sortOrder) {
    }

    public record LabTestView(UUID id, String code, String name, String section, String sampleType, String container, String loincCode,
                              BigDecimal price, int tatHours, String status, List<ParameterView> parameters) {
    }

    public record ProcedureView(UUID id, String code, String name, String modality, String bodyPart, String preparation, BigDecimal price,
                                String status) {
    }

    public record Catalogue(List<LabTestView> labTests, List<ProcedureView> procedures) {
    }

    public record CreateOrders(@NotNull UUID patientId, UUID visitRef, @Pattern(regexp = "ROUTINE|URGENT|STAT") String priority,
                               @Size(max = 1000) String clinicalNotes, @Size(max = 30) List<UUID> labTestIds,
                               @Size(max = 10) List<UUID> procedureIds) {
    }

    public record ResultView(UUID parameterId, String code, String name, String unit, String loincCode, BigDecimal valueNumeric,
                             String valueText, String flag, String reference) {
    }

    public record DiagnosticOrderView(UUID id, UUID centreId, String requestNo, String accessionNo, UUID patientId, String patientUhid,
                            String patientName, String patientSex, Integer patientAge, UUID visitRef, String kind, String testCode,
                            String testName, String section, String sampleType, String modality, String bodyPart, String priority,
                            String clinicalNotes, String status, UUID billId, String billNo, String billStatus, String orderedByName,
                            Instant orderedAt, String collectedByName, Instant collectedAt, Instant receivedAt, String rejectionNote,
                            String resultByName, Instant resultAt, String validatedByName, Instant validatedAt, boolean hasCritical,
                            Instant scheduledAt, String performedByName, Instant performedAt, String studyInstanceUid, Integer imageCount,
                            String findings, String impression, String reportedByName, Instant reportedAt, String verifiedByName,
                            Instant verifiedAt, String cancelReason, int version, boolean released, String viewerUrl,
                            List<ResultView> results) {
    }

    public record Step(@NotNull Integer expectedVersion, @Size(max = 500) String note) {
    }

    public record ResultInput(@NotNull UUID parameterId, BigDecimal valueNumeric, @Size(max = 500) String valueText) {
    }

    public record EnterResults(@NotNull Integer expectedVersion, @NotEmpty @Size(max = 60) List<@Valid ResultInput> results) {
    }

    public record Schedule(@NotNull Integer expectedVersion, @NotNull Instant scheduledAt) {
    }

    public record Acquire(@NotNull Integer expectedVersion,
                          @Pattern(regexp = "^[0-9]+(\\.[0-9]+)*$") @Size(max = 64) String studyInstanceUid, Integer imageCount) {
    }

    public record Report(@NotNull Integer expectedVersion, @NotBlank @Size(max = 8000) String findings,
                         @NotBlank @Size(max = 2000) String impression) {
    }
}
