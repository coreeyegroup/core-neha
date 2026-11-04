# Frontend guide
> The staff application: structure, session and permissions, the generated client, building a screen, design rules, and editing this help.
<!-- audience: administrator -->

## Structure

```
src/main.tsx              theme, React Query client, notifier, <App/>
src/App.tsx               routes; loads /v1/me first; 401 → sign-in page
src/auth/session.tsx      Session: me, working centre, hasAnywhere / hasAtCentre / hasTenantWide
src/api/http.ts           apiFetch: base path, cookies, CSRF header, problem → ApiError
src/api/generated/        Orval client (one function per operation, one type per schema)
src/api/useAction.ts      mutation + invalidate queries + success message
src/layout/               AppShell (top bar, working centre, help button, navigation), navigation.ts
src/components/           DataTable, FormDialog, ProblemAlert, StatusChip, PageHeader, Section/KeyValue, Notifier
src/pages/                administration screens
src/pages/clinic/         desks, visit chart, bill, surgery, patients, shared dialogs and helpers
src/help/                 help hook (GET /v1/help), search and Markdown renderer
src/theme/                design tokens and MUI theme
```

## Session and permissions in the UI

`useSession()` gives `me` (from `/v1/me`: tenant permissions, per-centre permissions, postings) and the **working centre**. Use:

- `hasAnywhere(p)` for navigation visibility,
- `hasAtCentre(p, centreId)` for buttons on a centre's data,
- `hasTenantWide(p)` for group-wide actions.

The UI only hides what the user cannot do; **the server checks every action**. Never rely on the UI for security.

## Data fetching

```tsx
const visits = useQuery({ queryKey: ['visits', centreId, stage], queryFn: () => api.visits(centreId!, { stage }), enabled: !!centreId });
const save = useAction(() => api.saveOptometry(visitId, body), { invalidate: [['visit', visitId], ['visits']], success: 'Optometry saved' });
```

Query keys start with the resource name so `invalidate: [['visits']]` refreshes every list of visits. Show errors with `<ProblemAlert error={…} />`; it displays the rule code and request id.

## Building a screen

1. Add the page under `src/pages/…` using `PageHeader`, `Section`, `DataTable`, `FormDialog`.
2. Add a route in `App.tsx` and, if it is a top-level screen, a `NAV_ITEMS` entry with the permission(s) that make it visible.
3. Call only the generated client. If the endpoint does not exist, change the backend and the contract first.
4. Keep forms as plain controlled state; send `undefined` for empty optional fields; send `expectedVersion` from the loaded record.
5. Add a browser test in `e2e/` with a mock route in `e2e/clinic-mock.mjs` or `e2e/mock-server.mjs`.
6. Map the route to its manual chapter in `chapterForPath` (`src/help/content.ts`) and write or update the chapter.

## Design rules

- Colours, sizes and spacing come from `src/theme/tokens.ts`; no raw colours in components.
- Status is shown with text, never colour alone (`StatusChip`, `StageChip`).
- Tables are sortable and filterable (`DataTable`); rows that open something are keyboard accessible.
- Dates on screen are IST (`clock`, `dateLabel`, `todayIst` in `pages/clinic/common.tsx`); money is formatted `₹` en-IN.
- Every dialog has one clear submit action and shows server refusals inside the dialog.
- The layout must work on a phone: navigation becomes a drawer; grids collapse to one column.

## Editing this help

Chapters live in the backend: `backend/src/main/resources/help/manual/` (operation manual) and `help/developer/` (this guide), named `NN-slug.md`:

```
# Chapter title
> One-line summary shown on the help home page.
<!-- audience: opd.checkin opd.appointment.manage -->

## Section            (becomes "On this page" and a search result)
Text, **bold**, *italic*, `code`, [internal link](/help/manual/accounts), tables, lists, ```code```,
> **Note** / **Tip** / **Warning** call-outs.
```

The audience decides who receives the chapter: `everyone`, `administrator`, or permission codes. A chapter without an audience line goes to administrators only. Raw HTML is not rendered. Update the chapter in the same change as the screen, and give it the audience of the people who use that screen.

## The public website

`frontend/website/` holds static pages (`index.html`, `treatments.html`, `visit.html`, `contact.html`, `404.html`), `assets/site.css`, `assets/site.js` and **`assets/site-config.js`**, the one file with the clinic's phone, WhatsApp, email, address, timings, map and booking links (empty values are hidden). The gateway image copies the folder to `<base>/` and the staff app to `<base>/staff/`. The pages use only same-origin files (strict CSP), self-hosted IBM Plex Sans, no tracking and no cookies. The pages were generated once from a template; header and footer are repeated in each page, so change them in every page.

## Commands

```bash
npm ci
npm run generate:api      # after contracts/openapi.json changes
npm run lint && npm run typecheck && npm test
npm run build
npx playwright test       # browser tests against e2e/mock-server.mjs (personas via the mock_persona cookie)
node e2e/mock-server.mjs 4173 --persona=priya.reception   # website at http://localhost:4173/core-neha/, staff app at /core-neha/staff/
```
