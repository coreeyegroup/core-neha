package in.coreeye.coreneha.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class InventoryModels {

    private InventoryModels() {
    }

    public record ItemView(UUID id, String code, String name, String category, String unit, String hsnCode, BigDecimal gstRate,
                           BigDecimal mrp, String genericName, String strength, String dosageForm, String schedule,
                           String manufacturer, String brand, String model, String colour, String sizeText, String lensType,
                           BigDecimal lensIndex, String coating, boolean batchTracked, boolean expiryTracked, int reorderLevel,
                           String status, int version) {
    }

    public record SaveItem(@NotBlank @Pattern(regexp = "^[A-Z0-9][A-Z0-9_-]{1,39}$") String code,
                           @NotBlank @Size(max = 200) String name,
                           @NotBlank @Pattern(regexp = "DRUG|CONSUMABLE|SURGICAL|FRAME|SPECTACLE_LENS|LENS_BLANK|CONTACT_LENS|OPTICAL_ACCESSORY|LAB_REAGENT") String category,
                           @NotBlank @Size(max = 20) String unit, @Size(max = 12) String hsnCode,
                           @NotNull BigDecimal gstRate, @DecimalMin("0") BigDecimal mrp,
                           @Size(max = 200) String genericName, @Size(max = 60) String strength, @Size(max = 60) String dosageForm,
                           @Pattern(regexp = "OTC|G|H|H1|X") String schedule, @Size(max = 200) String manufacturer,
                           @Size(max = 100) String brand, @Size(max = 100) String model, @Size(max = 60) String colour,
                           @Size(max = 40) String sizeText, @Pattern(regexp = "SINGLE_VISION|BIFOCAL|PROGRESSIVE") String lensType,
                           BigDecimal lensIndex, @Size(max = 60) String coating, Boolean expiryTracked,
                           @Min(0) @Max(100000) Integer reorderLevel, Integer expectedVersion) {
    }

    public record StoreView(UUID id, UUID centreId, String centreName, String code, String name, String storeType, String status,
                            int version) {
    }

    public record CreateStore(@NotBlank @Pattern(regexp = "^[A-Z0-9][A-Z0-9_-]{1,39}$") String code,
                              @NotBlank @Size(max = 200) String name,
                              @NotBlank @Pattern(regexp = "PHARMACY|OPTICAL|OPTICAL_LAB|OT|CENTRAL") String storeType) {
    }

    /** Stock of one item in one store: total, nearest expiry, and below reorder level. */
    public record StockLine(UUID itemId, String itemCode, String itemName, String category, String unit, int quantity,
                            int batches, LocalDate nearestExpiry, int reorderLevel, boolean belowReorder, BigDecimal stockValue) {
    }

    public record BatchView(UUID id, UUID storeId, UUID itemId, String itemCode, String itemName, String category, String batchNo,
                            LocalDate expiryDate, BigDecimal mrp, BigDecimal unitCost, int qtyOnHand, Instant receivedAt) {
    }

    public record MovementView(UUID id, UUID storeId, String storeName, String itemCode, String itemName, String batchNo,
                               String movementType, int quantity, int balanceAfter, String refType, UUID refId, String refNo,
                               String reason, String movedByName, Instant movedAt) {
    }

    public record ReceiptLineInput(@NotNull UUID itemId, @NotBlank @Size(max = 40) String batchNo, LocalDate expiryDate,
                                   @NotNull @Min(1) @Max(100000) Integer quantity, @NotNull @DecimalMin("0") BigDecimal unitCost,
                                   @NotNull @DecimalMin("0") BigDecimal mrp) {
    }

    public record CreateReceipt(@NotNull UUID storeId, @NotBlank @Size(max = 200) String supplierName,
                                @NotBlank @Size(max = 60) String supplierInvoiceNo, @NotNull LocalDate invoiceDate,
                                @NotEmpty @Size(max = 200) List<@Valid ReceiptLineInput> lines) {
    }

    public record ReceiptView(UUID id, UUID centreId, UUID storeId, String storeName, String receiptNo, String supplierName,
                              String supplierInvoiceNo, LocalDate invoiceDate, BigDecimal totalCost, String receivedByName,
                              Instant receivedAt, int lines) {
    }

    public record TransferLineInput(@NotNull UUID batchId, @NotNull @Min(1) @Max(100000) Integer quantity) {
    }

    public record CreateTransfer(@NotNull UUID fromStoreId, @NotNull UUID toStoreId, @Size(max = 500) String note,
                                 @NotEmpty @Size(max = 100) List<@Valid TransferLineInput> lines) {
    }

    public record TransferView(UUID id, String transferNo, UUID fromStoreId, String fromStoreName, UUID toStoreId, String toStoreName,
                               String note, String sentByName, Instant sentAt) {
    }

    public record RequestAdjustment(@NotNull UUID batchId, @NotNull @Min(-100000) @Max(100000) Integer quantity,
                                    @NotBlank @Pattern(regexp = "DAMAGED|EXPIRED|COUNT_CORRECTION|LOST|OTHER") String reason,
                                    @NotBlank @Size(max = 500) String note) {
    }

    public record AdjustmentView(UUID id, UUID storeId, String storeName, UUID batchId, String itemCode, String itemName, String batchNo,
                                 int quantity, String reason, String note, String status, String requestedByName, Instant requestedAt,
                                 String decidedByName, Instant decidedAt, String decisionNote, int version) {
    }

    public record DecideAdjustment(@NotNull Boolean approve, @NotBlank @Size(max = 500) String note, @NotNull Integer expectedVersion) {
    }
}
