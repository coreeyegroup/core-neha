package in.coreeye.coreneha.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import in.coreeye.coreneha.audit.AuditEntry;
import in.coreeye.coreneha.audit.AuditLog;
import in.coreeye.coreneha.inventory.InventoryModels.AdjustmentView;
import in.coreeye.coreneha.inventory.InventoryModels.BatchView;
import in.coreeye.coreneha.inventory.InventoryModels.ItemView;
import in.coreeye.coreneha.inventory.InventoryModels.MovementView;
import in.coreeye.coreneha.inventory.InventoryModels.ReceiptView;
import in.coreeye.coreneha.inventory.InventoryModels.StockLine;
import in.coreeye.coreneha.inventory.InventoryModels.StoreView;
import in.coreeye.coreneha.inventory.InventoryModels.TransferView;
import in.coreeye.coreneha.kernel.AccessControl;
import in.coreeye.coreneha.kernel.AccessDeniedException;
import in.coreeye.coreneha.kernel.Actor;
import in.coreeye.coreneha.kernel.BusinessTime;
import in.coreeye.coreneha.kernel.ConflictException;
import in.coreeye.coreneha.kernel.NotFoundException;
import in.coreeye.coreneha.kernel.Permission;
import in.coreeye.coreneha.kernel.RuleViolationException;
import in.coreeye.coreneha.kernel.Scope;
import in.coreeye.coreneha.org.OrgDirectory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Item master (per chain), stores (per centre), stock by batch and expiry, goods receipts, transfers,
 * adjustments that need a second person, and the append-only stock ledger (INV-01..INV-10).
 */
@Service
public class InventoryService implements StockLedger {

    static String userName(String column) {
        return "(select trim(wp.given_name || ' ' || wp.family_name) from iam.user_account ua "
                + "join workforce.person wp on wp.id = ua.person_id where ua.id = " + column + ")";
    }

    private static final String ITEM_SELECT = """
            select id, code, name, category, unit, hsn_code, gst_rate, mrp, generic_name, strength, dosage_form, schedule, manufacturer,
                   brand, model, colour, size_text, lens_type, lens_index, coating, batch_tracked, expiry_tracked, reorder_level, status, version
            from inventory.item""";
    private static final String STORE_SELECT = """
            select s.id, s.centre_id, c.name as centre_name, s.code, s.name, s.store_type, s.status, s.version
            from inventory.store s join org.centre c on c.id = s.centre_id""";
    private static final String BATCH_SELECT = """
            select b.id, b.store_id, b.item_id, i.code as item_code, i.name as item_name, i.category, b.batch_no, b.expiry_date, b.mrp,
                   b.unit_cost, b.qty_on_hand, b.received_at
            from inventory.stock_batch b join inventory.item i on i.id = b.item_id""";

    private final JdbcClient jdbc;
    private final AccessControl access;
    private final AuditLog audit;
    private final OrgDirectory org;

    InventoryService(JdbcClient jdbc, AccessControl access, AuditLog audit, OrgDirectory org) {
        this.jdbc = jdbc;
        this.access = access;
        this.audit = audit;
        this.org = org;
    }

    // ---------------------------------------------------------------- item master (chain-wide)

    /** Anyone who works with stock, prescribes or sells may read the item master. */
    public List<ItemView> items(Actor actor, String category, String query, boolean includeRetired) {
        requireAnyAnywhere(actor, Permission.INVENTORY_READ, Permission.PHARMACY_PRESCRIBE, Permission.PHARMACY_READ,
                Permission.OPTICAL_READ, Permission.OPTICALLAB_READ, Permission.INVENTORY_ITEM_MANAGE);
        String q = query == null || query.isBlank() ? null : "%" + query.trim().toLowerCase() + "%";
        return jdbc.sql(ITEM_SELECT + " " + """
                 where (cast(:cat as text) is null or category = :cat) and (:all or status = 'ACTIVE')
                   and (cast(:q as text) is null or lower(name) like :q or lower(code) like :q or lower(coalesce(generic_name, '')) like :q)
                 order by category, name limit 500""")
                .param("cat", category).param("all", includeRetired).param("q", q).query(ItemView.class).list();
    }

