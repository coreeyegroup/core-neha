import { Alert, Box, Button, Chip, Paper, Stack, TextField, Typography } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useParams } from 'react-router';

import * as api from '../api/generated/core-neha';
import { useAction } from '../api/useAction';
import { useSession } from '../auth/session';
import { FormDialog } from '../components/FormDialog';
import { PageHeader } from '../components/PageHeader';
import { ProblemAlert } from '../components/ProblemAlert';
import { StatusChip } from '../components/StatusChip';
import { PermissionPicker } from './permissionPicker';

export function RoleDetail() {
  const { roleId = '' } = useParams();
  const session = useSession();
  const detail = useQuery({ queryKey: ['role', roleId], queryFn: () => api.role(roleId) });
  const [editing, setEditing] = useState<api.RoleVersionView | 'new' | undefined>();
  const [deciding, setDeciding] = useState<{ version: api.RoleVersionView; approve: boolean } | undefined>();
  const submit = useAction((v: api.RoleVersionView) => api.submit(v.id ?? '', { expectedVersion: v.version ?? 0 }), {
    invalidate: [['role', roleId], ['roles']], success: 'Submitted for approval',
  });
  if (detail.error) {
    return <ProblemAlert error={detail.error} />;
  }
  const role = detail.data?.role;
  const versions = detail.data?.versions ?? [];
  const hasOpen = versions.some((v) => v.status === 'DRAFT' || v.status === 'PENDING_APPROVAL');
  const me = session.me.userId;
  return (
    <>
      <PageHeader
        title={role?.name ?? 'Role'}
        subtitle={role ? `${role.code} · ${role.system ? 'created by bootstrap' : 'custom role'}` : undefined}
        actions={session.hasTenantWide('iam.role.draft') && !hasOpen && <Button variant="contained" onClick={() => setEditing('new')}>Draft new version</Button>}
      />
      <ProblemAlert error={submit.error} />
      <Stack spacing={2}>
        {versions.map((v) => (
          <Paper key={v.id} sx={{ p: 2 }}>
            <Stack direction={{ xs: 'column', sm: 'row' }} sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' }, mb: 1, gap: 1 }}>
              <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                <Typography variant="h3">Version {v.versionNo}</Typography>
                <StatusChip status={v.status} />
              </Stack>
              <Stack direction="row" spacing={1}>
                {v.status === 'DRAFT' && session.hasTenantWide('iam.role.draft') && (
                  <>
                    <Button onClick={() => setEditing(v)}>Edit draft</Button>
                    <Button variant="contained" onClick={() => submit.mutate(v)} disabled={submit.isPending}>Submit for approval</Button>
                  </>
                )}
                {v.status === 'PENDING_APPROVAL' && session.hasTenantWide('iam.role.approve') && (
                  v.submittedBy === me || v.createdBy === me ? (
                    <Chip label="You drafted or submitted this version; someone else must decide" variant="outlined" color="warning" />
                  ) : (
                    <>
                      <Button color="error" onClick={() => setDeciding({ version: v, approve: false })}>Reject…</Button>
                      <Button variant="contained" onClick={() => setDeciding({ version: v, approve: true })}>Review and approve…</Button>
                    </>
                  )
                )}
              </Stack>
            </Stack>
            {v.changeNote && <Typography variant="body2" color="text.secondary" gutterBottom>{v.changeNote}</Typography>}
            <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 0.5 }}>
              {(v.permissions ?? []).map((p) => <Chip key={p} label={p} variant="outlined" />)}
            </Stack>
            {v.decisionNote && <Typography variant="caption" color="text.secondary" component="div" sx={{ mt: 1 }}>Decision: {v.decisionNote}</Typography>}
          </Paper>
        ))}
      </Stack>
      {editing && <DraftEditor roleId={roleId} version={editing === 'new' ? undefined : editing}
        initial={editing === 'new' ? versions.find((v) => v.status === 'PUBLISHED')?.permissions ?? [] : editing.permissions ?? []}
        onClose={() => setEditing(undefined)} />}
      {deciding && <Decide roleId={roleId} {...deciding} onClose={() => setDeciding(undefined)} />}
    </>
  );
}

function DraftEditor({ roleId, version, initial, onClose }: { roleId: string; version?: api.RoleVersionView; initial: string[]; onClose: () => void }) {
  const catalogue = useQuery({ queryKey: ['permissions'], queryFn: () => api.permissions() });
  const [perms, setPerms] = useState(new Set(initial));
  const [note, setNote] = useState(version?.changeNote ?? '');
  const action = useAction(
    () => (version
      ? api.updateDraft(version.id ?? '', { permissions: [...perms], changeNote: note, expectedVersion: version.version ?? 0 })
      : api.draftVersion(roleId, { permissions: [...perms], changeNote: note })),
    { invalidate: [['role', roleId], ['roles']], success: 'Draft saved', onDone: onClose },
  );
  return (
    <FormDialog open maxWidth="md" title={version ? `Edit draft v${version.versionNo}` : 'Draft new version'} submitLabel="Save draft"
      onClose={onClose} busy={action.isPending} error={action.error} onSubmit={() => action.mutate(undefined)}>
      <TextField label="What changes and why" value={note} onChange={(e) => setNote(e.target.value)} multiline minRows={2} />
      <PermissionPicker catalogue={catalogue.data ?? []} value={perms} onChange={setPerms} />
    </FormDialog>
  );
}

function Decide({ roleId, version, approve, onClose }: { roleId: string; version: api.RoleVersionView; approve: boolean; onClose: () => void }) {
  const impact = useQuery({ queryKey: ['impact', version.id], queryFn: () => api.impact(version.id ?? '') });
  const [note, setNote] = useState('');
  const action = useAction(
    () => (approve ? api.approveVersion : api.rejectVersion)(version.id ?? '', { note, expectedVersion: version.version ?? 0 }),
    { invalidate: [['role', roleId], ['roles']], success: approve ? 'Published. Access of affected users has changed.' : 'Rejected', onDone: onClose },
  );
  const i = impact.data;
  return (
    <FormDialog open maxWidth="md" title={`${approve ? 'Approve' : 'Reject'} version ${version.versionNo}`} submitLabel={approve ? 'Approve and publish' : 'Reject'}
      onClose={onClose} busy={action.isPending} error={action.error} onSubmit={() => action.mutate(undefined)}
      intro={i && (
        <Box>
          <Alert severity={approve ? 'warning' : 'info'} sx={{ mb: 1 }}>
            {approve ? `Publishing changes the access of ${i.affectedUsers} user(s) immediately.` : 'Rejecting leaves the current published version in force.'}
          </Alert>
          <Typography variant="body2"><strong>Added:</strong> {(i.added ?? []).join(', ') || 'none'}</Typography>
          <Typography variant="body2"><strong>Removed:</strong> {(i.removed ?? []).join(', ') || 'none'}</Typography>
          {(i.affected ?? []).length > 0 && (
            <Typography variant="body2" color="text.secondary">Affected: {(i.affected ?? []).map((u) => u.username).join(', ')}</Typography>
          )}
        </Box>
      )}>
      <TextField label="Decision note" value={note} onChange={(e) => setNote(e.target.value)} multiline minRows={2} />
    </FormDialog>
  );
}
