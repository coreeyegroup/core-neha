import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Alert,
  Box,
  Chip,
  Link,
  Paper,
  Stack,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Tabs,
  Typography,
} from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { Link as RouterLink } from 'react-router';

import * as api from '../api/generated/core-neha';
import { useSession } from '../auth/session';
import { DataTable } from '../components/DataTable';
import { PageHeader } from '../components/PageHeader';
import { ProblemAlert } from '../components/ProblemAlert';
import { NAV_ITEMS, visibleItems, type NavItem } from '../layout/navigation';

type Guide = api.AccessGuide;
type Perm = api.PermissionInfo;

/** Licence modules: name and what they cover (CORE is always included). */
const MODULE_NAMES: Record<string, [string, string]> = {
  CORE: ['Core', 'Organisation, people, access, audit, patients and the outpatient queue; always included'],
  EYE_CARE: ['Eye care', 'Optometry, consultation, counselling, surgery'],
  BILLING: ['Billing and accounts', 'Bills, payments, discounts, day close, price list'],
  INVENTORY: ['Inventory', 'Stores, items, batches, stock, receipts, transfers, adjustments'],
  PHARMACY: ['Pharmacy', 'Prescriptions, dispensing, sales, returns'],
  OPTICAL: ['Optical shop', 'Spectacle orders, delivery'],
  OPTICAL_LAB: ['Optical manufacturing', 'Surfacing, edging, fitting, quality check'],
  LABORATORY: ['Laboratory', 'Test orders, samples, results, validation'],
  RADIOLOGY: ['Radiology and imaging', 'Imaging orders, studies, image viewer, reports'],
};

const moduleName = (code: string | undefined) => MODULE_NAMES[code ?? '']?.[0] ?? code ?? '';

const SCOPE_WORDS: Record<string, string> = { TENANT: 'whole organisation', ORG_NODE: 'region', CENTRE: 'centre' };

/** The permissions a menu needs: any one of them shows it. */
const menuNeeds = (m: NavItem): string[] => (m.permission ? [m.permission] : m.anyOf ?? []);

/** Two-person rules: who starts, who must be someone else. */
const TWO_PERSON: [string, string, string][] = [
  ['Role version', 'Drafts and submits', 'Approves; also refused if the approver holds the role and the version adds permissions'],
  ['Role assignment', 'Requests (never for themselves)', 'Approves'],
  ['Discount on a bill', 'Requests', 'Approves or rejects'],
  ['Daily cash close', 'Submits', 'Approves or rejects'],
  ['Stock adjustment', 'Requests', 'Approves or rejects'],
  ['Optical lab job', 'Fits the lenses', 'Passes quality check and dispatches'],
  ['Laboratory result', 'Enters the results', 'Validates and releases'],
];

function useGuide() {
  return useQuery({ queryKey: ['access-guide'], queryFn: () => api.accessGuide(), staleTime: 60_000 });
}

/**
 * The access guide in the help centre, built from the live system. Everyone sees what they can do,
 * where, through which role, and which menus that opens. Group-wide administrators (the developer
 * guide's audience) also see the whole model: modules, every permission, every role, every
 * position and which menus each permission opens.
 */
