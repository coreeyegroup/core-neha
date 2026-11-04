# Reception desk
> Finding and registering patients, appointments, check-in with a token, the day's queue, bills, payments and checkout.
<!-- audience: opd.checkin opd.appointment.manage patient.register -->

Who uses it: receptionists and front office supervisors. Opens from **Clinic → Reception desk**.

## The top of the desk

| Card | Meaning |
|---|---|
| Appointments booked | Booked for today and not yet arrived |
| Arrived | Appointments checked in today, and the number of visits today |
| In the clinic | Patients in optometry, with the doctor, or in counselling, and the longest current wait |
| At checkout | Patients who have finished and need billing and checkout |
| Completed | Visits finished today |
| New patients | Patients registered at this centre today |

The figures refresh every 30 seconds.

Buttons: **Register patient**, **Book appointment**, **Walk-in check-in**.

## Finding a patient

Type at least two characters of the **name**, the **mobile number** (four or more digits) or the **UHID** in **Find a patient**. Choose the patient. You can then **Open record**, **Check in now** or **Book appointment**.

Always search before registering, so a patient does not get two UHIDs.

## Registering a new patient

1. Choose **Register patient**.
2. Enter **Given name** and **Sex** (required), **Family name**, **Date of birth** *or* **age in years** (the system then records an estimated date of birth), **Mobile**, address, **PIN code** (6 digits), **ABHA number** if the patient has one (format `12-3456-7890-1234`), **Guardian** for a child, and an emergency contact.
3. Choose **Register**. The patient gets a **UHID** such as `NH00000023`, valid at every centre.

If the system finds a possible match (same mobile and name, or same name and date of birth) it shows **"This may be an existing patient: …"**. Open the existing record instead. Only if you are sure it is a different person, tick **"I have checked: this is a different person"** and choose **Register as a new patient**.

## Appointments

The **Appointments** tab lists the chosen day (today by default).

**Book**: **Book appointment** → patient, date, time, visit type (*New consultation*, *Review*, *Post-operative review*), how it was booked (*Phone*, *At the desk*, *Online*, *Referral*), the doctor (or *Any available*), and a reason. A patient can have only one booked appointment per day at a centre.

**Check in** (today only): choose **Check in** on the row, confirm the doctor and the **chief complaint**, and confirm. The patient gets **today's token number** and joins the optometry queue.

**Cancel or no-show**: choose **Cancel…**, give a reason, and tick *"The patient did not come"* to record a **no-show** instead of a cancellation.

Appointment status: *Booked*, *Arrived*, *Cancelled*, *No-show*.

## Walk-in patients

Choose **Walk-in check-in** (or **Check in now** after finding the patient): patient, visit type, doctor, chief complaint. A patient cannot be checked in twice on the same day while a visit is still open.

## Today's queue

All of today's visits by token, with **Stage** and **Waiting** (minutes since the last stage change). Click a row to open the [visit record](/help/manual/the-visit-record).

## Checkout: bills, payment, sending the patient home

The **Checkout** tab lists patients whose consultation (and counselling, if surgery was advised) is finished. For each patient:

1. **Raise bill**: the dialog suggests charges from the visit type (for a new patient: new consultation and refraction). Add or remove charges from the **price list**, change quantities, and choose **Raise bill**. A bill number such as `VEC/2026-27/000042` is given.
2. **Collect ₹…**: choose the mode (*Cash*, *UPI*, *Card*, *Insurance / TPA*, *Corporate*) and the amount. Every mode except cash needs a **reference** (UPI transaction id, card approval code, authorisation number). Part payments are allowed.
3. When nothing is due, **Complete checkout**. The visit becomes *Completed*.

If the visit is free (for example a review within the free period), choose **No charge…** and write why. A visit with an unpaid balance or a discount waiting for approval cannot be checked out.

## Discounts

Open the bill (**View bill**) and choose **Request discount…** with the amount and the reason. A **front office supervisor** or the **accounts manager** decides it; you cannot decide your own request. While a discount waits, payment for that bill is paused. See [Accounts](/help/manual/accounts) for the bill page.

## Surgery package bills

When the counsellor has recorded a package, open the surgery (from the patient record or the visit) and choose **Raise package bill**. Advance or part payments can be collected; the balance shows as outstanding in accounts.