    @Transactional
    public ItemView createItem(Actor actor, InventoryModels.SaveItem cmd) {
        access.require(actor, Permission.INVENTORY_ITEM_MANAGE, Scope.tenant(actor.tenantId()), "inventory.item.create", "item", null);
        boolean drug = "DRUG".equals(cmd.category());
        if (drug && cmd.schedule() == null) {
            throw new RuleViolationException("inventory.drug-schedule", "Give the drug schedule (OTC, G, H, H1 or X)");
        }
        boolean expiry = drug || Boolean.TRUE.equals(cmd.expiryTracked());
        UUID id;
        try {
            id = jdbc.sql("""
                    insert into inventory.item (tenant_id, code, name, category, unit, hsn_code, gst_rate, mrp, generic_name, strength,
                        dosage_form, schedule, manufacturer, brand, model, colour, size_text, lens_type, lens_index, coating,
                        batch_tracked, expiry_tracked, reorder_level)
                    values (:t, :code, :name, :cat, :unit, :hsn, :gst, :mrp, :gen, :str, :form, :sch, :mfr, :brand, :model, :colour,
                        :size, :lt, :li, :coat, true, :exp, :reorder) returning id""")
                    .param("t", actor.tenantId()).param("code", cmd.code()).param("name", cmd.name().trim()).param("cat", cmd.category())
                    .param("unit", cmd.unit()).param("hsn", cmd.hsnCode()).param("gst", cmd.gstRate()).param("mrp", cmd.mrp())
                    .param("gen", cmd.genericName()).param("str", cmd.strength()).param("form", cmd.dosageForm())
                    .param("sch", cmd.schedule()).param("mfr", cmd.manufacturer()).param("brand", cmd.brand()).param("model", cmd.model())
                    .param("colour", cmd.colour()).param("size", cmd.sizeText()).param("lt", cmd.lensType()).param("li", cmd.lensIndex())
                    .param("coat", cmd.coating()).param("exp", expiry).param("reorder", cmd.reorderLevel() == null ? 0 : cmd.reorderLevel())
                    .query(UUID.class).single();
        } catch (DuplicateKeyException e) {
            throw new ConflictException("inventory.item.exists", "An item with code " + cmd.code() + " already exists");
        }
        audit.record(AuditEntry.success(actor, "inventory.item.create", "item", id, Scope.tenant(actor.tenantId()),
                Map.of("code", cmd.code(), "name", cmd.name(), "category", cmd.category())));
        return itemView(id);
    }

    @Transactional
    public ItemView updateItem(Actor actor, UUID itemId, InventoryModels.SaveItem cmd, boolean retire) {
        access.require(actor, Permission.INVENTORY_ITEM_MANAGE, Scope.tenant(actor.tenantId()), "inventory.item.update", "item",
                itemId.toString());
        ItemView before = itemView(itemId);
        if (cmd.expectedVersion() != null && cmd.expectedVersion() != before.version()) {
            throw new ConflictException("inventory.item.changed", "Someone else changed this item. Reload and try again.");
        }
        jdbc.sql("""
                update inventory.item set name = :name, unit = :unit, hsn_code = :hsn, gst_rate = :gst, mrp = :mrp, generic_name = :gen,
                    strength = :str, dosage_form = :form, schedule = :sch, manufacturer = :mfr, brand = :brand, model = :model,
                    colour = :colour, size_text = :size, lens_type = :lt, lens_index = :li, coating = :coat,
                    reorder_level = :reorder, status = :status, version = version + 1
                where id = :id""")
                .param("id", itemId).param("name", cmd.name().trim()).param("unit", cmd.unit()).param("hsn", cmd.hsnCode())
                .param("gst", cmd.gstRate()).param("mrp", cmd.mrp()).param("gen", cmd.genericName()).param("str", cmd.strength())
                .param("form", cmd.dosageForm()).param("sch", "DRUG".equals(before.category()) ? cmd.schedule() : null)
                .param("mfr", cmd.manufacturer()).param("brand", cmd.brand()).param("model", cmd.model()).param("colour", cmd.colour())
                .param("size", cmd.sizeText()).param("lt", cmd.lensType()).param("li", cmd.lensIndex()).param("coat", cmd.coating())
                .param("reorder", cmd.reorderLevel() == null ? before.reorderLevel() : cmd.reorderLevel())
                .param("status", retire ? "RETIRED" : "ACTIVE").update();
        Map<String, Object> details = new HashMap<>();
        details.put("code", before.code());
        details.put("mrpBefore", String.valueOf(before.mrp()));
        details.put("mrpAfter", String.valueOf(cmd.mrp()));
        details.put("status", retire ? "RETIRED" : "ACTIVE");
        audit.record(AuditEntry.success(actor, "inventory.item.update", "item", itemId, Scope.tenant(actor.tenantId()), details));
        return itemView(itemId);
    }