export function AccessGuide() {
  const q = useGuide();
  const g = q.data;
  const [tab, setTab] = useState('mine');
  const tabs: [string, string][] = g?.full
    ? [['mine', 'Your access'], ['modules', 'Modules'], ['permissions', 'Permissions'], ['roles', 'Roles'], ['positions', 'Positions'], ['menus', 'Menus'], ['rules', 'Rules']]
    : [['mine', 'Your access'], ['rules', 'How access works']];
  return (
    <>
      <Typography variant="caption" color="text.secondary">
        <Link component={RouterLink} to="/help">Help centre</Link> › Access guide
      </Typography>
      <PageHeader
        title="Access guide"
        subtitle={g?.full
          ? <>The whole access model of {g.chainName}: licence modules, permissions, roles, positions and menus, from the live system.</>
          : <>What you can do{g?.chainName ? ` at ${g.chainName}` : ''}, where, and why. Built from your current roles.</>}
      />
      <ProblemAlert error={q.error} />
      {g && (
        <>
          <Tabs value={tab} onChange={(_, v: string) => setTab(v)} variant="scrollable" sx={{ mb: 2, borderBottom: 1, borderColor: 'divider' }}>
            {tabs.map(([v, label]) => <Tab key={v} value={v} label={label} />)}
          </Tabs>
          {tab === 'mine' && <MyAccess guide={g} />}
          {tab === 'modules' && <Modules guide={g} />}
          {tab === 'permissions' && <Permissions guide={g} />}
          {tab === 'roles' && <Roles guide={g} />}
          {tab === 'positions' && <Positions guide={g} />}
          {tab === 'menus' && <Menus guide={g} />}
          {tab === 'rules' && <Rules full={!!g.full} />}
        </>
      )}
    </>
  );
}

function byModule(perms: Perm[]): [string, Perm[]][] {
  const map = new Map<string, Perm[]>();
  for (const p of perms) {
    const list = map.get(p.module ?? '') ?? [];
    list.push(p);
    map.set(p.module ?? '', list);
  }
  return [...map.entries()];
}

function MyAccess({ guide }: { guide: Guide }) {
  const { me, hasAnywhere } = useSession();
  const perms = guide.permissions ?? [];
  const held = perms.filter((p) => (p.heldAt ?? []).length > 0);
  const notHeld = perms.filter((p) => (p.heldAt ?? []).length === 0 && p.licensed);
  const menus = visibleItems(hasAnywhere).filter((m) => m.to !== '/' && m.group !== 'Help');
  const roles = (me.roles ?? []).filter((r) => r.kind === 'ACCESS');
  return (
    <Stack spacing={2}>
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(3, 1fr)' }, gap: 2 }}>
        <Paper sx={{ p: 2 }}>
          <Typography variant="body2" color="text.secondary">Your roles</Typography>
          {roles.length === 0 && <Typography variant="body2">None yet. Ask your administrator.</Typography>}
          {roles.map((r, i) => <Typography key={i} variant="body2">{r.roleName} at {r.scopeName}{r.validTo ? ` until ${r.validTo}` : ''}</Typography>)}
        </Paper>
        <Paper sx={{ p: 2 }}>
          <Typography variant="body2" color="text.secondary">What you can do</Typography>
          <Typography variant="h2">{held.length}</Typography>
          <Typography variant="body2" color="text.secondary">permissions in {byModule(held).length} area{byModule(held).length === 1 ? '' : 's'}</Typography>
        </Paper>
        <Paper sx={{ p: 2 }}>
          <Typography variant="body2" color="text.secondary" gutterBottom>Menus you can open</Typography>
          <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 0.5 }}>
            {menus.length === 0 && <Typography variant="body2">Only the overview and help.</Typography>}
            {menus.map((m) => <Chip key={m.to} size="small" label={m.label} component={RouterLink} to={m.to} clickable />)}
          </Stack>
        </Paper>
      </Box>

      <Typography variant="h3">What you can do, and where</Typography>
      {held.length === 0 && <Alert severity="info">You have no permissions yet. Your administrator gives access by assigning a role to your account.</Alert>}
      {byModule(held).map(([module, list]) => (
        <Paper key={module} variant="outlined">
          <Typography variant="subtitle2" sx={{ px: 2, pt: 1.5 }}>{moduleName(module)}</Typography>
          <Table size="small">
            <TableBody>
              {list.map((p) => (
                <TableRow key={p.code}>
                  <TableCell sx={{ width: { md: '45%' } }}>{p.description}</TableCell>
                  <TableCell>
                    <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 0.5 }}>
                      {(p.heldAt ?? []).map((h, i) => (
                        <Chip key={i} size="small" variant="outlined" label={`${h.scopeName} (${SCOPE_WORDS[h.scopeType ?? ''] ?? h.scopeType}) · ${h.role}`} />
                      ))}
                    </Stack>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Paper>
      ))}

      {notHeld.length > 0 && (
        <Accordion variant="outlined" disableGutters>
          <AccordionSummary expandIcon={<span aria-hidden>▾</span>}>
            <Typography>Not in your roles ({notHeld.length})</Typography>
          </AccordionSummary>
          <AccordionDetails>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
              These are switched on for your organisation but not given to you. If your work needs one, ask your administrator for a role that includes it.
            </Typography>
            {byModule(notHeld).map(([module, list]) => (
              <Box key={module} sx={{ mb: 1.5 }}>
                <Typography variant="subtitle2">{moduleName(module)}</Typography>
                {list.map((p) => <Typography key={p.code} variant="body2">• {p.description}</Typography>)}
              </Box>
            ))}
          </AccordionDetails>
        </Accordion>
      )}
    </Stack>
  );
}

