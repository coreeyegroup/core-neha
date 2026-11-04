# Patient database
> Searching patients across all centres, the patient record, and correcting details.

Opens from **Clinic → Patient database**. Needs `patient.read`.

## Searching

Type a **name** (start of given or family name), a **mobile number** (four or more digits) or a **UHID**. Without a search the list shows the most recently registered patients. Columns: UHID, name, age and sex, mobile, area, ABHA, centre of registration, registration date.

A patient has **one UHID for the whole group**, so a patient registered at one centre is found at every centre.

## The patient record

| Part | Contents | Who sees it |
|---|---|---|
| Details | UHID, contacts, address, ABHA, guardian, emergency contact, where and when registered | Everyone with patient access |
| Upcoming appointments | Date, time, type, doctor | Clinic staff |
| Visits | Date, centre, type, doctor, stage; for clinical staff also diagnosis, best-corrected vision and eye pressure | Clinic staff |
| Surgeries | Procedure, eye, date, surgeon, status | Clinical staff |
| Bills | Bill number, date, net, balance, status | Billing staff |

Buttons: **Edit details**, **Book appointment**, **Check in now** (depending on permissions).

Opening a record is written to the audit trail with your name and the time.

## Correcting details

**Edit details** changes name, sex, date of birth, contacts, address, ABHA, guardian and emergency contact. The UHID never changes. An estimated date of birth stays estimated until you enter the real date. Every correction is audited with the previous name and mobile.

## Duplicates

If one person has two UHIDs, do not register a third: note both UHIDs and report it to the administrator. Merging records is planned for a later release.
