# Accounts
> Collections, bills, outstanding balances, discount approvals, the daily cash close, and the price list.
<!-- audience: billing.report.read billing.dayclose.submit billing.dayclose.approve billing.tariff.manage billing.discount.approve -->

Who uses it: accountants and accounts managers (front office supervisors also see collections and approve discounts). Opens from **Clinic → Accounts**.

## Period and summary

Choose **From** and **To**, or **Today**, **Last 7 days**, **This month**. The cards show:

| Card | Meaning |
|---|---|
| Billed | Total of bills raised in the period (before discount), and the number of bills |
| Discounts | Approved discounts |
| Collected | Payments received in the period, any mode |
| Outstanding | Balance due on all open bills, whatever their date |
| Discounts to decide | Requests waiting for a decision |

## Tabs

- **Collections**: by payment mode, by service (consultation, investigation, surgery package…), by collector (who received the money), and day by day.
- **Bills**: every bill in the period, filter by status (*Open*, *Paid*, *Cancelled*). Click to open.
- **Outstanding**: open bills with a balance.
- **Discounts**: requests waiting. **Decide…** shows the bill amount, the discount, who asked and why; **Approve** or **Reject instead**. Your own requests show "Your request" and cannot be decided by you.
- **Day close**: see below.
- **Price list**: see below.

## The bill page

Opened from any bill number. It shows each charge (rate, GST, amount), gross, discount, **net payable**, paid and **balance**, and every payment with mode, reference, who received it and when.

Actions (depending on your permissions): **Print**, **Add charges**, **Remove** a charge (only before any payment or discount), **Request discount…**, **Decide discount…**, **Collect ₹…**, **Cancel bill…** (approvers only, and only before any payment).

Rules: charges come only from the centre's active price list; a bill becomes **Paid** automatically when the balance reaches zero; a paid bill cannot be changed (raise a new bill); no payment is taken while a discount waits.

## Day close (cash reconciliation)

Done every evening.

1. **Accountant**: on **Day close**, choose the **Business day**. The system shows cash, card, UPI and insurance/corporate received that day. Count the cash drawer, enter **Cash counted**. If it differs, the difference is shown and a **note explaining it** is required. **Submit day close**.
2. **Accounts manager**: in the **Day closes** list choose **Review…**, check the figures and **Approve** or **Reject (recount needed)**. The submitter can never approve their own day close.

Only one day close per centre and day can be pending or approved; after a rejection the day can be submitted again.

## Price list

Each centre has its own price list. Accounts managers maintain it.

- **Add price list item**: **Code** (capitals, for example `OCT_DISC`), **Service name**, **Category** (*Registration, Consultation, Investigation, Procedure, Surgery package, Optical, Pharmacy, Other*), **Price**, **GST** (0, 5, 12, 18 or 28 %).
- Click an item to change its name, price, GST or status. **Retired** items can no longer be billed; bills already raised keep their price.

Clinical services are normally GST-exempt (0 %); spectacles and optical goods usually carry GST. Confirm rates with your tax adviser.

Every change is recorded in the audit trail with the old and new price.
