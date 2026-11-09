package in.coreeye.coreneha.inventory;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import in.coreeye.coreneha.inventory.InventoryModels.AdjustmentView;
import in.coreeye.coreneha.inventory.InventoryModels.BatchView;
import in.coreeye.coreneha.inventory.InventoryModels.ItemView;
import in.coreeye.coreneha.inventory.InventoryModels.MovementView;
import in.coreeye.coreneha.inventory.InventoryModels.ReceiptView;
import in.coreeye.coreneha.inventory.InventoryModels.StockLine;
import in.coreeye.coreneha.inventory.InventoryModels.StoreView;
import in.coreeye.coreneha.inventory.InventoryModels.TransferView;
import in.coreeye.coreneha.kernel.CurrentActor;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
class InventoryController {

    private final InventoryService inventory;
    private final CurrentActor actor;

    InventoryController(InventoryService inventory, CurrentActor actor) {
        this.inventory = inventory;
        this.actor = actor;
    }

    @GetMapping("/inventory/items")
    List<ItemView> items(@RequestParam(required = false) String category, @RequestParam(required = false) String q,
                         @RequestParam(defaultValue = "false") boolean includeRetired) {
        return inventory.items(actor.require(), category, q, includeRetired);
    }

    @PostMapping("/inventory/items")
    @ResponseStatus(HttpStatus.CREATED)
    ItemView createItem(@Valid @RequestBody InventoryModels.SaveItem cmd) {
        return inventory.createItem(actor.require(), cmd);
    }

    @PutMapping("/inventory/items/{itemId}")
    ItemView updateItem(@PathVariable UUID itemId, @RequestParam(defaultValue = "false") boolean retire,
                        @Valid @RequestBody InventoryModels.SaveItem cmd) {
        return inventory.updateItem(actor.require(), itemId, cmd, retire);
    }

    @GetMapping("/centres/{centreId}/stores")
    List<StoreView> stores(@PathVariable UUID centreId) {
        return inventory.stores(actor.require(), centreId);
    }

    @PostMapping("/centres/{centreId}/stores")
    @ResponseStatus(HttpStatus.CREATED)
    StoreView createStore(@PathVariable UUID centreId, @Valid @RequestBody InventoryModels.CreateStore cmd) {
        return inventory.createStore(actor.require(), centreId, cmd);
    }

    @GetMapping("/stores/{storeId}/stock")
    List<StockLine> stock(@PathVariable UUID storeId, @RequestParam(required = false) String category) {
        return inventory.stock(actor.require(), storeId, category);
    }

    @GetMapping("/stores/{storeId}/batches")
    List<BatchView> batches(@PathVariable UUID storeId, @RequestParam(required = false) UUID itemId,
                            @RequestParam(defaultValue = "true") boolean inStock) {
        return inventory.batches(actor.require(), storeId, itemId, inStock);
    }

    @GetMapping("/stores/{storeId}/movements")
    List<MovementView> movements(@PathVariable UUID storeId, @RequestParam(required = false) LocalDate from,
                                 @RequestParam(required = false) LocalDate to) {
        return inventory.movements(actor.require(), storeId, from, to);
    }

    @GetMapping("/centres/{centreId}/expiring")
    List<BatchView> expiring(@PathVariable UUID centreId, @RequestParam(defaultValue = "90") int days) {
        return inventory.expiring(actor.require(), centreId, days);
    }

    @PostMapping("/inventory/receipts")
    @ResponseStatus(HttpStatus.CREATED)
    ReceiptView receive(@Valid @RequestBody InventoryModels.CreateReceipt cmd) {
        return inventory.receive(actor.require(), cmd);
    }

    @GetMapping("/centres/{centreId}/receipts")
    List<ReceiptView> receipts(@PathVariable UUID centreId) {
        return inventory.receipts(actor.require(), centreId);
    }

    @PostMapping("/inventory/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    TransferView transfer(@Valid @RequestBody InventoryModels.CreateTransfer cmd) {
        return inventory.transfer(actor.require(), cmd);
    }

    @GetMapping("/centres/{centreId}/transfers")
    List<TransferView> transfers(@PathVariable UUID centreId) {
        return inventory.transfers(actor.require(), centreId);
    }

    @PostMapping("/inventory/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    AdjustmentView requestAdjustment(@Valid @RequestBody InventoryModels.RequestAdjustment cmd) {
        return inventory.requestAdjustment(actor.require(), cmd);
    }

    @PostMapping("/inventory/adjustments/{adjustmentId}/decide")
    AdjustmentView decideAdjustment(@PathVariable UUID adjustmentId, @Valid @RequestBody InventoryModels.DecideAdjustment cmd) {
        return inventory.decideAdjustment(actor.require(), adjustmentId, cmd);
    }

    @GetMapping("/centres/{centreId}/adjustments")
    List<AdjustmentView> adjustments(@PathVariable UUID centreId, @RequestParam(required = false) String status) {
        return inventory.adjustments(actor.require(), centreId, status);
    }
}
