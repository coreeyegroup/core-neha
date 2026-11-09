/**
 * Pharmacy module (PHA-01..PHA-08): prescriptions written by doctors, dispensing against a
 * prescription (always for Schedule H, H1 and X drugs) or over the counter, first-expiry-first-out
 * batch selection that never dispenses expired stock, the bill raised in billing, and returns into
 * stock. Stock moves only through the inventory module's ledger.
 */
package in.coreeye.coreneha.pharmacy;
