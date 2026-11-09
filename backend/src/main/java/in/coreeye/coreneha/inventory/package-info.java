/**
 * Inventory module (INV-01..INV-10): the chain's item master (drugs, consumables, frames, spectacle
 * lenses, lens blanks), stores at each centre (pharmacy, optical shop, optical lab, OT, central),
 * stock by batch and expiry, goods receipts from suppliers, transfers between stores, adjustments
 * that a second person approves, and an append-only stock ledger. Pharmacy, optical shop and
 * optical lab take and return stock only through {@link in.coreeye.coreneha.inventory.StockLedger}.
 */
package in.coreeye.coreneha.inventory;