function Modules({ guide }: { guide: Guide }) {
  const perms = guide.permissions ?? [];
  const l = guide.licence;
  return (
    <Stack spacing={2}>
      {l && (
        <Typography variant="body2" color="text.secondary">
          Licence {l.licenceNo} · {l.plan} · {l.status} · valid {l.validFrom} to {l.validTo ?? 'no end date'}
          {l.maxCentres ? ` · up to ${l.maxCentres} centres` : ''}. Set by the software provider (developer) from the server console.
        </Typography>
      )}
      <Paper variant="outlined">
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Module</TableCell><TableCell>Covers</TableCell><TableCell align="right">Permissions</TableCell>
              <TableCell>Menus it opens</TableCell><TableCell align="right">Status</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {(guide.modules ?? []).map((m) => {
              const codes = new Set(perms.filter((p) => p.module === m.code).map((p) => p.code));
              const menus = NAV_ITEMS.filter((n) => menuNeeds(n).some((c) => codes.has(c)));
              return (
                <TableRow key={m.code}>
                  <TableCell><b>{moduleName(m.code)}</b><Typography variant="caption" component="div" color="text.secondary">{m.code}</Typography></TableCell>
                  <TableCell>{MODULE_NAMES[m.code ?? '']?.[1]}</TableCell>
                  <TableCell align="right">{m.permissions}</TableCell>
                  <TableCell>{menus.map((n) => n.label).join(', ')}</TableCell>
                  <TableCell align="right"><Chip size="small" color={m.licensed ? 'success' : 'default'} variant={m.licensed ? 'filled' : 'outlined'} label={m.licensed ? 'Licensed' : 'Off'} /></TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </Paper>
      <Typography variant="body2" color="text.secondary">
        A module that is off removes its permissions from every role in the organisation, whatever the roles say. When the licence ends, every module except Core switches off.
      </Typography>
    </Stack>
  );
}

function Permissions({ guide }: { guide: Guide }) {
  return (
    <DataTable<Perm>
      rows={guide.permissions}
      rowKey={(p) => p.code ?? ''}
      filterPlaceholder="Filter: permission, module, role or words"
      columns={[
        { key: 'code', header: 'Permission', value: (p) => p.code, render: (p) => <code>{p.code}</code> },
        { key: 'module', header: 'Module', value: (p) => moduleName(p.module), render: (p) => moduleName(p.module) },
        { key: 'desc', header: 'What it allows', value: (p) => p.description, render: (p) => p.description },
        {
          key: 'flags', header: 'Notes', value: (p) => `${p.tenantOnly ? 'organisation-wide only' : ''} ${p.licensed ? '' : 'not licensed'}`,
          render: (p) => (
            <Stack direction="row" sx={{ gap: 0.5, flexWrap: 'wrap' }}>
              {p.tenantOnly && <Chip size="small" label="Organisation-wide only" />}
              {!p.licensed && <Chip size="small" variant="outlined" label="Not licensed" />}
            </Stack>
          ),
        },
        {
          key: 'menus', header: 'Opens menu', value: (p) => NAV_ITEMS.filter((n) => menuNeeds(n).includes(p.code ?? '')).map((n) => n.label).join(', '),
          render: (p) => NAV_ITEMS.filter((n) => menuNeeds(n).includes(p.code ?? '')).map((n) => n.label).join(', '),
        },
        { key: 'roles', header: 'Roles that hold it', value: (p) => (p.roles ?? []).join(' '), render: (p) => <Chips items={p.roles ?? []} empty="No role yet" /> },
      ]}
    />
  );
}

function Chips({ items, empty }: { items: string[]; empty: string }) {
  if (items.length === 0) {
    return <Typography variant="body2" color="text.secondary">{empty}</Typography>;
  }
  return (
    <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 0.5 }}>
      {items.map((i) => <Chip key={i} size="small" variant="outlined" label={i} />)}
    </Stack>
  );
}

function Roles({ guide }: { guide: Guide }) {
  const describe = useMemo(() => new Map((guide.permissions ?? []).map((p) => [p.code ?? '', p])), [guide.permissions]);
  return (
    <DataTable<api.RoleInfo>
      rows={guide.roles}
      rowKey={(r) => r.code ?? ''}
      filterPlaceholder="Filter: role, permission or position"
      columns={[
        {
          key: 'role', header: 'Role', value: (r) => `${r.code} ${r.name}`,
          render: (r) => (
            <>
              <b>{r.name}</b>
              <Typography variant="caption" component="div" color="text.secondary">
                {r.code}{r.system ? ' · system' : ''}{r.status !== 'ACTIVE' ? ` · ${r.status}` : ''}{r.publishedVersion ? ` · version ${r.publishedVersion}` : ' · no published version'}
              </Typography>
            </>
          ),
        },
        {
          key: 'perms', header: 'Permissions', value: (r) => (r.permissions ?? []).join(' '),
          render: (r) => (
            <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 0.5 }}>
              {(r.permissions ?? []).map((c) => <Chip key={c} size="small" variant="outlined" label={c} title={describe.get(c)?.description} />)}
            </Stack>
          ),
        },
        {
          key: 'menus', header: 'Menus it opens', value: (r) => menusFor(r.permissions ?? []).join(', '),
          render: (r) => menusFor(r.permissions ?? []).join(', '),
        },
        { key: 'people', header: 'Active', align: 'right', value: (r) => r.activeAssignments ?? 0, render: (r) => r.activeAssignments },
        { key: 'positions', header: 'Given to positions', value: (r) => (r.positions ?? []).join(', '), render: (r) => (r.positions ?? []).join(', ') || '—' },
      ]}
    />
  );
}

