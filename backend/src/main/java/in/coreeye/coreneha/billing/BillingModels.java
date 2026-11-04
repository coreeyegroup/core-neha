package in.coreeye.coreneha.billing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request and response shapes of the billing API. Amounts are rupees with two decimals. */
public final class BillingModels {

    private BillingModels() {
    }

    public record TariffItemView(UUID id, UUID centreId, String code, String name, String category, BigDecimal price,
                                 BigDecimal gstRate, String status, OffsetDateTime updatedAt, int version) {
    }

    public record CreateTariffItem(@NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,39}$") String code,
                                   @NotBlank @Size(max = 200) String name,
                                   @NotBlank @Pattern(regexp = "REGISTRATION|CONSULTATION|INVESTIGATION|PROCEDURE|SURGERY_PACKAGE|OPTICAL|PHARMACY|OTHER") String category,
                                   @NotNull @DecimalMin("0") @DecimalMax("9999999") BigDecimal price,
                                   @NotNull BigDecimal gstRate) {
    }

    public record UpdateTariffItem(@NotBlank @Size(max = 200) String name,
                                   @NotNull @DecimalMin("0") @DecimalMax("9999999") BigDecimal price,
                                   @NotNull BigDecimal gstRate,
                                   @NotBlank @Pattern(regexp = "ACTIVE|RETIRED") String status,
                                   @NotNull Integer expectedVersion) {
    }

    public record BillView(UUID id, UUID centreId, String centreName, UUID patientId, String patientUhid, String patientName,
                           String billNo, LocalDate billDate, UUID visitRef, UUID surgeryRef, String status,
                           BigDecimal grossAmount, BigDecimal taxAmount, BigDecimal discountAmount, BigDecimal netAmount,
                           BigDecimal paidAmount, BigDecimal balance, BigDecimal discountRequested, String discountReason,
                           String discountStatus, String discountRequestedByName, String discountDecidedByName,
                           String discountDecisionNote, String cancelReason, String createdByName, OffsetDateTime createdAt,
                           int version) {
    }

    public record BillLineView(UUID id, UUID tariffItemId, String description, String category, int quantity,
                               BigDecimal unitPrice, BigDecimal gstRate, BigDecimal amount, BigDecimal taxAmount) {
    }

    public record PaymentView(UUID id, String mode, BigDecimal amount, String reference, LocalDate businessDate,
                              String receivedByName, OffsetDateTime receivedAt) {
    }

    public record BillDetail(BillView bill, List<BillLineView> lines, List<PaymentView> payments) {
    }

    public record LineInput(@NotNull UUID tariffItemId, @NotNull @Min(1) @Max(999) Integer quantity) {
    }

    public record CreateBill(@NotNull UUID patientId, UUID visitRef, UUID surgeryRef,
                             @NotEmpty @Size(max = 50) List<@Valid LineInput> lines) {
    }

    public record AddLines(@NotEmpty @Size(max = 50) List<@Valid LineInput> lines, @NotNull Integer expectedVersion) {
    }

    public record RemoveLine(@NotNull UUID lineId, @NotNull Integer expectedVersion) {
    }

    public record RequestDiscount(@NotNull @DecimalMin("0.01") BigDecimal amount, @NotBlank @Size(max = 500) String reason,
                                  @NotNull Integer expectedVersion) {
    }

    public record Decision(boolean approve, @Size(max = 500) String note, @NotNull Integer expectedVersion) {
    }

    public record CollectPayment(@NotBlank @Pattern(regexp = "CASH|CARD|UPI|INSURANCE|CORPORATE") String mode,
                                 @NotNull @DecimalMin("0.01") BigDecimal amount, @Size(max = 100) String reference,
                                 @NotNull Integer expectedVersion) {
    }

    public record CancelBill(@NotBlank @Size(max = 500) String reason, @NotNull Integer expectedVersion) {
    }

    public record AmountRow(String label, BigDecimal amount, int count) {
    }

    public record DayRow(LocalDate date, BigDecimal billed, BigDecimal collected, int bills) {
    }

    public record CollectionReport(UUID centreId, LocalDate from, LocalDate to, BigDecimal billed, BigDecimal discounts,
                                   BigDecimal collected, BigDecimal outstanding, int bills, int pendingDiscounts,
                                   List<AmountRow> byMode, List<AmountRow> byCategory, List<DayRow> byDay,
                                   List<AmountRow> byCollector, List<BillView> outstandingBills) {
    }

    public record DayCloseView(UUID id, UUID centreId, LocalDate businessDate, BigDecimal systemCash, BigDecimal systemCard,
                               BigDecimal systemUpi, BigDecimal systemOther, BigDecimal countedCash, BigDecimal variance,
                               String notes, String status, String submittedByName, OffsetDateTime submittedAt,
                               String decidedByName, OffsetDateTime decidedAt, String decisionNote, int version) {
    }

    public record DayClosePreview(LocalDate businessDate, BigDecimal systemCash, BigDecimal systemCard, BigDecimal systemUpi,
                                  BigDecimal systemOther, int payments, DayCloseView existing) {
    }

    public record SubmitDayClose(@NotNull LocalDate businessDate, @NotNull @DecimalMin("0") BigDecimal countedCash,
                                 @Size(max = 1000) String notes) {
    }
}