    private ItemView itemView(UUID id) {
        return jdbc.sql(ITEM_SELECT + " where id = :id").param("id", id).query(ItemView.class).optional()
                .orElseThrow(() -> new NotFoundException("Item not found"));
    }

    // ---------------------------------------------------------------- stores (per centre)

    public List<StoreView> stores(Actor actor, UUID centreId) {
        requireAnyAt(actor, Scope.centre(centreId), Permission.INVENTORY_READ, Permission.PHARMACY_DISPENSE, Permission.OPTICAL_ORDER,
                Permission.OPTICALLAB_PROCESS);
        return jdbc.sql(STORE_SELECT + " where s.centre_id = :c order by s.store_type, s.name").param("c", centreId)
                .query(StoreView.class).list();
    }

    @Transactional
    public StoreView createStore(Actor actor, UUID centreId, InventoryModels.CreateStore cmd) {
        Scope scope = Scope.centre(centreId);
        access.require(actor, Permission.INVENTORY_STORE_MANAGE, scope, "inventory.store.create", "store", null);
        org.centre(centreId).orElseThrow(() -> new NotFoundException("Centre not found"));
        UUID id;
        try {
            id = jdbc.sql("""
                    insert into inventory.store (tenant_id, centre_id, code, name, store_type) values (:t, :c, :code, :name, :type)
                    returning id""").param("t", actor.tenantId()).param("c", centreId).param("code", cmd.code())
                    .param("name", cmd.name().trim()).param("type", cmd.storeType()).query(UUID.class).single();
        } catch (DuplicateKeyException e) {
            throw new ConflictException("inventory.store.exists", "A store with code " + cmd.code() + " already exists at this centre");
        }
        audit.record(AuditEntry.success(actor, "inventory.store.create", "store", id, scope,
                Map.of("code", cmd.code(), "name", cmd.name(), "type", cmd.storeType())));
        return storeView(id);
    }

    private StoreView storeView(UUID id) {
        return jdbc.sql(STORE_SELECT + " where s.id = :id").param("id", id).query(StoreView.class).optional()
                .orElseThrow(() -> new NotFoundException("Store not found"));
    }

    // ---------------------------------------------------------------- stock

    public List<StockLine> stock(Actor actor, UUID storeId, String category) {
        StoreView store = readableStore(actor, storeId);
        return jdbc.sql("""
                select i.id as item_id, i.code as item_code, i.name as item_name, i.category, i.unit,
                       coalesce(sum(b.qty_on_hand), 0)::int as quantity, count(b.id) filter (where b.qty_on_hand > 0)::int as batches,
                       min(b.expiry_date) filter (where b.qty_on_hand > 0) as nearest_expiry, i.reorder_level,
                       coalesce(sum(b.qty_on_hand), 0) <= i.reorder_level as below_reorder,
                       coalesce(sum(b.qty_on_hand * b.unit_cost), 0) as stock_value
                from inventory.item i
                left join inventory.stock_batch b on b.item_id = i.id and b.store_id = :s
                where i.status = 'ACTIVE' and (cast(:cat as text) is null or i.category = :cat)
                  and (b.id is not null or i.category in (select unnest(cast(:cats as text[]))))
                group by i.id order by i.category, i.name""")
                .param("s", store.id()).param("cat", category).param("cats", categoriesFor(store.storeType()))
                .query(StockLine.class).list();
    }

    public List<BatchView> batches(Actor actor, UUID storeId, UUID itemId, boolean onlyInStock) {
        StoreView store = readableStore(actor, storeId);
        return jdbc.sql(BATCH_SELECT + " " + """
                 where b.store_id = :s and (cast(:i as uuid) is null or b.item_id = :i) and (not :only or b.qty_on_hand > 0)
                 order by i.name, b.expiry_date nulls last, b.received_at""")
                .param("s", store.id()).param("i", itemId).param("only", onlyInStock).query(BatchView.class).list();
    }

    public List<MovementView> movements(Actor actor, UUID storeId, LocalDate from, LocalDate to) {
        StoreView store = readableStore(actor, storeId);
        LocalDate f = from == null ? BusinessTime.today().minusDays(30) : from;
        LocalDate t = to == null ? BusinessTime.today() : to;
        return jdbc.sql("""
                select m.id, m.store_id, s.name as store_name, i.code as item_code, i.name as item_name, b.batch_no, m.movement_type,
                       m.quantity, m.balance_after, m.ref_type, m.ref_id, m.ref_no, m.reason, %s as moved_by_name, m.moved_at
                from inventory.stock_movement m join inventory.store s on s.id = m.store_id
                join inventory.item i on i.id = m.item_id join inventory.stock_batch b on b.id = m.batch_id
                where m.store_id = :s and (m.moved_at at time zone 'Asia/Kolkata')::date between :f and :t
                order by m.moved_at desc limit 1000""".formatted(userName("m.moved_by")))
                .param("s", store.id()).param("f", f).param("t", t).query(MovementView.class).list();
    }

