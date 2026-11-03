import { Alert, Box, Button, Chip, MenuItem, Paper, Stack, Tab, Tabs, TextField, Typography } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useParams } from 'react-router';

import * as api from '../api/generated/core-neha';
import { useAction } from '../api/useAction';
import { useSession } from '../auth/session';
import { DataTable } from '../components/DataTable';
import { FormDialog } from '../components/FormDialog';
import { PageHeader } from '../components/PageHeader';
import { ProblemAlert } from '../components/ProblemAlert';
import { StatusChip } from '../components/StatusChip';
import { ScopeFields, type ScopeValue } from './scope';

export function UserDetail() {
  const { userId = '' } = useParams();
  const session = useSession();
  const access = useQuery({ queryKey: ['effective-access', userId], queryFn: () => api.effectiveAccess(userId) });
  const [tab, setTab] = useState('assignments');
  const [dialog, setDialog] = useState<'request' | 'disable' | 'enable' | undefined>();
  const [revoking, setRevoking] = useState<api.AssignmentView | undefined>();
  if (access.error) {
    return <ProblemAlert error={access.error} />;
  }
  const user = access.data?.user;
  const isSelf = user?.id === session.me.userId;
  return (
    <>
      <PageHeader
        title={user?.username ?? 'User'}
        subtitle={user ? <>{user.personName} · <StatusChip status={user.status} /></> : undefined}
        actions={
          <>
            {session.hasAnywhere('iam.assignment.request') && !isSelf && user?.status === 'ACTIVE' && (
              <Button variant="contained" onClick={() => setDialog('request')}>Request access…</Button>
            )}
            {session.hasAnywhere('iam.user.manage') && !isSelf && user?.status === 'ACTIVE' && (
              <Button color="error" onClick={() => setDialog('disable')}>Disable account…</Button>
            )}
            {session.hasAnywhere('iam.user.manage') && user?.status === 'DISABLED' && (
              <Button onClick={() => setDialog('enable')}>Enable account…</Button>
            )}
          </>
        }
      />
      <Tabs value={tab} onChange={(_, v: string) => setTab(v)} sx={{ mb: 2, borderBottom: 1, borderColor: 'divider' }}>
        <Tab value="assignments" label="Assignments" />
        <Tab value="effective" label="Effective access" />
        <Tab value="explain" label="Explain a decision" />
      </Tabs>
      {tab === 'assignments' && (
        <DataTable
          rows={access.data?.assignments}
          loading={access.isLoading}
          rowKey={(r) => r.id ?? ''}
          empty="No assignments."
          columns={[
            { key: 'kind', header: 'Kind', render: (r) => (r.kind === 'AUTHORITY' ? 'Authority to assign' : 'Access'), value: (r) => r.kind },
            { key: 'role', header: 'Role', render: (r) => r.roleName ?? 'All roles', value: (r) => r.roleName },
            { key: 'scope', header: 'Applies to', render: (r) => `${r.scopeName} (${r.scopeType?.toLowerCase().replace('_', ' ')})`, value: (r) => r.scopeName },
            { key: 'status', header: 'Status', render: (r) => <StatusChip status={r.status} />, value: (r) => r.status },
            { key: 'requested', header: 'Requested by', render: (r) => (r.viaBootstrap ? 'Bootstrap' : r.requestedByName), value: (r) => r.requestedByName },
            { key: 'dates', header: 'Valid', render: (r) => `${r.validFrom ?? ''} → ${r.validTo ?? 'open'}` },
            {
              key: 'revoke', header: '', align: 'right',
              render: (r) => (r.status === 'ACTIVE' || r.status === 'PENDING_APPROVAL') && session.hasAnywhere('iam.assignment.revoke')
                ? <Button color="warning" onClick={() => setRevoking(r)}>Revoke…</Button> : null,
            },
          ]}
        />
      )}
      {tab === 'effective' && access.data && <Effective access={access.data} />}
      {tab === 'explain' && <Explain userId={userId} />}
      {dialog === 'request' && user && <RequestAccess user={user} onClose={() => setDialog(undefined)} />}
      {(dialog === 'disable' || dialog === 'enable') && user && <ChangeStatus user={user} disable={dialog === 'disable'} onClose={() => setDialog(undefined)} />}
      {revoking && <RevokeAssignment assignment={revoking} userId={userId} onClose={() => setRevoking(undefined)} />}
    </>
  );
}

