# Slice 2.1 – Eye clinic end to end (Vijay Eye Clinic)

Status: **implemented; CI green; acceptance on core-node pending**.

## What a day at the clinic looks like

| Department (local name) | Positions (one login each) | Role | Signs in to | Does |
|---|---|---|---|---|
| Reception | Receptionist (`priya.reception`), Front office supervisor (`ramesh.frontoffice`) | EYE_FRONT_OFFICE, EYE_FRONT_OFFICE_SUPERVISOR | Reception desk | Find or register patients (UHID), book appointments, check in (token), raise bills, collect payment, request discounts, checkout. The supervisor also approves discounts and sees collections. |
| Optometry | Optometrist (`sunita.optometry`), Senior optometrist (`karthik.optometry`) | EYE_OPTOMETRY | Optometry | Queue of checked-in patients; vision (unaided, glasses, near, best corrected), refraction (sphere, cylinder, axis, add) and IOP for each eye; send to the doctor. |
| Consultation (eye specialists) | Consultant ophthalmologist (`dr.vijay`), Associate ophthalmologist (`dr.meera`) | EYE_CONSULTANT | Consultation | Queue with optometry done; anterior and posterior segment, diagnosis with ICD-10, medicines, spectacles, follow-up; advise surgery (procedure, eye, urgency) which sends the patient to counselling. |
| Surgery counselling | Surgery counsellor (`lata.counselling`), Patient care coordinator (`naveen.coordinator`) | EYE_COUNSELLING | Surgery counselling | Patients advised surgery; package and cost from the price list, payment plan (self, insurance, corporate, scheme), decision; book the OT date and surgeon. |
| Operation theatre (OT) | Ophthalmic surgeon (`dr.arjun`), OT nurse (`mary.otnurse`) | EYE_SURGEON, EYE_OT_NURSE | Operation theatre | OT list by day; pre-operative checklist (consent, eye marked, fitness, biometry, IOL power, BP, sugar); the surgeon's operative note; completing books the day-1 review. |
| Accounts | Accountant (`ganesh.accounts`), Accounts manager (`shobha.accounts`) | EYE_ACCOUNTANT, EYE_ACCOUNTS_MANAGER | Accounts | Collections by mode, service and collector; outstanding bills; day close (accountant submits, manager approves); the manager also approves discounts and maintains the price list. |

Everyone with `patient.read` has the **Patient database**: search by name, mobile or
UHID; the record shows visits (with diagnosis and best-corrected vision for clinical
staff), surgeries, upcoming appointments and bills (for billing staff).

## Loading it on core-node

```bash
ops/clinic-seed.sh        # after ops/demo-seed.sh; demo environment only; runs once
```

It creates the centre, six departments, twelve positions, nine roles (drafted by
one administrator, approved by the other), twelve users with centre-scoped approved
access, the price list, 22 synthetic patients and a working day: visits at every
stage, two surgeries today (one ready, one completed), one tomorrow, one accepted and
waiting for a date, a discount waiting for approval and bills paid by cash, UPI, card
and insurance. One-time passwords go to `~/core-neha/secrets/onboarding/clinic-logins-*.txt`.

## Checked automatically (CI)

`ClinicSeedTest` builds the clinic through the services, then checks: the board counts
at every stage; twelve centre-scoped, independently approved assignments; front office
cannot see clinical findings or record optometry; accounts and administrators cannot
search patients; the requester cannot approve a discount and payment waits for the
decision; a visit with an unpaid bill cannot be checked out; surgery cannot start
before the checklist; the accountant cannot approve the day close the manager approves.
Browser tests (`e2e/clinic.spec.ts`) sign in as four of the positions against the mock API.

## Acceptance on core-node (to record)

- [ ] `ops/clinic-seed.sh` output and the credentials file name
- [ ] Each of the 12 logins sees only its department's desks
- [ ] Walk a new patient: register -> walk-in -> optometry -> consultation (advise surgery) -> counselling -> bill and pay -> checkout
- [ ] Discount requested by `priya.reception`, approved by `ramesh.frontoffice`
- [ ] Day close submitted by `ganesh.accounts`, approved by `shobha.accounts`
- [ ] OT: `mary.otnurse` completes the checklist; `dr.arjun` completes the surgery