    /** Batches that have expired or expire within {@code days}, with stock, across the centre's stores. */
    public List<BatchView> expiring(Actor actor, UUID centreId, int days) {
        access.require(actor, Permission.INVENTORY_READ, Scope.centre(centreId), "inventory.expiry.read", "centre", centreId.toString());
        return jdbc.sql(BATCH_SELECT + " " + """
                 join inventory.store s on s.id = b.store_id
                 where s.centre_id = :c and b.qty_on_hand > 0 and b.expiry_date is not null and b.expiry_date <= :until
                 order by b.expiry_date, i.name""")
                .param("c", centreId).param("until", BusinessTime.today().plusDays(days)).query(BatchView.class).list();
    }

    // ---------------------------------------------------------------- goods receipt

    @Transactional
    public ReceiptView receive(Actor actor, InventoryModels.CreateReceipt cmd) {
        StoreView store = activeStore(cmd.storeId());
        Scope scope = Scope.centre(store.centreId());
        access.require(actor, Permission.INVENTORY_RECEIVE, scope, "inventory.receipt.create", "store", store.id().toString());
        String receiptNo = nextNumber(actor, store.centreId(), "GRN");
        BigDecimal total = BigDecimal.ZERO;
        UUID receiptId;
        try {
            receiptId = jdbc.sql("""
                    insert into inventory.receipt (tenant_id, centre_id, store_id, receipt_no, supplier_name, supplier_invoice_no, invoice_date,
                        total_cost, received_by)
                    values (:t, :c, :s, :no, :sup, :inv, :d, 0, :by) returning id""")
                    .param("t", actor.tenantId()).param("c", store.centreId()).param("s", store.id()).param("no", receiptNo)
                    .param("sup", cmd.supplierName().trim()).param("inv", cmd.supplierInvoiceNo().trim()).param("d", cmd.invoiceDate())
                    .param("by", actor.userId()).query(UUID.class).single();
        } catch (DuplicateKeyException e) {
            throw new ConflictException("inventory.receipt.duplicate",
                    "Invoice " + cmd.supplierInvoiceNo() + " from " + cmd.supplierName() + " has already been received into this store");
        }
        for (InventoryModels.ReceiptLineInput line : cmd.lines()) {
            ItemRef item = item(line.itemId()).orElseThrow(() -> new NotFoundException("Item not found"));
            if (!"ACTIVE".equals(item.status())) {
                throw new RuleViolationException("inventory.item.retired", item.name() + " is retired");
            }
            boolean expiryTracked = jdbc.sql("select expiry_tracked from inventory.item where id = :i").param("i", item.id())
                    .query(Boolean.class).single();
            if (expiryTracked && line.expiryDate() == null) {
                throw new RuleViolationException("inventory.expiry-required", "Give the expiry date for " + item.name());
            }
            if (line.expiryDate() != null && !line.expiryDate().isAfter(BusinessTime.today())) {
                throw new RuleViolationException("inventory.expired-on-receipt", item.name() + " batch " + line.batchNo() + " has already expired");
            }
            UUID batchId = jdbc.sql("""
                    insert into inventory.stock_batch (tenant_id, store_id, item_id, batch_no, expiry_date, mrp, unit_cost, qty_on_hand)
                    values (:t, :s, :i, :b, :e, :mrp, :cost, 0)
                    on conflict (store_id, item_id, batch_no) do update set mrp = excluded.mrp, unit_cost = excluded.unit_cost
                    returning id""")
                    .param("t", actor.tenantId()).param("s", store.id()).param("i", item.id()).param("b", line.batchNo().trim())
                    .param("e", line.expiryDate()).param("mrp", line.mrp()).param("cost", line.unitCost()).query(UUID.class).single();
            move(actor, batchId, line.quantity(), "RECEIPT", "RECEIPT", receiptId, receiptNo, null);
            jdbc.sql("""
                    insert into inventory.receipt_line (tenant_id, receipt_id, item_id, batch_id, quantity, unit_cost, mrp)
                    values (:t, :r, :i, :b, :q, :c, :m)""")
                    .param("t", actor.tenantId()).param("r", receiptId).param("i", item.id()).param("b", batchId)
                    .param("q", line.quantity()).param("c", line.unitCost()).param("m", line.mrp()).update();
            total = total.add(line.unitCost().multiply(BigDecimal.valueOf(line.quantity())));
        }
        jdbc.sql("update inventory.receipt set total_cost = :t where id = :id").param("t", total).param("id", receiptId).update();
        audit.record(AuditEntry.success(actor, "inventory.receipt.create", "receipt", receiptId, scope,
                Map.of("receiptNo", receiptNo, "store", store.code(), "supplier", cmd.supplierName(), "invoice", cmd.supplierInvoiceNo(),
                        "lines", cmd.lines().size(), "totalCost", total.toPlainString())));
        return receiptRows("r.id = :id", Map.of("id", receiptId)).getFirst();
    }

