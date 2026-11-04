# Users, roles and access
> User accounts, roles and role versions, requesting and approving access, revoking, disabling, and checking who can do what.
<!-- audience: iam.user.manage iam.assignment.read iam.assignment.request iam.assignment.approve iam.role.read -->

Screens: **Access → Users and access**, **Access → Roles**, **Access → Approvals**, and a person's page.

## How access works, in one paragraph

A user can do something only if they hold an **active, approved assignment** of a **role** whose **published version** contains the needed **permission**, at a **scope** that covers the place (the centre, a node above it, or the whole group). Nothing else gives access: not the job title, not the posting, not being an administrator. Every change to access needs a second person.

## Creating a user account

1. Open the person's page (**People and postings → the person**) and choose **Create user account**.
2. The **Login id** is filled in with the person's **employee number** (for example `e10003`). Keep it: the employee number never changes, so the login stays the same when the person changes position, department or centre. Sign-in accepts it in capitals too (`E10003`).
3. Choose **Create account**. The system shows a **one-time password** once. Give it to the person privately (in person or by phone). It is not stored anywhere you can see again.

The new account has **no access** until an assignment is approved.

## Roles

A role is a named set of permissions, for example "Eye clinic front office". Open **Roles** to see them.

- **System administrator** is created at setup. It contains administration permissions only (organisation, people, access, audit).
- Every other role is a **custom role** made by your administrators.

### Role versions

A role changes only through versions: **Draft → Pending approval → Published**. When a new version is published, the old one becomes *Superseded* and every user holding the role gets the new permissions immediately.

### Creating a role

1. Open **Roles** and choose **Draft custom role**.
2. Enter **Code** (`EYE_FRONT_OFFICE`), **Name**, **Description**, and tick the **permissions** (see [Permissions and roles reference](/help/manual/permissions-and-roles-reference)).
3. Choose **Save draft**, then on the role page **Submit for approval**.
4. Another administrator opens **Approvals** (or the role) and chooses **Review and approve…**. The dialog shows the **impact**: which permissions are added or removed and how many users are affected. Approve with a **Decision note**, or **Reject…**.

**Clone role** copies a published role into a new draft, for example to make a "Front office supervisor" from "Front office".

To change a role later, open it and choose **Draft new version**, edit, explain **What changes and why**, and submit. The person who drafted or submitted a version can never approve it.

## Requesting access for a user

1. Open **Users and access** and the user.
2. Choose **Request access…**.
3. Fill in:

| Field | Meaning |
|---|---|
| Kind | **Access (permissions of a role)**: the user may do what the role allows. **Authority to request this role for others**: the user (usually a centre manager) may *request* this role for others; it gives no other access. |
| Role | The role to grant |
| Applies to | **One centre**, **A hierarchy node and everything below it**, or **The whole group**, then the centre or node |
| End date (optional) | For temporary cover or contract staff |
| Reason | Why, in plain words. Required; it is kept in the audit trail. |

4. Choose **Submit request**. It now waits for an approver.

## Approving requests

Open **Approvals**. You see only requests **you may decide**: never your own requests and never requests that would give *you* access. For each, choose **Approve…** or **Reject…**, write a **Decision note**, and confirm. The user gets the access on their next action.

The number on **Approvals** in the navigation shows how many are waiting for you.

## Revoking access

Open the user, find the assignment under **Assignments** and choose **Revoke…** with a reason. It stops working on the user's next action.

## Disabling and enabling an account

Choose **Disable account…** on the user's page, with a reason. This ends all of the user's sessions at once and blocks sign-in. **Enable account…** restores it. Disabling is the fastest way to stop someone who leaves; their history is kept.

## Checking who can do what

On a user's page:

- **Assignments**: every assignment with its status, scope, dates, requester and approver.
- **Effective access**: every permission the user holds, where, and through which role.
- **Explain a decision**: choose a **Permission** and a place; the system says **allowed** or **refused** and why.

Use these before approving a request and during access reviews.

## Sign-in with or without a one-time code

Every account signs in with a password **and** a 6-digit one-time code from an authenticator app, unless the main administrator turns the code off for that account.

Who can change it: only a **group-wide** user administrator (the main administrator, holding `iam.user.manage` for the whole group), never for their own account.

1. Open **Users and access** and the user. The header shows "sign-in with password and one-time code" or "with password only". The list of users has a **One-time code** column.
2. Choose **Turn off one-time code…** or **Require one-time code…**, write the **Reason**, and confirm.
   - *Turn off*: the user's authenticator is removed; the next sign-in needs only the password.
   - *Require*: the user's sessions end; at the next sign-in they scan a QR code and from then on enter the code every time.

Every change is written to the audit trail (`iam.user.mfa.exempt`, `iam.user.mfa.require`) with the reason. Turn the code off only where the risk is accepted, and review those accounts every quarter.

Users cannot remove their own authenticator: the identity provider's self-service account page is closed.

## Resetting a password or an authenticator

Password and authenticator resets are done by an identity administrator in the identity console, which is reachable only from the server (see the developer guide, *Deployment and operations*). Plan this with your IT administrator.
