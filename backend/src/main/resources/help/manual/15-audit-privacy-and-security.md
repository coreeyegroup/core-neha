# Audit, privacy and security
> The audit trail, what is recorded, how patient privacy is protected, and good practice for every user.
<!-- audience: everyone -->

## The audit trail

Opens from **Access → Audit trail**. Needs `audit.read`.

Every change, approval, refusal, sign-in and sign-out, and every opening of a patient or clinical record, is recorded: **when**, **who**, **what action**, **on which object**, **where** (centre, node or group), the **outcome** (*Success*, *Denied*, *Failed*), the reason, the request id, and details such as old and new values.

Filter by **Action (exact)** (for example `billing.discount.approve`), **Object type (exact)** (for example `bill`, `patient`, `role_assignment`) and **Outcome**. **Load older events** pages back in time.

Audit records **cannot be edited or deleted** by anyone using the system.

Useful searches:

| Question | Filter |
|---|---|
| Who opened this patient's record? | Object type `patient`, then find the patient's id |
| Who approved discounts this week? | Action `billing.discount.approve` |
| Who changed prices? | Action `billing.tariff.update` |
| Who tried something they were not allowed to? | Outcome *Denied* |
| Who gave this person access? | Object type `role_assignment` |

## How patient privacy is protected

- Access is given **per centre**, and only through approved roles.
- Front office and accounts see the visit's stage and bills, **not** findings or diagnoses.
- Administrators do **not** see patients or money unless given a clinic role.
- Every opening of a patient or clinical record is audited.
- Data of different groups is separated inside the database itself.
- Sessions end after 30 minutes of inactivity and after 12 hours.
- Sign-in needs a password **and** an authenticator code, unless the main administrator has exempted an account (audited, with a reason).
- The help centre shows each person only the chapters for their own work.

## Rules for every user

1. Never share your account or your authenticator. Every action is recorded under your name.
2. Lock or sign out of shared computers.
3. Open a patient's record only when you need it for their care or your job.
4. Never enter real patients on the demonstration system.
5. Report a lost phone at once so your authenticator can be reset.
6. Report anything that looks wrong (a patient you did not expect, access you should not have) to the administrator.

## Rules for administrators

- Give the smallest role that does the job, at the smallest scope.
- Use end dates for temporary staff and cover.
- Disable accounts on the day someone leaves.
- Review each centre's users and their effective access every quarter.
- Keep at least two active administrators at all times, so every change can be approved.