    public List<ReceiptView> receipts(Actor actor, UUID centreId) {
        access.require(actor, Permission.INVENTORY_READ, Scope.centre(centreId), "inventory.receipt.read", "centre", centreId.toString());
        return receiptRows("r.centre_id = :c", Map.of("c", centreId));
    }

    private List<ReceiptView> receiptRows(String where, Map<String, Object> params) {
        return jdbc.sql("""
                select r.id, r.centre_id, r.store_id, s.name as store_name, r.receipt_no, r.supplier_name, r.supplier_invoice_no,
                       r.invoice_date, r.total_cost, %s as received_by_name, r.received_at,
                       (select count(*) from inventory.receipt_line l where l.receipt_id = r.id)::int as lines
                from inventory.receipt r join inventory.store s on s.id = r.store_id where %s
                order by r.received_at desc limit 300""".formatted(userName("r.received_by"), where))
                .params(params).query(ReceiptView.class).list();
    }

    // ---------------------------------------------------------------- transfers

    @Transactional
    public TransferView transfer(Actor actor, InventoryModels.CreateTransfer cmd) {
        StoreView from = activeStore(cmd.fromStoreId());
        StoreView to = activeStore(cmd.toStoreId());
        if (from.id().equals(to.id())) {
            throw new RuleViolationException("inventory.transfer.same-store", "Choose a different store to send to");
        }
        access.require(actor, Permission.INVENTORY_TRANSFER, Scope.centre(from.centreId()), "inventory.transfer.create", "store",
                from.id().toString());
        String no = nextNumber(actor, from.centreId(), "TRF");
        UUID id = jdbc.sql("""
                insert into inventory.transfer (tenant_id, transfer_no, from_store_id, to_store_id, note, sent_by)
                values (:t, :no, :f, :to, :note, :by) returning id""")
                .param("t", actor.tenantId()).param("no", no).param("f", from.id()).param("to", to.id()).param("note", cmd.note())
                .param("by", actor.userId()).query(UUID.class).single();
        for (InventoryModels.TransferLineInput line : cmd.lines()) {
            BatchView batch = batchRow(line.batchId());
            if (!batch.storeId().equals(from.id())) {
                throw new RuleViolationException("inventory.batch.other-store", "Batch " + batch.batchNo() + " is not in " + from.name());
            }
            move(actor, batch.id(), -line.quantity(), "TRANSFER_OUT", "TRANSFER", id, no, "to " + to.name());
            UUID target = jdbc.sql("""
                    insert into inventory.stock_batch (tenant_id, store_id, item_id, batch_no, expiry_date, mrp, unit_cost, qty_on_hand)
                    values (:t, :s, :i, :b, :e, :mrp, :cost, 0)
                    on conflict (store_id, item_id, batch_no) do update set mrp = excluded.mrp returning id""")
                    .param("t", actor.tenantId()).param("s", to.id()).param("i", batch.itemId()).param("b", batch.batchNo())
                    .param("e", batch.expiryDate()).param("mrp", batch.mrp()).param("cost", batch.unitCost()).query(UUID.class).single();
            move(actor, target, line.quantity(), "TRANSFER_IN", "TRANSFER", id, no, "from " + from.name());
        }
        audit.record(AuditEntry.success(actor, "inventory.transfer.create", "transfer", id, Scope.centre(from.centreId()),
                Map.of("transferNo", no, "from", from.code(), "to", to.code(), "lines", cmd.lines().size())));
        return transferRows("t.id = :id", Map.of("id", id)).getFirst();
    }

