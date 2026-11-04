# Troubleshooting and FAQ
> What common messages mean and what to do about them.

Every refusal shows a title (*Not permitted*, *Not allowed by a rule*, *Conflict*), a sentence, a **code** and a **request id**. Quote the code and request id when you ask for help.

## Common messages

| Code | Meaning | What to do |
|---|---|---|
| `access.denied` | You lack the permission at this place | Check the **Working centre**; ask an administrator to request the right role |
| `session.ended` | Your session ended, or your account or access was changed | Sign in again; if it repeats, ask the administrator |
| `iam.assignment.self` | You tried to request or approve access for yourself | Another person must do it |
| `patient.possible-duplicate` | A similar patient exists | Open the existing record; register only after checking |
| `eyecare.visit.already-open` | The patient is already checked in today | Find the visit in today's queue |
| `eyecare.appointment.not-today` | Only today's appointments can be checked in | Book a new appointment for today, or check in as walk-in |
| `eyecare.appointment.duplicate` | The patient already has an appointment that day | Change the existing one |
| `eyecare.optometry.incomplete` | Vision not recorded | Enter vision for the eyes, then complete |
| `eyecare.consultation.diagnosis` | No diagnosis | Enter the diagnosis, then complete |
| `eyecare.checkout.balance` | Money is still due | Collect the balance first |
| `eyecare.checkout.no-bill` | No bill and no reason | Raise the bill, or use **No charge…** with a reason |
| `eyecare.checkout.discount-pending` | A discount waits | Ask the supervisor or accounts manager to decide it |
| `eyecare.surgery.package-required` | Accepting needs a package | Choose the package |
| `eyecare.surgery.slot-taken` | Surgeon already booked at that time | Choose another time |
| `eyecare.surgery.checklist` | Consent, eye marking or fitness missing | Complete those three, then mark ready |
| `eyecare.surgery.not-ready` | Checklist not complete | The OT nurse completes it first |
| `eyecare.clinician.not-posted` | The chosen doctor does not work at this centre | Choose a doctor of this centre |
| `billing.discount-pending` | No payment while a discount waits | Get the discount decided |
| `billing.discount.self-approval` | You requested this discount | Another approver decides it |
| `billing.payment.reference` | Non-cash payment without a reference | Enter the UPI id, card approval code or authorisation |
| `billing.payment.too-large` | More than the balance | Enter the balance or less |
| `billing.bill.closed` | The bill is paid or cancelled | Raise a new bill |
| `billing.tariff.unavailable` | Item not on this centre's active price list | Ask accounts to add or re-activate it |
| `billing.dayclose.variance-note` | Cash differs and no note | Explain the difference |
| `billing.dayclose.exists` | Day already submitted | Wait for the decision; after a rejection submit again |
| `…changed` | Someone else saved first | Reload the page and enter again |

## Frequently asked questions

**I cannot see a desk that a colleague sees.** Your role is different, or you are on another working centre. Ask your administrator to check **Effective access**.

**A patient has two UHIDs.** Report both UHIDs to the administrator. Do not create a third.

**I made a mistake in a completed consultation.** Completed consultations cannot be edited yet. Record the correction in the next visit and inform the head of department.

**The page looks out of date.** Desks refresh automatically every 20 to 30 seconds; press the browser's reload to refresh now.

**I lost my phone with the authenticator.** Tell your administrator immediately; your account can be disabled and the authenticator reset.

**Where do I report a bug?** Note the time, the screen, what you did, and the code and request id from the message, and send it to the IT team.
