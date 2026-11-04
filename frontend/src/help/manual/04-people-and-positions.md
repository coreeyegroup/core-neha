# People and positions
> The position library, establishing positions at centres, people, postings across centres, and responsibilities.

Screens: **People → Position library**, **People → People and postings**, a person's page, and a centre's **Positions established** tab.

Who can do it: defining library positions needs the group-wide `workforce.position.define`; establishing positions needs `workforce.establish` at the centre; adding people and postings needs `workforce.person.manage`; viewing needs `workforce.read`. Responsibilities need `iam.responsibility.manage`.

## The position library

A position is a job defined once for the whole group, for example *Receptionist*, *Optometrist*, *Consultant ophthalmologist*. A position is **not** a permission role.

1. Open **Position library** and choose **Define position**.
2. Enter a **Code** in capitals (`SENIOR_OPTOMETRIST`), a **Title**, a **Category** (*Clinical*, *Nursing*, *Allied health*, *Technical*, *Administrative*, *Management*, *Support*) and an optional **Grade**.
3. Choose **Define**. This does not create the job at any centre yet.

## Establishing a position at a centre

1. Open the centre, tab **Positions established**, and choose **Establish position**.
2. Choose the **Position**, the **Department at this centre** (or "Not tied to a department"), the **Approved headcount** and the date it starts.
3. Choose **Establish**.

The list shows each position's headcount and how many people currently hold it.

## Adding a person

1. Open **People and postings** and choose **Add person**.
2. Enter **Given name**, **Family name** and **Work email** (unique in the group). Choose **Add person**.

A person can exist without a login; for example contract staff who never use the system.

## Postings: where a person works

1. Open the person and choose **Add posting**.
2. Choose the **Centre** and the **Established position** there.
3. Enter the **FTE** (1.00 = full time; 0.40 = two days a week), **From** and optionally **To**.
4. Choose **Add posting**.

A visiting consultant gets one posting per centre, each with its own FTE and dates.

To end a posting (transfer, resignation), choose **End…** next to it and enter the **Last day**.

> **Important** Ending a posting does not remove system access. When someone leaves, also **disable the user account** or **revoke** their assignments (see [Users, roles and access](/help/manual/users-roles-and-access)).

## Responsibilities

Responsibilities record accountability: *Fire safety warden*, *Stock controller*, *Infection control nurse*. They **never** give system access.

1. On the person's page choose **Assign responsibility**.
2. Choose the responsibility, where it applies (a centre, a node or the whole group) and the dates.

New responsibility types are defined by an administrator with `iam.responsibility.manage`.

## Creating the person's login

From the person's page choose **Create user account**, then continue with [Users, roles and access](/help/manual/users-roles-and-access).