function menusFor(perms: string[]): string[] {
  const set = new Set(perms);
  return NAV_ITEMS.filter((n) => n.to !== '/' && n.group !== 'Help' && menuNeeds(n).some((c) => set.has(c))).map((n) => n.label);
}

function Positions({ guide }: { guide: Guide }) {
  return (
    <Stack spacing={1}>
      <Typography variant="body2" color="text.secondary">
        A position is a job title. It gives no access on its own: access comes from the roles assigned to the people posted in it. The roles below are those
        currently assigned through postings in each position.
      </Typography>
      <DataTable<api.PositionInfo>
        rows={guide.positions}
        rowKey={(p) => p.code ?? ''}
        filterPlaceholder="Filter: position, category or role"
        columns={[
          { key: 'title', header: 'Position', value: (p) => `${p.title} ${p.code}`, render: (p) => <><b>{p.title}</b><Typography variant="caption" component="div" color="text.secondary">{p.code}{p.status !== 'ACTIVE' ? ` · ${p.status}` : ''}</Typography></> },
          { key: 'category', header: 'Category', value: (p) => p.category, render: (p) => (p.category ?? '').replace('_', ' ').toLowerCase() },
          { key: 'people', header: 'People', align: 'right', value: (p) => p.people ?? 0, render: (p) => p.people },
          { key: 'roles', header: 'Roles held', value: (p) => (p.roles ?? []).join(' '), render: (p) => <Chips items={p.roles ?? []} empty="No access assigned" /> },
        ]}
      />
    </Stack>
  );
}

