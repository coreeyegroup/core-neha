# Clinical and billing flows
> The state machines of the visit, the surgery case and the bill, and how a patient moves through the modules.
<!-- audience: administrator -->

## End-to-end sequence

```
Reception        registerPatient → bookAppointment → checkInAppointment / startWalkInVisit
                    visit.stage = OPTOMETRY, token from eyecare.daily_counter
Optometry        saveOptometry(complete=true)             → stage CONSULTATION
Consultation     saveConsultation(complete=true)
                    no surgery   → stage CHECKOUT
                    surgery      → stage COUNSELLING + surgery_case ADVISED
Counselling      counselSurgery(decision)                 → visit stage CHECKOUT
                    ACCEPTED → case ACCEPTED, DECLINED → DECLINED, UNDECIDED → stays ADVISED
                 scheduleSurgery                           → case SCHEDULED
Reception        createBill(visitRef) → collectPayment … bill PAID
                 checkoutVisit                             → stage COMPLETED
OT               saveSurgeryPreOp(ready=true)              → case READY
                 saveSurgeryOperation(complete=true)       → case COMPLETED + POST_OP appointment tomorrow 10:00
Accounts         submitDayClose → decideDayClose (another person)
```

## Visit stages

| From | Action | To | Permission | Guard |
|---|---|---|---|---|
| — | check-in / walk-in | OPTOMETRY | `opd.checkin` | patient active; no other open visit today at the centre; doctor posted at the centre |
| OPTOMETRY | optometry complete | CONSULTATION | `eye.optometry.record` | some vision recorded |
| OPTOMETRY, CONSULTATION | consultation complete | CHECKOUT or COUNSELLING | `eye.consultation.record` | diagnosis; procedure and eye if surgery advised |
| COUNSELLING | counselling recorded | CHECKOUT | `eye.counselling.record` | package required to accept |
| CHECKOUT | checkout | COMPLETED | `opd.checkin` | every non-cancelled bill has balance 0 and no pending discount; no bill ⇒ no-charge reason |
| any open | cancel | CANCELLED | `opd.checkin` | no payment taken |

Optometry can be edited in OPTOMETRY and CONSULTATION; a completed consultation is locked.

## Surgery case

```
ADVISED ──counsel ACCEPTED──▶ ACCEPTED ──schedule──▶ SCHEDULED ──pre-op ready──▶ READY ──operate complete──▶ COMPLETED
   │  ▲                           │                    ▲   │                       │
   │  └─counsel UNDECIDED         │                    └───┘ reschedule /           │
   └─counsel DECLINED─▶ DECLINED ─┘ (re-counsel)            pre-op not ready         │
any state except COMPLETED ──cancel──▶ CANCELLED
```

Database CHECKs: READY/COMPLETED need consent, site marked and fitness; COMPLETED needs anaesthesia and operative notes. Scheduling refuses a surgeon already booked at the same date and time.

## Bill

- Created OPEN with lines from the centre's ACTIVE tariff; numbered `CENTRE/FY/######` per centre and financial year (April–March).
- `recompute()`: gross and tax from lines; `net = gross + tax − discount_amount`; becomes PAID when lines exist, `paid ≥ net` and no discount is pending.
- Discount: `requestDiscount` (billing.charge) → PENDING; `decideDiscount` (billing.discount.approve, not the requester) → APPROVED (applied) or REJECTED. Payments and line changes are refused while PENDING.
- Lines can be removed only before payments and discounts. Cancel only with no payments, by an approver.
- Payments: amount ≤ balance; non-cash needs a reference; `business_date` is the IST day.

## Day close

Preview sums payments of the day by mode. Submit stores system totals, counted cash and variance (a note is required if variance ≠ 0). One non-rejected day close per centre and day (partial unique index). Approval by another person.
