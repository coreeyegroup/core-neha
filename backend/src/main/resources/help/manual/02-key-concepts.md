# Key concepts
> The ideas the whole system is built on. Read this once before setting anything up.
<!-- audience: everyone -->

## The organisation

| Concept | Meaning | Example |
|---|---|---|
| **Group (tenant)** | The whole hospital group. Everything belongs to one group. | Neha Health Group |
| **Hierarchy levels** | The layers the group uses to organise centres. The group chooses them; they are not fixed. | State (rank 1), City (rank 2), Area (rank 3) |
| **Hierarchy nodes** | The actual places in those levels. Each node has a parent. | Karnataka → Bengaluru |
| **Legal entity** | The registered company that runs a centre (for invoices and registrations). | Neha Eye and Health Care Pvt Ltd |
| **Centre** | A hospital, clinic, store or laboratory. It sits under one node and belongs to one legal entity. | Vijay Eye Clinic (VEC) |
| **Department library** | The list of departments the group knows about, defined once. | Reception, Optometry, Ophthalmology, Billing… |
| **Department at a centre** | A library department *enabled* at a centre, optionally with a local name. A centre runs only the departments enabled for it. | "Ophthalmology" called "Consultation (eye specialists)" at VEC |

## People and jobs

These five things are **separate on purpose**. Most mistakes come from mixing them up.

| Concept | What it is | Gives system access? |
|---|---|---|
| **Position** | A job defined once for the group (title, category, grade). | No |
| **Establishment** | A position created at a centre, in a department, with an approved headcount. | No |
| **Person** | A staff member's identity (name, work email). | No |
| **Posting** | A person holding an established position, with a start date, an optional end date and an FTE (for example 0.6 for three days a week). One person can have several postings, at several centres. | No |
| **User account** | The login of a person (username, password, authenticator). | Only together with an assignment |
| **Role** | A named set of permissions, for example "Eye clinic front office". | Only through an assignment |
| **Assignment** | "This user has this role at this scope", approved by someone else. | **Yes. This is the only thing that gives access.** |
| **Responsibility** | Accountability, for example "Fire safety warden" or "Stock controller". | No, never |

> **Important** A posting says where someone works. An assignment says what they may do in the system. Creating a posting does not give access, and ending a posting does not remove access by itself; revoke the assignment too.

## Scope: where access applies

Every assignment has a scope:

- **One centre**: the user may act only at that centre.
- **A hierarchy node and everything below it**: for example all centres in Bengaluru.
- **The whole group**.

A visiting consultant who works at two centres gets two assignments, one per centre.

## The two-person rule

Nothing that expands access, or moves money outside the normal path, is done by one person alone:

- A **role version** is drafted by one person and approved by another.
- An **access request** is made by one person and approved by another; nobody can approve their own request or a request that gives *them* access.
- A **discount** is requested at the desk and decided by a supervisor or the accounts manager, never by the requester.
- A **day close** is submitted by the accountant and approved by the accounts manager.

The system enforces this; there is no override.

## Patients and visits

| Concept | Meaning |
|---|---|
| **Patient** | One record per person for the whole group, identified by the **UHID** (for example `NH00000012`). The same UHID is used at every centre. |
| **Appointment** | A booked date and time at a centre, optionally with a chosen doctor. |
| **Visit** | One attendance at a centre on a day, with a **token number**. Every department works on the same visit. |
| **Stage** | Where the visit is now: *Optometry → With doctor → Counselling (only if surgery is advised) → Checkout → Completed* (or *Cancelled*). |
| **Surgery case** | An operation from the doctor's advice to the operative note: *Advised → Accepted (or Declined) → Scheduled → Ready for theatre → Completed*. |
| **Bill** | Charges from the centre's **price list**, with payments. *Open* until fully paid, then *Paid*. |

## Clinical privacy

Front office and accounts see a visit's **stage**, waiting time and bills, but not the findings or diagnosis. Clinical staff (optometry, doctors, counselling, theatre) see the clinical record. Every opening of a clinical record is written to the audit trail.
