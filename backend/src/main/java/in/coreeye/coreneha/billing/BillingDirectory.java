package in.coreeye.coreneha.billing;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Bill summaries for other modules (no access checks; callers check their own permission). */
public interface BillingDirectory {

    record BillSummary(UUID id, String billNo, String status, BigDecimal netAmount, BigDecimal paidAmount,
                       BigDecimal balance, String discountStatus) {
    }

    record TariffRef(UUID id, UUID centreId, String code, String name, String category, BigDecimal price, String status) {
    }

    List<BillSummary> billsForVisit(UUID visitId);

    Optional<TariffRef> tariffRef(UUID tariffItemId);

    List<BillSummary> billsForSurgery(UUID surgeryId);

    /** A charge raised by another module (pharmacy sale, spectacle order); price excludes GST, which is added. */
    record SourcedLine(String description, String category, int quantity, BigDecimal unitPrice, BigDecimal gstRate) {
    }

    /**
     * Raises an open bill for charges that another module priced (pharmacy, optical). The caller has
     * already checked its own permission (pharmacy.dispense, optical.order); billing records who raised
     * it and the source, and the bill is then paid at the counter like any other (billing.collect).
     */
    java.util.Optional<BillSummary> bill(UUID billId);

    /** Cancels an unpaid bill raised by another module (a cancelled spectacle order); refuses once anything is paid. */
    void cancelSourcedBill(in.coreeye.coreneha.kernel.Actor actor, UUID billId, String reason);

    BillSummary raiseSourcedBill(in.coreeye.coreneha.kernel.Actor actor, UUID centreId, UUID patientId, UUID visitRef,
                                 String sourceType, UUID sourceRef, List<SourcedLine> lines);
}