    public List<TransferView> transfers(Actor actor, UUID centreId) {
        access.require(actor, Permission.INVENTORY_READ, Scope.centre(centreId), "inventory.transfer.read", "centre", centreId.toString());
        return transferRows("(f.centre_id = :c or s2.centre_id = :c)", Map.of("c", centreId));
    }

    private List<TransferView> transferRows(String where, Map<String, Object> params) {
        return jdbc.sql("""
                select t.id, t.transfer_no, t.from_store_id, f.name as from_store_name, t.to_store_id, s2.name as to_store_name, t.note,
                       %s as sent_by_name, t.sent_at
                from inventory.transfer t join inventory.store f on f.id = t.from_store_id join inventory.store s2 on s2.id = t.to_store_id
                where %s order by t.sent_at desc limit 300""".formatted(userName("t.sent_by"), where))
                .params(params).query(TransferView.class).list();
    }

    // ---------------------------------------------------------------- adjustments (two people)

    @Transactional
    public AdjustmentView requestAdjustment(Actor actor, InventoryModels.RequestAdjustment cmd) {
        BatchView batch = batchRow(cmd.batchId());
        StoreView store = storeView(batch.storeId());
        Scope scope = Scope.centre(store.centreId());
        access.require(actor, Permission.INVENTORY_ADJUST_REQUEST, scope, "inventory.adjustment.request", "batch", batch.id().toString());
        if (batch.qtyOnHand() + cmd.quantity() < 0) {
            throw new RuleViolationException("inventory.adjustment.below-zero", "Only " + batch.qtyOnHand() + " in stock in this batch");
        }
        UUID id = jdbc.sql("""
                insert into inventory.adjustment (tenant_id, centre_id, store_id, batch_id, quantity, reason, note, requested_by)
                values (:t, :c, :s, :b, :q, :r, :n, :by) returning id""")
                .param("t", actor.tenantId()).param("c", store.centreId()).param("s", store.id()).param("b", batch.id())
                .param("q", cmd.quantity()).param("r", cmd.reason()).param("n", cmd.note().trim()).param("by", actor.userId())
                .query(UUID.class).single();
        audit.record(AuditEntry.success(actor, "inventory.adjustment.request", "adjustment", id, scope,
                Map.of("item", batch.itemCode(), "batch", batch.batchNo(), "quantity", cmd.quantity(), "reason", cmd.reason())));
        return adjustmentRows("a.id = :id", Map.of("id", id)).getFirst();
    }

    @Transactional
    public AdjustmentView decideAdjustment(Actor actor, UUID adjustmentId, InventoryModels.DecideAdjustment cmd) {
        AdjustmentView a = jdbc.sql("select id from inventory.adjustment where id = :id for update").param("id", adjustmentId)
                .query(UUID.class).optional().map(id -> adjustmentRows("a.id = :id", Map.of("id", id)).getFirst())
                .orElseThrow(() -> new NotFoundException("Adjustment not found"));
        StoreView store = storeView(a.storeId());
        Scope scope = Scope.centre(store.centreId());
        access.require(actor, Permission.INVENTORY_ADJUST_APPROVE, scope, "inventory.adjustment.decide", "adjustment", adjustmentId.toString());
        if (!"PENDING".equals(a.status())) {
            throw new RuleViolationException("inventory.adjustment.decided", "This adjustment has already been decided");
        }
        if (cmd.expectedVersion() != a.version()) {
            throw new ConflictException("inventory.adjustment.changed", "Someone else changed this adjustment. Reload and try again.");
        }
        UUID requestedBy = jdbc.sql("select requested_by from inventory.adjustment where id = :id").param("id", adjustmentId)
                .query(UUID.class).single();
        if (requestedBy.equals(actor.userId())) {
            throw new AccessDeniedException("inventory.adjustment.self", "You cannot decide an adjustment you requested; another person must");
        }
        String status = cmd.approve() ? "APPROVED" : "REJECTED";
        jdbc.sql("""
                update inventory.adjustment set status = :s, decided_by = :by, decided_at = now(), decision_note = :n, version = version + 1
                where id = :id""").param("s", status).param("by", actor.userId()).param("n", cmd.note()).param("id", adjustmentId).update();
        if (cmd.approve()) {
            move(actor, a.batchId(), a.quantity(), "ADJUSTMENT", "ADJUSTMENT", adjustmentId, null, a.reason() + ": " + a.note());
        }
        audit.record(AuditEntry.success(actor, "inventory.adjustment." + status.toLowerCase(), "adjustment", adjustmentId, scope,
                Map.of("item", a.itemCode(), "batch", a.batchNo(), "quantity", a.quantity(), "note", cmd.note())));
        return adjustmentRows("a.id = :id", Map.of("id", adjustmentId)).getFirst();
    }