function Effective({ access }: { access: api.EffectiveAccess }) {
  return (
    <Stack spacing={2}>
      {(access.notes ?? []).map((n) => <Alert key={n} severity="info">{n}</Alert>)}
      <DataTable
        rows={access.permissions}
        rowKey={(r) => r.permission ?? ''}
        empty="No effective permissions."
        columns={[
          { key: 'perm', header: 'Permission', render: (r) => <><code>{r.permission}</code><Typography variant="caption" component="div" color="text.secondary">{r.description}</Typography></>, value: (r) => r.permission },
          {
            key: 'scopes', header: 'Where (and why)',
            render: (r) => (
              <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 0.5 }}>
                {(r.scopes ?? []).map((s) => <Chip key={`${s.viaAssignment}-${s.scopeId}`} label={`${s.scopeName} · via ${s.viaRole}`} variant="outlined" />)}
              </Stack>
            ),
          },
        ]}
      />
      {(access.responsibilities ?? []).length > 0 && (
        <Paper sx={{ p: 2 }}>
          <Typography variant="h3" gutterBottom>Responsibilities (grant no access)</Typography>
          {(access.responsibilities ?? []).map((r) => <Chip key={r.id} label={`${r.name} · ${r.scopeName}`} sx={{ mr: 0.5 }} />)}
        </Paper>
      )}
    </Stack>
  );
}

function Explain({ userId }: { userId: string }) {
  const catalogue = useQuery({ queryKey: ['permissions'], queryFn: () => api.permissions(), retry: false });
  const [permission, setPermission] = useState('org.centre.read');
  const [scope, setScope] = useState<ScopeValue>({ scopeType: 'CENTRE', scopeId: '' });
  const [result, setResult] = useState<api.Explanation | undefined>();
  const [error, setError] = useState<unknown>();
  const run = async () => {
    setError(undefined);
    try {
      setResult(await api.explain(userId, { permission, scopeType: scope.scopeType, scopeId: scope.scopeId }));
    } catch (e) {
      setError(e);
    }
  };
  return (
    <Paper sx={{ p: 2, maxWidth: 720 }}>
      <Stack spacing={2}>
        <TextField select label="Permission" value={permission} onChange={(e) => setPermission(e.target.value)}>
          {(catalogue.data ?? [{ code: 'org.centre.read', description: '' }]).map((p) => <MenuItem key={p.code} value={p.code}>{p.code}</MenuItem>)}
        </TextField>
        <ScopeFields value={scope} onChange={setScope} />
        <Box><Button variant="contained" onClick={run} disabled={!scope.scopeId}>Explain</Button></Box>
        <ProblemAlert error={error} />
        {result && (
          <Alert severity={result.allowed ? 'success' : 'warning'}>
            <strong>{result.allowed ? 'Allowed' : 'Refused'}</strong>
            <ul style={{ margin: '4px 0 0', paddingLeft: 18 }}>{(result.reasons ?? []).map((r) => <li key={r}>{r}</li>)}</ul>
          </Alert>
        )}
      </Stack>
    </Paper>
  );
}

