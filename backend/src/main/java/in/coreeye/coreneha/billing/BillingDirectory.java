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
}