    public List<AdjustmentView> adjustments(Actor actor, UUID centreId, String status) {
        access.require(actor, Permission.INVENTORY_READ, Scope.centre(centreId), "inventory.adjustment.read", "centre", centreId.toString());
        Map<String, Object> p = new HashMap<>();
        p.put("c", centreId);
        p.put("st", status);
        return adjustmentRows("a.centre_id = :c and (cast(:st as text) is null or a.status = :st)", p);
    }

    private List<AdjustmentView> adjustmentRows(String where, Map<String, Object> params) {
        return jdbc.sql("""
                select a.id, a.store_id, s.name as store_name, a.batch_id, i.code as item_code, i.name as item_name, b.batch_no, a.quantity,
                       a.reason, a.note, a.status, %s as requested_by_name, a.requested_at, %s as decided_by_name, a.decided_at,
                       a.decision_note, a.version
                from inventory.adjustment a join inventory.store s on s.id = a.store_id
                join inventory.stock_batch b on b.id = a.batch_id join inventory.item i on i.id = b.item_id
                where %s order by a.requested_at desc limit 300""".formatted(userName("a.requested_by"), userName("a.decided_by"), where))
                .params(params).query(AdjustmentView.class).list();
    }

    // ---------------------------------------------------------------- StockLedger (used by pharmacy and optical)

    @Override
    public Optional<ItemRef> item(UUID itemId) {
        return jdbc.sql("""
                select id, code, name, category, unit, gst_rate, mrp, schedule, lens_type, lens_index, status from inventory.item where id = :id""")
                .param("id", itemId).query(ItemRef.class).optional();
    }

    @Override
    public Optional<StoreRef> store(UUID storeId) {
        return jdbc.sql("select id, centre_id, code, name, store_type, status from inventory.store where id = :id").param("id", storeId)
                .query(StoreRef.class).optional();
    }