function RequestAccess({ user, onClose }: { user: api.UserView; onClose: () => void }) {
  const roles = useQuery({ queryKey: ['roles'], queryFn: () => api.roles(), retry: false });
  const [kind, setKind] = useState('ACCESS');
  const [roleId, setRoleId] = useState('');
  const [scope, setScope] = useState<ScopeValue>({ scopeType: 'CENTRE', scopeId: '' });
  const [reason, setReason] = useState('');
  const [validTo, setValidTo] = useState('');
  const action = useAction((b: api.RequestAssignment) => api.request(b), {
    invalidate: [['effective-access', user.id]], success: 'Request submitted. Another authorised person must approve it.', onDone: onClose,
  });
  const published = (roles.data ?? []).filter((r) => r.publishedVersion && r.status === 'ACTIVE');
  return (
    <FormDialog open title={`Request access for ${user.username}`} submitLabel="Submit request" onClose={onClose} busy={action.isPending} error={action.error}
      intro={<Alert severity="info">The request takes effect only after someone other than you (and other than {user.username}) approves it.</Alert>}
      onSubmit={() => action.mutate({ kind, userId: user.id ?? '', roleId: roleId || undefined, ...scope, reason, validTo: validTo || undefined })}>
      <TextField select label="Kind" value={kind} onChange={(e) => setKind(e.target.value)}>
        <MenuItem value="ACCESS">Access (permissions of a role)</MenuItem>
        <MenuItem value="AUTHORITY">Authority to request this role for others</MenuItem>
      </TextField>
      <TextField select label="Role" value={roleId} onChange={(e) => setRoleId(e.target.value)} required={kind === 'ACCESS'}
        helperText={roles.error ? 'You cannot list roles; ask a role administrator for the role.' : 'Only roles with a published version'}>
        {published.map((r) => <MenuItem key={r.id} value={r.id}>{r.name} (v{r.publishedVersion})</MenuItem>)}
      </TextField>
      <ScopeFields value={scope} onChange={setScope} />
      <TextField label="Reason" value={reason} onChange={(e) => setReason(e.target.value)} required multiline minRows={2} />
      <TextField label="End date (optional)" type="date" value={validTo} onChange={(e) => setValidTo(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
    </FormDialog>
  );
}

function RevokeAssignment({ assignment, userId, onClose }: { assignment: api.AssignmentView; userId: string; onClose: () => void }) {
  const [reason, setReason] = useState('');
  const action = useAction((b: api.Revoke) => api.revoke(assignment.id ?? '', b), { invalidate: [['effective-access', userId]], success: 'Revoked; access ends now', onDone: onClose });
  return (
    <FormDialog open title={`Revoke ${assignment.roleName ?? 'authority'} at ${assignment.scopeName}`} submitLabel="Revoke" onClose={onClose} busy={action.isPending} error={action.error}
      onSubmit={() => action.mutate({ reason, expectedVersion: assignment.version ?? 0 })}>
      <TextField label="Reason" value={reason} onChange={(e) => setReason(e.target.value)} required autoFocus />
    </FormDialog>
  );
}

function ChangeStatus({ user, disable, onClose }: { user: api.UserView; disable: boolean; onClose: () => void }) {
  const [reason, setReason] = useState('');
  const action = useAction(
    (b: api.ChangeUserStatus) => (disable ? api.disable(user.id ?? '', b) : api.enable(user.id ?? '', b)),
    { invalidate: [['effective-access', user.id], ['users']], success: disable ? 'Account disabled; all sessions ended' : 'Account enabled', onDone: onClose },
  );
  return (
    <FormDialog open title={`${disable ? 'Disable' : 'Enable'} ${user.username}`} submitLabel={disable ? 'Disable now' : 'Enable'} onClose={onClose} busy={action.isPending} error={action.error}
      intro={disable ? <Alert severity="warning">Access ends on the user's next request, open sessions are ended and new sign-ins are blocked.</Alert> : undefined}
      onSubmit={() => action.mutate({ reason, expectedVersion: user.version ?? 0 })}>
      <TextField label="Reason" value={reason} onChange={(e) => setReason(e.target.value)} required autoFocus />
    </FormDialog>
  );
}
