# Inventory and stores
> The item master, stores at each centre, stock by batch and expiry, goods receipts, transfers, adjustments approved by a second person, and the stock ledger.
<!-- audience: inventory.read inventory.receive inventory.transfer inventory.adjust.request inventory.adjust.approve inventory.store.manage inventory.item.manage -->

Who uses it: store keepers, the stores manager, and anyone who needs to see stock (pharmacists, optical staff, the lab). Opens from **Pharmacy and optical → Inventory and stores**.

## Item master (the whole organisation)

One list of everything the organisation stocks, kept by people with the item master role: medicines (with generic name, form and **drug schedule**), consumables, frames, spectacle lenses (type, index, coating), lens blanks and contact lenses. Each item has an MRP (including GST), a GST rate and a reorder level. Retire an item instead of deleting it.

## Stores

Each centre has its own stores: Pharmacy, Optical shop, Optical lab, OT store, Main store. The stores manager opens new ones (**New store**). Choose a store at the top of the page to see its stock.

## Stock

- **Stock**: each item's quantity, number of batches, nearest expiry, reorder level (highlighted when at or below it) and value at cost.
- **Batches**: every batch with expiry, MRP and cost.
- **Expiry**: batches at the centre that expire within 90 days, or have expired. Expired stock is never dispensed or used.
- **Stock ledger**: every movement (receipt, sale, return, transfer, adjustment, consumption) with the balance after it and who made it. The ledger cannot be edited, so stock on hand can always be explained.

## Receiving goods

**Receive goods**: the supplier, their invoice number and date, then each line: item, batch, expiry (required for medicines), quantity, unit cost and MRP. The same supplier invoice cannot be received twice into one store, and expired goods are refused. The receipt gets a number such as `VEC/GRN/2026-27/000003`.

## Transfers

**Send to another store** moves a batch quantity from this store to another (for example from the Main store to the Pharmacy).

## Adjustments (two people)

Damaged, expired, lost or miscounted stock is corrected by an adjustment:

1. The store keeper uses **Request adjustment**: batch, change in quantity, reason and what happened.
2. Stock does not change yet. The stores manager sees it under **Adjustments** and approves or rejects.
3. The person who requested cannot approve their own adjustment.
