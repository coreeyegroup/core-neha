package in.coreeye.coreneha.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import in.coreeye.coreneha.kernel.Actor;

/**
 * The stock ledger as other modules (pharmacy, optical shop, optical lab) use it. Every call runs in
 * the caller's transaction and writes a movement row; the caller has already checked its own
 * permission. Expired batches are never taken; batches are taken first-expiry-first-out.
 */
public interface StockLedger {

    record ItemRef(UUID id, String code, String name, String category, String unit, BigDecimal gstRate, BigDecimal mrp,
                   String schedule, String lensType, BigDecimal lensIndex, String status) {
    }

    record StoreRef(UUID id, UUID centreId, String code, String name, String storeType, String status) {
    }

    record Taken(UUID batchId, UUID itemId, String batchNo, LocalDate expiryDate, int quantity, BigDecimal mrp) {
    }

    Optional<ItemRef> item(UUID itemId);

    Optional<StoreRef> store(UUID storeId);

    /** Active stores of a type in the tenant (for example the optical lab that serves every shop). */
    List<StoreRef> stores(String storeType);

    /** Takes {@code quantity} of an item from a store, earliest expiry first, across batches if needed. */
    List<Taken> takeFirstExpiry(Actor actor, UUID storeId, UUID itemId, int quantity, String movementType, String refType, UUID refId,
                                String refNo);

    /** Takes from one named batch (a chosen frame, a lens from stock). */
    Taken takeBatch(Actor actor, UUID storeId, UUID batchId, int quantity, String movementType, String refType, UUID refId, String refNo);

    /** Puts quantity back into a batch (a return, a cancelled order). */
    void putBack(Actor actor, UUID batchId, int quantity, String movementType, String refType, UUID refId, String refNo, String reason);

    /** The next document number at a centre, for example VEC/PH/2026-27/000012. */
    String nextNumber(Actor actor, UUID centreId, String docType);
}
