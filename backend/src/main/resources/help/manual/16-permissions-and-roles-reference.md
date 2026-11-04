# Permissions and roles reference
> Every permission in the system, what it allows, and the roles used at Vijaya Eye Clinic.
<!-- audience: iam.role.read iam.assignment.approve -->

Permissions marked **group-wide only** work only from an assignment that applies to the whole group.

## Organisation

| Permission | Allows |
|---|---|
| `org.structure.read` | View hierarchy, legal entities and the department library |
| `org.structure.manage` | Define hierarchy levels, nodes, legal entities and departments (group-wide only) |
| `org.centre.create` | Create a centre under a hierarchy node |
| `org.centre.read` | View centres and their configuration |
| `org.centre.configure` | Enable or retire departments at a centre |

## People

| Permission | Allows |
|---|---|
| `workforce.position.define` | Define positions in the group library (group-wide only) |
| `workforce.read` | View positions, establishments, people and postings |
| `workforce.establish` | Establish positions at centres and departments |
| `workforce.person.manage` | Create people and manage their postings |

## Access and audit

| Permission | Allows |
|---|---|
| `iam.role.read` | View roles, versions and the permission catalogue (group-wide only) |
| `iam.role.draft` | Draft and submit role versions (group-wide only) |
| `iam.role.approve` | Approve or reject role versions (group-wide only) |
| `iam.user.manage` | Create, disable and enable user accounts |
| `iam.assignment.read` | View assignments, responsibilities and effective access |
| `iam.assignment.request` | Request role assignments (within assignment authority) |
| `iam.assignment.approve` | Approve or reject requested assignments |
| `iam.assignment.revoke` | Revoke active assignments |
| `iam.responsibility.manage` | Define and assign responsibilities |
| `audit.read` | Search the audit trail |

## Patients and the clinic

| Permission | Allows |
|---|---|
| `patient.read` | Search and view patient demographics |
| `patient.register` | Register patients and correct their details |
| `opd.queue.read` | View appointments, the visit queue and the clinic board |
| `opd.appointment.manage` | Book and cancel appointments |
| `opd.checkin` | Check in, start walk-in visits, complete checkout, cancel visits |
| `eye.clinical.read` | View optometry, consultation and surgery records |
| `eye.optometry.record` | Record vision, refraction and eye pressure |
| `eye.consultation.record` | Record consultation, diagnosis, treatment and surgery advice |
| `eye.counselling.record` | Record surgery counselling and the patient's decision |
| `eye.surgery.schedule` | Schedule and cancel surgeries |
| `eye.surgery.prepare` | Complete the pre-operative checklist |
| `eye.surgery.operate` | Record the operation and complete the surgery |

## Billing and accounts

| Permission | Allows |
|---|---|
| `billing.read` | View bills and payments |
| `billing.charge` | Raise bills, add charges, request discounts |
| `billing.collect` | Collect payments |
| `billing.discount.approve` | Decide discounts requested by others; cancel unpaid bills |
| `billing.tariff.manage` | Maintain the centre price list |
| `billing.report.read` | View collections and outstanding reports |
| `billing.dayclose.submit` | Submit the daily cash close |
| `billing.dayclose.approve` | Approve or reject a daily cash close |

## Roles at Vijaya Eye Clinic

All are assigned at **one centre**. Every role also includes `org.centre.read`.

| Role | Positions | Main permissions |
|---|---|---|
| Eye clinic centre director (sees all centre data) | Centre director | patient read, queue, clinical read, billing read, reports, discount and day close approval, staff and access read, audit |
| Eye clinic front office | Receptionist, Patient registration executive | patient read/register, queue, appointments, check-in, billing read/charge/collect |
| Eye clinic front office supervisor | Front office supervisor | front office + discount approval + reports |
| Eye clinic appointments and call centre | Appointment and call-centre executive | patient read/register, queue, appointments |
| Eye clinic patient relations | Patient relations executive | patient read, queue, appointments |
| Eye clinic centre operations manager | Centre operations manager | patient read, queue, appointments, billing read, reports, audit (no clinical records) |
| Eye clinic optometry | Optometrist, Senior and Chief optometrist, Ophthalmic assistant, Imaging technician | patient read, queue, clinical read, optometry |
| Eye clinic optical dispensing | Dispensing optician | patient read, queue, clinical read, billing read/charge |
| Eye clinic medical director | Medical director | consultant + surgery scheduling, reports, audit |
| Eye clinic consultant | Consultant, Associate, Retina, Glaucoma, Cornea, Paediatric ophthalmologists, Resident medical officer | patient read, queue, clinical read, optometry, consultation |
| Eye clinic OPD nurse | OPD nurse | patient read, queue, clinical read, optometry |
| Eye clinic surgery counselling | Surgery counsellor, Patient care coordinator, Senior counsellor, Surgery scheduler | patient read, queue, appointments, clinical read, counselling, surgery scheduling, billing read |
| Eye clinic insurance (Mediclaim) desk | Insurance (Mediclaim) coordinator | patient read, queue, clinical read, billing read/charge |
| Eye clinic surgeon (operation theatre) | Ophthalmic surgeon | patient read, queue, clinical read, pre-op, operate |
| Eye clinic anaesthesia | Anaesthesiologist | patient read, queue, clinical read, pre-op |
| Eye clinic OT nurse | OT nursing in-charge, OT nurse, Scrub nurse, OT technician, Day-care ward nurse | patient read, queue, clinical read, pre-op |
| Eye clinic OT support (no patient records) | CSSD technician | centre read only |
| Eye clinic accountant | Accountant, Accounts assistant | billing read/collect, reports, day close submit |
| Eye clinic accounts manager | Accounts manager | billing read, reports, day close approve, discount approval, price list, audit |
| Eye clinic billing | Billing executive | patient read, queue, billing read/charge/collect |
| Eye clinic cashier | Cashier | patient read, billing read/collect |
| Eye clinic internal audit (read only) | Internal auditor | billing read, reports, audit |

Use these as the template for every new eye clinic; for a centre of another kind, copy the closest role with **Clone role** and adjust.
