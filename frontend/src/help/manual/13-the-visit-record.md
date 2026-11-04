# The visit record
> One page per visit that every department works on: progress, optometry, consultation, surgery, bills and earlier visits.

Opens by clicking a patient in any queue, or from the patient record.

## Header

**Token · patient name**, then UHID, age and sex, mobile, visit type, date and centre. Buttons: **Patient record**, and **Cancel visit…** for front office (only while the visit is open and no payment was taken).

## Progress

A step bar shows *Optometry → With doctor → (Counselling) → Checkout → Completed*. The **Visit** box shows the chief complaint, doctor, and the time of each step.

## Sections

| Section | Who edits | When |
|---|---|---|
| Optometry | Optometrists and doctors | Until the consultation is complete |
| Consultation | Doctors | Until completed |
| Surgery | Counselling and OT on the surgery page | See [Surgery counselling](/help/manual/surgery-counselling) and [Operation theatre](/help/manual/operation-theatre) |
| Bills | Front office | While the visit is open |

Others see the sections read-only. Front office and accounts do not see the clinical sections at all; the page says so.

## Earlier visits

Links to the patient's previous visits at centres you can see.

## Saving and conflicts

Each section saves on its own. If someone else saved the same section after you opened it, the system refuses with "Someone else changed this record. Reload and try again." Reload the page; your entries are not merged automatically.
