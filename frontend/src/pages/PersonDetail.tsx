import { Alert, Box, Button, MenuItem, TextField, Typography } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link as RouterLink, useParams } from 'react-router';

import * as api from '../api/generated/core-neha';
import { useAction } from '../api/useAction';
import { useSession } from '../auth/session';
import { DataTable } from '../components/DataTable';
import { FormDialog } from '../components/FormDialog';
import { PageHeader } from '../components/PageHeader';
import { ProblemAlert } from '../components/ProblemAlert';
import { KeyValue, Section } from '../components/Section';
import { ScopeFields, type ScopeValue } from './scope';

export function PersonDetail() {
  const { personId = '' } = useParams();
  const session = useSession();
  const detail = useQuery({ queryKey: ['person', personId], queryFn: () => api.person(personId) });
  const users = useQuery({ queryKey: ['users'], queryFn: () => api.users(), enabled: session.hasAnywhere('iam.assignment.read') });
  const [dialog, setDialog] = useState<'posting' | 'account' | 'responsibility' | undefined>();
  const [ending, setEnding] = useState<api.PostingView | undefined>();
  if (detail.error) {
    return <ProblemAlert error={detail.error} />;
  }
  const p = detail.data?.person;
  const account = (users.data ?? []).find((u) => u.personId === personId);
  return (
    <>
      <PageHeader title={p ? `${p.givenName} ${p.familyName}` : 'Person'}
        subtitle={p ? <>Employee no. <strong>{p.employeeNo}</strong> · {p.email}</> : undefined}
        actions={
          <>
            {session.hasAnywhere('workforce.person.manage') && <Button onClick={() => setDialog('posting')}>Add posting</Button>}
            {session.hasAnywhere('iam.responsibility.manage') && <Button onClick={() => setDialog('responsibility')}>Assign responsibility</Button>}
            {!account && session.hasAnywhere('iam.user.manage') && <Button variant="contained" onClick={() => setDialog('account')}>Create user account</Button>}
          </>
        } />
      {p?.demo && <Alert severity="info" sx={{ mb: 2 }}>Synthetic demonstration person.</Alert>}
      <Section title="Postings" description="Each posting is a position at a centre, with its own dates and FTE. Postings do not give system access.">
        <DataTable
          rows={detail.data?.postings}
          loading={detail.isLoading}
          rowKey={(r) => r.id ?? ''}
          empty="No postings."
          columns={[
            { key: 'centre', header: 'Centre', render: (r) => r.centreName, value: (r) => r.centreName },
            { key: 'position', header: 'Position', render: (r) => r.positionTitle, value: (r) => r.positionTitle },
            { key: 'dept', header: 'Department', render: (r) => r.departmentName ?? '—' },
            { key: 'fte', header: 'FTE', align: 'right', render: (r) => r.fte },
            { key: 'from', header: 'From', render: (r) => r.validFrom, value: (r) => r.validFrom },
            { key: 'to', header: 'To', render: (r) => r.validTo ?? 'Open' },
            {
              key: 'end', header: '', align: 'right',
              render: (r) => !r.validTo && session.hasAtCentre('workforce.person.manage', r.centreId) ? <Button onClick={() => setEnding(r)}>End…</Button> : null,
            },
          ]}
        />
      </Section>
      <Section title="User account">
        {account ? (
          <KeyValue items={[
            ['Username', <RouterLink key="u" to={`/users/${account.id}`}>{account.username}</RouterLink>],
            ['Status', account.status],
            ['Login linked', account.loginLinked ? 'Yes' : 'No'],
          ]} />
        ) : (
          <Typography variant="body2" color="text.secondary">No user account. A person can exist without system access.</Typography>
        )}
      </Section>
      {dialog === 'posting' && <AddPosting personId={personId} onClose={() => setDialog(undefined)} />}
      {dialog === 'account' && p && <CreateAccount person={p} onClose={() => setDialog(undefined)} />}
      {dialog === 'responsibility' && <AssignResponsibility personId={personId} onClose={() => setDialog(undefined)} />}
      {ending && <EndPosting posting={ending} personId={personId} onClose={() => setEnding(undefined)} />}
    </>
  );
}

