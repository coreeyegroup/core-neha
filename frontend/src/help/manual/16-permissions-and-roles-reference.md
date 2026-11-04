# Permissions and roles reference
> Every permission in the system, what it allows, and the roles used at Vijay Eye Clinic.

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

## Roles at Vijay Eye Clinic

All are assigned at **one centre**. Every role also includes `org.centre.read`.

| Role | Positions | Main permissions |
|---|---|---|
| Eye clinic front office | Receptionist | patient read/register, queue, appointments, check-in, billing read/charge/collect |
| Eye clinic front office supervisor | Front office supervisor | front office + discount approval + reports |
| Eye clinic optometry | Optometrist, Senior optometrist | patient read, queue, clinical read, optometry |
| Eye clinic consultant | Consultant and associate ophthalmologist | patient read, queue, clinical read, optometry, consultation |
| Eye clinic surgery counselling | Surgery counsellor, Patient care coordinator | patient read, queue, appointments, clinical read, counselling, surgery scheduling, billing read |
| Eye clinic surgeon (operation theatre) | Ophthalmic surgeon | patient read, queue, clinical read, pre-op, operate |
| Eye clinic OT nurse | OT nurse | patient read, queue, clinical read, pre-op |
| Eye clinic accountant | Accountant | billing read/collect, reports, day close submit |
| Eye clinic accounts manager | Accounts manager | billing read, reports, day close approve, discount approval, price list, audit |

Use these as the template for every new eye clinic; for a centre of another kind, copy the closest role with **Clone role** and adjust.
