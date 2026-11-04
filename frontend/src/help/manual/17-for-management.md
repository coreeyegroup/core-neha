# For management
> What management should know and check: ownership, regular reviews, daily and monthly controls, and handing over.

## Who owns what

| Area | Typical owner | Uses |
|---|---|---|
| Organisation structure, centres, departments | Group operations / IT administrator | Organisation screens |
| Positions and postings | HR | People screens |
| Roles and access | Two named system administrators | Access screens |
| Price lists | Accounts manager of each centre | Accounts → Price list |
| Discounts | Front office supervisor, accounts manager | Accounts → Discounts |
| Day close | Accountant submits, accounts manager approves | Accounts → Day close |
| Clinical records | Medical director / head of department | Clinic desks |
| Audit and privacy | Quality or compliance manager | Audit trail |

## Daily controls

- Every centre's day close is submitted and approved; differences are explained.
- Discounts waiting are decided the same day.
- Nobody is left at **Checkout** at closing time (the reception card should be 0).

## Weekly and monthly controls

- **Outstanding**: review open bills and part-paid surgery packages.
- **Discounts**: review approved discounts by approver and reason (audit action `billing.discount.approve`).
- **Price changes**: review `billing.tariff.update` in the audit trail.
- **Access review** (quarterly): for each centre, open each user's **Effective access**; revoke what is not needed; disable accounts of leavers.
- **Denied actions**: audit outcome *Denied* shows people trying things outside their role; it may mean missing access or misuse.
- **Backups**: ask IT for the latest backup and restore-test result every month.

## When management or staff change

- New administrators: create their accounts, give them the administrator role, have the existing administrators approve; then remove the old ones. Always keep at least two.
- New department heads: give the right role at the right centre; review their team's access.
- Train new staff with this manual and the demonstration system before giving them access to the live system.

## Where the system is going

Planned next: insurance pre-authorisation and claims, pharmacy and optical orders, refunds and credit notes, GST invoices, ABHA linking, patient portal and messaging, reports by city and state, and addenda to completed clinical notes. The developer guide's *Decisions and roadmap* chapter keeps the current list.