    @Override
    public List<StoreRef> stores(String storeType) {
        return jdbc.sql("""
                select id, centre_id, code, name, store_type, status from inventory.store where store_type = :t and status = 'ACTIVE'
                order by created_at""").param("t", storeType).query(StoreRef.class).list();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Taken> takeFirstExpiry(Actor actor, UUID storeId, UUID itemId, int quantity, String movementType, String refType,
                                       UUID refId, String refNo) {
        List<UUID> batches = jdbc.sql("""
                select id from inventory.stock_batch
                where store_id = :s and item_id = :i and qty_on_hand > 0 and (expiry_date is null or expiry_date > :today)
                order by expiry_date nulls last, received_at for update""")
                .param("s", storeId).param("i", itemId).param("today", BusinessTime.today()).query(UUID.class).list();
        List<Taken> taken = new ArrayList<>();
        int remaining = quantity;
        for (UUID b : batches) {
            if (remaining == 0) {
                break;
            }
            BatchView batch = batchRow(b);
            int take = Math.min(remaining, batch.qtyOnHand());
            move(actor, b, -take, movementType, refType, refId, refNo, null);
            taken.add(new Taken(b, itemId, batch.batchNo(), batch.expiryDate(), take, batch.mrp()));
            remaining -= take;
        }
        if (remaining > 0) {
            String name = item(itemId).map(ItemRef::name).orElse("this item");
            throw new RuleViolationException("inventory.insufficient-stock",
                    "Not enough unexpired stock of " + name + ": " + (quantity - remaining) + " available, " + quantity + " needed");
        }
        return taken;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Taken takeBatch(Actor actor, UUID storeId, UUID batchId, int quantity, String movementType, String refType, UUID refId,
                           String refNo) {
        BatchView batch = batchRow(batchId);
        if (!batch.storeId().equals(storeId)) {
            throw new RuleViolationException("inventory.batch.other-store", "That batch is not in this store");
        }
        if (batch.expiryDate() != null && !batch.expiryDate().isAfter(BusinessTime.today())) {
            throw new RuleViolationException("inventory.batch.expired", batch.itemName() + " batch " + batch.batchNo() + " has expired");
        }
        move(actor, batchId, -quantity, movementType, refType, refId, refNo, null);
        return new Taken(batchId, batch.itemId(), batch.batchNo(), batch.expiryDate(), quantity, batch.mrp());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void putBack(Actor actor, UUID batchId, int quantity, String movementType, String refType, UUID refId, String refNo,
                        String reason) {
        move(actor, batchId, quantity, movementType, refType, refId, refNo, reason);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public String nextNumber(Actor actor, UUID centreId, String docType) {
        String period = financialYear(BusinessTime.today());
        int seq = jdbc.sql("""
                insert into inventory.doc_counter (tenant_id, centre_id, doc_type, period, last_value) values (:t, :c, :d, :p, 1)
                on conflict (centre_id, doc_type, period) do update set last_value = inventory.doc_counter.last_value + 1
                returning last_value""").param("t", actor.tenantId()).param("c", centreId).param("d", docType).param("p", period)
                .query(Integer.class).single();
        String centreCode = org.centre(centreId).orElseThrow(() -> new NotFoundException("Centre not found")).code();
        return centreCode + "/" + docType + "/" + period + "/" + String.format("%06d", seq);
    }

    // ---------------------------------------------------------------- helpers

    /** Changes a batch's quantity and writes the ledger row; never below zero. */
    private void move(Actor actor, UUID batchId, int delta, String type, String refType, UUID refId, String refNo, String reason) {
        Moved m = jdbc.sql("""
                update inventory.stock_batch set qty_on_hand = qty_on_hand + :d, version = version + 1
                where id = :id and qty_on_hand + :d >= 0 returning store_id, item_id, qty_on_hand""")
                .param("d", delta).param("id", batchId).query(Moved.class).optional()
                .orElseThrow(() -> new RuleViolationException("inventory.insufficient-stock", "Not enough stock in this batch"));
        jdbc.sql("""
                insert into inventory.stock_movement (tenant_id, store_id, item_id, batch_id, movement_type, quantity, balance_after,
                    ref_type, ref_id, ref_no, reason, moved_by)
                values (:t, :s, :i, :b, :type, :q, :bal, :rt, :rid, :rno, :reason, :by)""")
                .param("t", actor.tenantId()).param("s", m.storeId()).param("i", m.itemId()).param("b", batchId).param("type", type)
                .param("q", delta).param("bal", m.qtyOnHand()).param("rt", refType).param("rid", refId).param("rno", refNo)
                .param("reason", reason).param("by", actor.userId()).update();
    }

    private record Moved(UUID storeId, UUID itemId, int qtyOnHand) {
    }

    private BatchView batchRow(UUID batchId) {
        return jdbc.sql(BATCH_SELECT + " where b.id = :id").param("id", batchId).query(BatchView.class).optional()
                .orElseThrow(() -> new NotFoundException("Batch not found"));
    }

    private StoreView activeStore(UUID storeId) {
        StoreView s = storeView(storeId);
        if (!"ACTIVE".equals(s.status())) {
            throw new RuleViolationException("inventory.store.retired", s.name() + " is retired");
        }
        return s;
    }

    private StoreView readableStore(Actor actor, UUID storeId) {
        StoreView s = storeView(storeId);
        requireAnyAt(actor, Scope.centre(s.centreId()), Permission.INVENTORY_READ, Permission.PHARMACY_DISPENSE, Permission.OPTICAL_ORDER,
                Permission.OPTICALLAB_PROCESS);
        return s;
    }

    private void requireAnyAt(Actor actor, Scope scope, Permission... permissions) {
        for (Permission p : permissions) {
            if (access.holds(actor, p, scope)) {
                return;
            }
        }
        access.require(actor, permissions[0], scope, "inventory.read", "centre", scope.id().toString());
    }

    private void requireAnyAnywhere(Actor actor, Permission... permissions) {
        for (Permission p : permissions) {
            if (access.holds(actor, p, null) || !access.centresWhere(actor, p).isEmpty()) {
                return;
            }
        }
        access.requireAnywhere(actor, permissions[0], "inventory.item.read", "item");
    }

    private static String[] categoriesFor(String storeType) {
        return switch (storeType) {
            case "PHARMACY" -> new String[] {"DRUG", "CONSUMABLE", "CONTACT_LENS"};
            case "OPTICAL" -> new String[] {"FRAME", "SPECTACLE_LENS", "CONTACT_LENS", "OPTICAL_ACCESSORY"};
            case "OPTICAL_LAB" -> new String[] {"LENS_BLANK", "SPECTACLE_LENS"};
            case "OT" -> new String[] {"SURGICAL", "CONSUMABLE", "DRUG"};
            default -> new String[] {};
        };
    }

    static String financialYear(LocalDate d) {
        int start = d.getMonthValue() >= 4 ? d.getYear() : d.getYear() - 1;
        return start + "-" + String.format("%02d", (start + 1) % 100);
    }
}
