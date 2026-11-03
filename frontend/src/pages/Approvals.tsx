import { Button, Stack, TextField, Typography } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link as RouterLink } from 'react-router';

import * as api from '../api/generated/core-neha';
import { useAction } from '../api/useAction';
import { useSession } from '../auth/session';
import { DataTable } from '../components/DataTable';
import { FormDialog } from '../components/FormDialog';
import { PageHeader } from '../components/PageHeader';
import { Section } from '../components/Section';

export function Approvals() {
  const session = useSession();
  const pending = useQuery({ queryKey: ['approvals'], queryFn: () => api.pending() });
  const roles = useQuery({ queryKey: ['roles'], queryFn: () => api.roles(), enabled: session.hasTenantWide('iam.role.approve') });
  const [deciding, setDeciding] = useState<{ a: api.AssignmentView; approve: boolean } | undefined>();
  const pendingRoles = (roles.data ?? []).filter((r) => r.openVersionStatus === 'PENDING_APPROVAL');
  return (
    <>
      <PageHeader title="Approvals" subtitle="Requests you may decide. Requests you made, or that would give you access, are never shown to you." />
      <Section title="Access and authority requests">
        <DataTable
          rows={pending.data}
          loading={pending.isLoading}
          rowKey={(r) => r.id ?? ''}
          empty="Nothing waiting for you."
          columns={[
            { key: 'user', header: 'For', render: (r) => <RouterLink to={`/users/${r.userId}`}>{r.username}</RouterLink>, value: (r) => r.username },
            { key: 'kind', header: 'Kind', render: (r) => (r.kind === 'AUTHORITY' ? 'Authority' : 'Access') },
            { key: 'role', header: 'Role', render: (r) => r.roleName ?? 'All roles', value: (r) => r.roleName },
            { key: 'scope', header: 'Applies to', render: (r) => r.scopeName, value: (r) => r.scopeName },
            { key: 'reason', header: 'Reason', render: (r) => r.reason },
            { key: 'by', header: 'Requested by', render: (r) => r.requestedByName, value: (r) => r.requestedByName },
            {
              key: 'decide', header: '', align: 'right',
              render: (r) => (
                <Stack direction="row" spacing={1} sx={{ justifyContent: 'flex-end' }}>
                  <Button color="error" onClick={() => setDeciding({ a: r, approve: false })}>Reject…</Button>
                  <Button variant="contained" onClick={() => setDeciding({ a: r, approve: true })}>Approve…</Button>
                </Stack>
              ),
            },
          ]}
        />
      </Section>
      {session.hasTenantWide('iam.role.approve') && (
        <Section title="Role versions waiting for approval">
          {pendingRoles.length === 0 ? (
            <Typography variant="body2" color="text.secondary">None.</Typography>
          ) : (
            pendingRoles.map((r) => (
              <Typography key={r.id} variant="body2"><RouterLink to={`/roles/${r.id}`}>{r.name} v{r.openVersion}</RouterLink></Typography>
            ))
          )}
        </Section>
      )}
      {deciding && <Decide {...deciding} onClose={() => setDeciding(undefined)} />}
    </>
  );
}

function Decide({ a, approve, onClose }: { a: api.AssignmentView; approve: boolean; onClose: () => void }) {
  const [note, setNote] = useState('');
  const action = useAction(
    () => (approve ? api.approve : api.reject)(a.id ?? '', { note, expectedVersion: a.version ?? 0 }),
    { invalidate: [['approvals']], success: approve ? `Approved: ${a.username} now has this access` : 'Rejected', onDone: onClose },
  );
  return (
    <FormDialog open title={`${approve ? 'Approve' : 'Reject'} ${a.kind === 'AUTHORITY' ? 'authority' : 'access'} for ${a.username}`}
      submitLabel={approve ? 'Approve' : 'Reject'} onClose={onClose} busy={action.isPending} error={action.error} onSubmit={() => action.mutate(undefined)}
      intro={<Typography variant="body2">{a.roleName ?? 'All roles'} at {a.scopeName}. Reason given: {a.reason}</Typography>}>
      <TextField label="Decision note" value={note} onChange={(e) => setNote(e.target.value)} multiline minRows={2} autoFocus />
    </FormDialog>
  );
}
