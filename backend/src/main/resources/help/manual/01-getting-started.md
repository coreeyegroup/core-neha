# Getting started
> Signing in, the screen layout, the working centre, and where to find help.
<!-- audience: everyone -->

## What core-neha is

core-neha is the hospital management system of the group. One system serves every centre: hospitals, clinics, optical stores and laboratories. Each person sees only the screens and data their job needs.

The system has two kinds of work:

- **Administration**: the organisation (centres, departments), people and their positions, user accounts and access. Administrators do this.
- **Daily clinic work**: patients, appointments, the visit (optometry, consultation, counselling), surgery, bills and accounts. Clinic staff do this at their desks.

> **Note** Administrators do not see patients, clinical records or money unless they are also given a clinic role. This is deliberate.

## Signing in

1. Open the clinic website, for example `https://coreeye.in/core-neha`, and choose **Staff login** (or go straight to `https://coreeye.in/core-neha/staff/`).
2. Choose **Sign in**. The sign-in page shows **CORE-NEHA**.
3. Enter your **username** and **password**.

**The first time you sign in** you are given a one-time password by your administrator. The system then asks you to:

1. Choose your own new password.
2. Set up an **authenticator app** (for example Google Authenticator or Microsoft Authenticator) by scanning the QR code shown. From then on, each sign-in asks for the 6-digit code from the app.

> **Tip** One authenticator app can hold codes for several accounts. If you change your phone, ask your administrator to reset your authenticator before you lose the old phone.

Some accounts are set up by the main administrator to sign in **without** a one-time code (for example a shared front-desk computer). Then the authenticator step does not appear. Only the main administrator can change this.

## Staying signed in, and signing out

- You are signed out after **30 minutes without activity**, and in any case **12 hours** after you signed in. Unsaved work in an open form is lost, so save regularly.
- To sign out, open the **account menu** (the round person icon at the top right) and choose **Sign out**. Always sign out on shared computers.
- If your account is disabled or your access is removed, the next action you take signs you out with the message "Your session ended or your account has no access".

## The screen

| Area | What it does |
|---|---|
| Top bar | System name, **DEMONSTRATION** badge (only on the demonstration system), group name, **Working centre** selector, **Help** button (?), account menu |
| Left navigation | Only the screens you are allowed to use, in groups: *Workspace*, *Clinic*, *Organisation*, *People*, *Access*, *Help* |
| Main area | The screen you opened |

On a phone or small screen the navigation is hidden behind the **menu** button (three lines) at the top left.

## The working centre

If you work at more than one centre, choose the centre you are working at now from **Working centre** in the top bar. The clinic desks (reception, optometry, accounts and so on) always show the working centre. The system remembers your choice on that browser.

If a desk says "You do not have … access at the working centre", either choose the right working centre or ask for access.

## The home page (Overview)

The **Overview** shows:

- **Your positions**: for example "Receptionist · Reception · Vijaya Eye Clinic".
- **Your desks**: a card for every desk you can use. Click a card to open it.
- For administrators: **Pending approvals** and **Tenant-wide permissions**.
- **Your centres**, and any **responsibilities** assigned to you.

## Getting help

- The **?** button at the top right opens the manual chapter for the screen you are on.
- **Help → Help centre** shows *your work in the system* (your positions, roles and responsibilities) and the manual chapters for **your** work only, with search. Chapters for other departments are not shown to you.
- Every refusal shows a short **code** (for example `billing.discount.self-approval`) and a **request id**. Give both to support; they find the exact event in the audit trail. See [Troubleshooting and FAQ](/help/manual/troubleshooting-and-faq).

## The demonstration system

When the top bar shows **DEMONSTRATION**, every person, patient and centre is invented. Use it for training. **Never enter real patient or staff information there.**
