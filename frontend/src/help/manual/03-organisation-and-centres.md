# Organisation and centres
> Hierarchy levels and nodes, legal entities, the department library, centres, and enabling or retiring departments.

Screens: **Organisation → Hierarchy and legal entities**, **Organisation → Department library**, **Organisation → Centres**.

Who can do it: defining levels, nodes, legal entities and library departments needs the group-wide permission `org.structure.manage`. Creating a centre needs `org.centre.create` at the node. Enabling or retiring departments needs `org.centre.configure` at the centre. Viewing needs `org.structure.read` or `org.centre.read`.

## Hierarchy levels

Levels are the layers of your organisation. The group decides them, for example **State → City → Area**.

1. Open **Hierarchy and legal entities**.
2. Under **Hierarchy levels** choose **Add level**.
3. Enter a **Code** in capitals (for example `CITY`), a **Name** ("City") and a **Rank**. Rank 1 is the top. Each level needs a different rank.
4. Choose **Add level**.

## Hierarchy nodes

Nodes are the actual places: Karnataka, Bengaluru, Vijayanagar.

1. Under **Hierarchy** choose **Add node**.
2. Choose the **Level**, then the **Parent** (leave it empty for a top-level node such as a state).
3. Enter a **Code** (`BLR`) and a **Name** (`Bengaluru`). Choose **Add node**.

A node must sit at a lower level (higher rank number) than its parent; the system refuses otherwise.

> **Tip** Access given at a node covers every centre below it. A city head who is given a role at "Bengaluru" sees all Bengaluru centres, including centres added later.

## Legal entities

1. Under **Legal entities** choose **Add legal entity**.
2. Enter a short **Code** and the **Registered name** exactly as registered. Choose **Add legal entity**.

## The department library

The library is the group's list of departments. Defining a department does **not** add it to any centre.

1. Open **Department library** and choose **Define department**.
2. Enter a **Code** in capitals (`OPTOMETRY`), a **Name** and a **Category**: *Clinical*, *Clinical support*, *Administrative* or *Operations*.
3. Choose **Define**.

## Creating a centre

1. Open **Centres** and choose **Create centre**.
2. Fill in:

| Field | What to enter |
|---|---|
| Location (hierarchy node) | The node the centre belongs to, usually a city or area |
| Legal entity | The company that runs it |
| Code | A short unique code, used in bill numbers (for example `VEC` gives `VEC/2026-27/000001`) |
| Name | The name patients see |
| Centre type | For example `EYE_CLINIC`, `EYE_HOSPITAL`, `DIAGNOSTIC_CENTRE`, `OPTICAL_STORE` |
| Departments to enable now | Tick the library departments this centre runs; more can be added later |

3. Choose **Create centre**. The new centre opens.

## A centre's workspace

Open a centre from **Centres**. It has tabs:

- **Departments**: what the centre runs. **Enable department** adds a library department, with an optional **Local name** (what staff at that centre call it) and an **Effective from** date. **Show retired** shows departments that were closed.
- **Positions established**: the jobs created at this centre (see [People and positions](/help/manual/people-and-positions)).
- **Details**: code, type, location, legal entity, status.

## Retiring a department

When a centre stops running a department:

1. On the **Departments** tab choose **Retire…** next to it.
2. The system first **checks active work**: open positions and current postings in that department. Retirement is **blocked** while any remain, and the dialog lists them.
3. End the postings and close the positions first, then retire with a **Retire from** date.

Retired departments stay in the history; nothing is deleted.
