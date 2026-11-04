# Setting up a new centre (step by step)
> Complete checklists for the common administration jobs: open a centre, add an employee, a visiting consultant, a leaver, a role change.
<!-- audience: org.structure.manage org.centre.create iam.assignment.request -->

These recipes use two administrators, called **A** (requests) and **B** (approves), because every access change needs two people.

## Recipe 1: open a new eye clinic

1. **Location**: in **Hierarchy and legal entities**, make sure the city (and area, if you use areas) exists as a node. Add it if not.
2. **Legal entity**: make sure the company that runs the clinic exists.
3. **Departments**: in **Department library**, check the clinic's departments exist (for example Reception, Optometry, Ophthalmology, Counselling, Surgery and day-care, Billing and accounts).
4. **Centre**: in **Centres → Create centre** enter location, legal entity, code, name and type; tick the departments.
5. **Local names**: if staff call a department something else, leave it unticked when creating the centre and add it on the centre's **Departments** tab with **Enable department** and a **Local name** (for example "Operation theatre (OT)").
6. **Positions**: in **Position library**, make sure the jobs exist. Then on the centre's **Positions established** tab, establish each position in its department with its headcount.
7. **Roles**: make sure suitable roles exist (see [Permissions and roles reference](/help/manual/permissions-and-roles-reference) for the eye clinic set). A drafts and B approves any new role.
8. **Price list**: the accounts manager of the centre (or an administrator given that role) fills **Accounts → Price list** before the first patient. Bills can only use items on the centre's own price list.
9. **Staff**: for each employee follow Recipe 2.
10. **Check**: sign in as one person of each department (or use **Explain a decision**) and confirm each sees only their desk.

## Recipe 2: a new employee

1. **People and postings → Add person** (name, work email).
2. On the person: **Add posting** at the centre and position, FTE, start date.
3. **Create user account**; hand over the one-time password privately.
4. On the user: **Request access…** → Kind *Access*, the role for their job, *One centre*, the centre, a reason ("New receptionist from 1 Dec").
5. B approves in **Approvals**.
6. The employee signs in, sets a password and the authenticator.

## Recipe 3: a visiting consultant at two centres

1. One person, **two postings** (one per centre) with their FTE, for example 0.6 and 0.4.
2. One user account.
3. **Two access requests**, one per centre, with the consultant role. B approves both.

The consultant switches centres with **Working centre** in the top bar.

## Recipe 4: a centre manager who arranges access for their own staff

1. Give the manager their own access at the centre (Recipe 2).
2. Also request **Kind: Authority to request this role for others** for each role they may hand out (for example front office), scoped to their centre. B approves.
3. The manager can now **request** those roles for staff at that centre. Someone else still approves each request.

## Recipe 5: someone leaves or moves

- **Leaves**: **Disable account…** first (immediate), then **End…** their postings with the last day, and **Revoke…** their assignments.
- **Moves to another centre**: end the old posting, add the new posting, revoke the old centre's assignment, request the new one.
- **Long leave**: disable the account; enable it on return.

## Recipe 6: change what a job can do

1. Open the role and choose **Draft new version**.
2. Tick or untick permissions and explain the change.
3. **Submit for approval**. B reviews the **impact** (how many users change) and approves.

Never create a personal role for one person; change the job's role or create a new role for a new kind of job.

## Recipe 7: close a department at a centre

End the postings and positions in that department, then **Retire…** the department with a date. The system blocks retirement while work is still open there.