function AddPosting({ personId, onClose }: { personId: string; onClose: () => void }) {
  const session = useSession();
  const centres = (session.me.centres ?? []).filter((c) => session.hasAtCentre('workforce.person.manage', c.centreId));
  const [centreId, setCentreId] = useState(centres.some((c) => c.centreId === session.centreId) ? session.centreId ?? '' : centres[0]?.centreId ?? '');
  const est = useQuery({ queryKey: ['establishments', centreId], queryFn: () => api.establishments(centreId), enabled: !!centreId });
  const [establishmentId, setEst] = useState('');
  const [fte, setFte] = useState('1');
  const [validFrom, setFrom] = useState('');
  const [validTo, setTo] = useState('');
  const action = useAction((b: api.AddPosting) => api.addPosting(personId, b), { invalidate: [['person', personId], ['establishments', centreId]], success: 'Posting added', onDone: onClose });
  return (
    <FormDialog open title="Add posting" submitLabel="Add posting" onClose={onClose} busy={action.isPending} error={action.error}
      onSubmit={() => action.mutate({ establishmentId, fte: Number(fte), validFrom: validFrom || undefined, validTo: validTo || undefined })}>
      <TextField select label="Centre" value={centreId} onChange={(e) => { setCentreId(e.target.value); setEst(''); }} required>
        {centres.map((c) => <MenuItem key={c.centreId} value={c.centreId}>{c.name}</MenuItem>)}
      </TextField>
      <TextField select label="Established position" value={establishmentId} onChange={(e) => setEst(e.target.value)} required
        helperText="Only positions established at this centre are offered">
        {(est.data ?? []).map((e) => (
          <MenuItem key={e.id} value={e.id} disabled={(e.activePostings ?? 0) >= (e.approvedHeadcount ?? 0)}>
            {e.positionTitle}{e.departmentName ? ` · ${e.departmentName}` : ''} ({e.activePostings}/{e.approvedHeadcount})
          </MenuItem>
        ))}
      </TextField>
      <TextField label="FTE" type="number" value={fte} onChange={(e) => setFte(e.target.value)} slotProps={{ htmlInput: { step: 0.05, min: 0.05, max: 1 } }} />
      <Box sx={{ display: 'flex', gap: 2 }}>
        <TextField label="From" type="date" value={validFrom} onChange={(e) => setFrom(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} helperText="Empty = today" />
        <TextField label="To (optional)" type="date" value={validTo} onChange={(e) => setTo(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
      </Box>
    </FormDialog>
  );
}

function EndPosting({ posting, personId, onClose }: { posting: api.PostingView; personId: string; onClose: () => void }) {
  const [validTo, setTo] = useState(new Date().toISOString().slice(0, 10));
  const action = useAction((b: api.EndPosting) => api.endPosting(posting.id ?? '', b), { invalidate: [['person', personId]], success: 'Posting ended', onDone: onClose });
  return (
    <FormDialog open title={`End posting at ${posting.centreName}`} submitLabel="End posting" onClose={onClose} busy={action.isPending} error={action.error}
      onSubmit={() => action.mutate({ validTo, expectedVersion: posting.version ?? 0 })}>
      <TextField label="Last day" type="date" value={validTo} onChange={(e) => setTo(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
    </FormDialog>
  );
}

function CreateAccount({ person, onClose }: { person: api.PersonView; onClose: () => void }) {
  // The login id is the permanent employee number, so it never changes with the person's position.
  const suggested = (person.employeeNo ?? '').toLowerCase();
  const [username, setUsername] = useState(suggested);
  const [created, setCreated] = useState<api.UserCreated | undefined>();
  const action = useAction((b: api.CreateUser) => api.createUser(b), { invalidate: [['users']], onDone: setCreated });
  if (created) {
    return (
      <FormDialog open title="User account created" submitLabel="Done" onClose={onClose} onSubmit={onClose}>
        <Alert severity="success">The account <strong>{created.user?.username}</strong> has no access yet. Request a role assignment next.</Alert>
        <Alert severity="warning">
          One-time temporary password (shown only now): <code style={{ fontSize: 15 }}>{created.temporaryPassword}</code>
          <br />{created.instructions}
        </Alert>
      </FormDialog>
    );
  }
  return (
    <FormDialog open title="Create user account" submitLabel="Create account" onClose={onClose} busy={action.isPending} error={action.error}
      intro={<Typography variant="body2" color="text.secondary">Creates a login. It grants no permission until a role assignment is requested and approved by someone else.</Typography>}
      onSubmit={() => action.mutate({ personId: person.id ?? '', username })}>
      <TextField label="Login id" value={username} onChange={(e) => setUsername(e.target.value)} required autoFocus
        helperText="The employee number. Keep it: it stays the same when the person's position changes. Sign-in accepts capitals too." />
    </FormDialog>
  );
}

function AssignResponsibility({ personId, onClose }: { personId: string; onClose: () => void }) {
  const types = useQuery({ queryKey: ['responsibility-types'], queryFn: () => api.responsibilityTypes() });
  const [responsibilityId, setResp] = useState('');
  const [scope, setScope] = useState<ScopeValue>({ scopeType: 'CENTRE', scopeId: '' });
  const action = useAction((b: api.AssignResponsibility) => api.assignResponsibility(b), { invalidate: [['person', personId]], success: 'Responsibility assigned (no access granted)', onDone: onClose });
  return (
    <FormDialog open title="Assign responsibility" submitLabel="Assign" onClose={onClose} busy={action.isPending} error={action.error}
      intro={<Alert severity="info">Responsibilities record accountability only. They never give system access.</Alert>}
      onSubmit={() => action.mutate({ personId, responsibilityId, ...scope })}>
      <TextField select label="Responsibility" value={responsibilityId} onChange={(e) => setResp(e.target.value)} required>
        {(types.data ?? []).map((t) => <MenuItem key={t.id} value={t.id}>{t.name}</MenuItem>)}
      </TextField>
      <ScopeFields value={scope} onChange={setScope} />
    </FormDialog>
  );
}
