# Permissions and roles reference
> Every permission in the system, what it allows, and the roles used at Vijaya Eye Clinic.
<!-- audience: iam.role.read iam.assignment.approve -->

The live version of this chapter is the **[Access guide](/help/access)**: it is built from the system itself, so it always shows the current roles, which positions hold them, which menus each permission opens, and which modules your organisation's licence includes. This chapter explains the permissions in words.

A permission works only if its module is in your organisation's licence (see [Licence](/licence)). Permissions marked **group-wide only** work only from an assignment that applies to the whole group.

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

## Inventory and stores

| Permission | Allows |
|---|---|
| `inventory.item.manage` | Create and change items in the chain item master (group-wide only) |
| `inventory.read` | See stores, stock by batch and expiry, ledger, receipts, transfers, adjustments |
| `inventory.store.manage` | Open and retire stores |
| `inventory.receive` | Goods receipt from supplier into a store |
| `inventory.transfer` | Send stock between stores |
| `inventory.adjust.request` | Request damage, expiry, count adjustments |
| `inventory.adjust.approve` | Approve or reject adjustments (not own) |

## Pharmacy

| Permission | Allows |
|---|---|
| `pharmacy.prescribe` | Write and cancel medicine prescriptions |
| `pharmacy.read` | See prescriptions and sales |
| `pharmacy.dispense` | Dispense against prescription or over the counter; raises the bill |
| `pharmacy.return` | Take back dispensed medicines into stock |

## Optical shop

| Permission | Allows |
|---|---|
| `optical.read` | See spectacle orders |
| `optical.order` | Take, mark ready, cancel spectacle orders |
| `optical.deliver` | Hand finished spectacles to the patient |

## Optical lab (manufacturing)

| Permission | Allows |
|---|---|
| `opticallab.read` | See manufacturing jobs |
| `opticallab.process` | Move a job through surfacing, coating, edging, fitting |
| `opticallab.qc` | QC pass/fail and dispatch (not own fitting) |

## Laboratory

| Permission | Allows |
|---|---|
| `lab.catalogue.manage` | Add tests, parameters, reference and critical ranges (group-wide only) |
| `lab.order` | Order lab tests; cancel own orders |
| `lab.read` | See lab orders and released results |
| `lab.collect` | Collect and label samples |
| `lab.process` | Receive or reject samples, enter results |
| `lab.validate` | Validate and release results entered by someone else |

## Imaging (radiology)

| Permission | Allows |
|---|---|
| `radiology.catalogue.manage` | Add imaging procedures (group-wide only) |
| `radiology.order` | Order imaging; cancel own orders |
| `radiology.read` | See imaging orders, released reports, open images |
| `radiology.perform` | Schedule, acquire, link the study |
| `radiology.report` | Write imaging reports |
| `radiology.verify` | Sign and release reports |

## Roles at Vijaya Eye Clinic

All are assigned at **one centre** unless noted. Every role also includes `org.centre.read`.

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
| Eye clinic prescribing (medicines) | All doctors (extra role) | pharmacy.prescribe, pharmacy.read |
| Eye clinic diagnostics ordering (lab and imaging) | All doctors (extra role) | lab.order, lab.read, radiology.order, radiology.read |
| Eye clinic pharmacist | Chief pharmacist, Pharmacist | patient.read, pharmacy.read, pharmacy.dispense, pharmacy.return, inventory.read, billing.read, billing.collect |
| Eye clinic pharmacy assistant | Pharmacy assistant | patient.read, pharmacy.read, pharmacy.dispense, inventory.read |
| Eye clinic optical shop | Optical store in-charge, Optical sales executive | patient.read, eye.clinical.read, optical.read, optical.order, optical.deliver, inventory.read, billing.read, billing.collect |
| Optical lab technician | Surfacing technician, Edging and fitting technician | opticallab.read, opticallab.process, inventory.read |
| Optical lab quality check and dispatch | Optical lab supervisor, Optical quality inspector | opticallab.read, opticallab.qc, inventory.read |
| Eye clinic store keeper | Store keeper | inventory.read, inventory.receive, inventory.transfer, inventory.adjust.request |
| Eye clinic stores manager | Stores manager | inventory.read, inventory.store.manage, inventory.adjust.approve, audit.read |
| Item master (drugs, frames, lenses) for the whole organisation | Stores manager (extra role, whole group) | inventory.item.manage |
| Eye clinic pathologist | Consultant pathologist | patient.read, lab.read, lab.process, lab.validate |
| Eye clinic laboratory technician | Laboratory technician | patient.read, lab.read, lab.collect, lab.process, inventory.read |
| Eye clinic phlebotomist | Phlebotomist (sample collection) | patient.read, lab.read, lab.collect |
| Eye clinic radiologist | Consultant radiologist | patient.read, radiology.read, radiology.report, radiology.verify |
| Eye clinic radiographer | Radiographer (X-ray, CT, MRI, ultrasound) | patient.read, radiology.read, radiology.perform |
| Laboratory and imaging catalogue for the whole organisation | Pathologist, radiologist (extra role, whole group) | lab.catalogue.manage, radiology.catalogue.manage |

Use these as the template for every new eye clinic; for a centre of another kind, copy the closest role with **Clone role** and adjust.