function Menus({ guide }: { guide: Guide }) {
  const roles = guide.roles ?? [];
  return (
    <Paper variant="outlined">
      <Table size="small">
        <TableHead>
          <TableRow><TableCell>Menu</TableCell><TableCell>Group</TableCell><TableCell>Shown with any of</TableCell><TableCell>Roles that open it</TableCell></TableRow>
        </TableHead>
        <TableBody>
          {NAV_ITEMS.map((n) => {
            const needs = menuNeeds(n);
            const openers = needs.length === 0 ? [] : roles.filter((r) => (r.permissions ?? []).some((c) => needs.includes(c))).map((r) => r.code ?? '');
            return (
              <TableRow key={n.to}>
                <TableCell><Link component={RouterLink} to={n.to}>{n.label}</Link></TableCell>
                <TableCell>{n.group}</TableCell>
                <TableCell>{needs.length === 0 ? 'Everyone signed in' : needs.map((c) => <code key={c} style={{ marginRight: 6 }}>{c}</code>)}</TableCell>
                <TableCell>{needs.length === 0 ? 'All' : <Chips items={openers} empty="No role yet" />}</TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </Paper>
  );
}

function Rules({ full }: { full: boolean }) {
  return (
    <Stack spacing={2}>
      <Paper sx={{ p: 2 }}>
        <Typography variant="h3" gutterBottom>Every action passes these checks</Typography>
        <ol style={{ margin: 0, paddingLeft: 20 }}>
          <li><Typography variant="body2">You are signed in (with your one-time code when your account needs it).</Typography></li>
          <li><Typography variant="body2">Your account is active, and your organisation's licence is active.</Typography></li>
          <li><Typography variant="body2">The module the action belongs to is licensed.</Typography></li>
          <li><Typography variant="body2">You hold an approved, current role whose published version includes the permission.</Typography></li>
          <li><Typography variant="body2">The role's scope covers the record: the whole organisation, a region, or the record's centre.</Typography></li>
          <li><Typography variant="body2">The record's rules allow it, including the two-person rules below.</Typography></li>
        </ol>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
          Any check that fails refuses the action and is recorded in the audit trail. Your position (job title) never gives access by itself.
        </Typography>
      </Paper>
      <Paper variant="outlined">
        <Typography variant="h3" sx={{ px: 2, pt: 2 }}>Scope</Typography>
        <Table size="small">
          <TableBody>
            <TableRow><TableCell sx={{ width: 220 }}>Whole organisation</TableCell><TableCell>Every centre; needed for organisation-wide permissions such as roles, the item master and catalogues</TableCell></TableRow>
            <TableRow><TableCell>Region (state, city or area)</TableCell><TableCell>Every centre under that part of the hierarchy</TableCell></TableRow>
            <TableRow><TableCell>Centre</TableCell><TableCell>That centre only</TableCell></TableRow>
          </TableBody>
        </Table>
      </Paper>
      <Paper variant="outlined">
        <Typography variant="h3" sx={{ px: 2, pt: 2 }}>Two-person rules</Typography>
        <Table size="small">
          <TableHead><TableRow><TableCell>Work</TableCell><TableCell>First person</TableCell><TableCell>A different person</TableCell></TableRow></TableHead>
          <TableBody>
            {TWO_PERSON.map(([w, a, b]) => <TableRow key={w}><TableCell>{w}</TableCell><TableCell>{a}</TableCell><TableCell>{b}</TableCell></TableRow>)}
          </TableBody>
        </Table>
      </Paper>
      {full && (
        <Typography variant="body2">
          The full reference, including what the software provider controls from the server and what is still to be built, is in the developer guide:{' '}
          <Link component={RouterLink} to="/help/developer/access-control-reference">Access control reference</Link>.
        </Typography>
      )}
    </Stack>
  );
}
