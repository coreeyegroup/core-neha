package in.coreeye.coreneha.pharmacy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class PharmacyModels {

    private PharmacyModels() {
    }

    public record PrescriptionLineInput(@NotNull UUID itemId, @NotBlank @Size(max = 60) String dose,
                                        @NotBlank @Size(max = 60) String frequency, @NotNull @Min(1) @Max(365) Integer durationDays,
                                        @Pattern(regexp = "RE|LE|BE") String eye,
                                        @Pattern(regexp = "TOPICAL|ORAL|INJECTION|OTHER") String route,
                                        @NotNull @Min(1) @Max(999) Integer quantity, @Size(max = 300) String instructions) {
    }

    public record CreatePrescription(@NotNull UUID patientId, UUID visitRef, @Size(max = 1000) String notes,
                                     @NotEmpty @Size(max = 30) List<@Valid PrescriptionLineInput> lines) {
    }

    public record PrescriptionLineView(UUID id, UUID itemId, String itemCode, String itemName, String schedule, String dose,
                                       String frequency, int durationDays, String eye, String route, int quantity, String instructions,
                                       int dispensedQty) {
    }

    public record PrescriptionView(UUID id, UUID centreId, UUID patientId, String patientUhid, String patientName, UUID visitRef,
                                   String prescriptionNo, String status, String notes, String prescribedByName, Instant prescribedAt,
                                   int version, List<PrescriptionLineView> lines) {
    }

    public record DispenseLineInput(@NotNull UUID itemId, @NotNull @Min(1) @Max(999) Integer quantity, UUID prescriptionLineId) {
    }

    public record Dispense(@NotNull UUID storeId, @NotNull UUID patientId, UUID prescriptionId,
                           @NotEmpty @Size(max = 40) List<@Valid DispenseLineInput> lines) {
    }

    public record SaleLineView(UUID id, UUID itemId, String itemCode, String itemName, String batchNo, LocalDate expiryDate, int quantity,
                               BigDecimal mrp, BigDecimal gstRate, BigDecimal amount, int returnedQty) {
    }

    public record SaleView(UUID id, UUID centreId, UUID storeId, String storeName, String saleNo, UUID patientId, String patientUhid,
                           String patientName, UUID prescriptionId, String prescriptionNo, UUID billId, String billNo, String billStatus,
                           BigDecimal totalAmount, BigDecimal returnedAmount, String soldByName, Instant soldAt, List<SaleLineView> lines) {
    }

    public record ReturnLine(@NotNull UUID saleLineId, @NotNull @Min(1) @Max(999) Integer quantity,
                             @NotBlank @Size(max = 300) String reason) {
    }
}
