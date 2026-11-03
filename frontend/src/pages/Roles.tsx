import { Button, MenuItem, TextField } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate } from 'react-router';

import * as api from '../api/generated/core-neha';
import { useAction } from '../api/useAction';
import { useSession } from '../auth/session';
import { DataTable } from '../components/DataTable';
import { FormDialog } from '../components/FormDialog';
import { PageHeader } from '../components/PageHeader';
import { StatusChip } from '../components/StatusChip';
import { PermissionPicker } from './permissionPicker';

export function Roles() {
  const session = useSession();
  const navigate = useNavigate();
  const roles = useQuery({ queryKey: ['roles'], queryFn: () => api.roles() });
  const [dialog, setDialog] = useState<'create' | 'clone' | undefined>();
  const canDraft = session.hasTenantWide('iam.role.draft');
  return (
    <>
      <PageHeader
        title="Roles"
        subtitle="A role grants permissions only through its published version. Every version is approved by someone other than its author."
        actions={canDraft && <><Button onClick={() => setDialog('clone')}>Clone role</Button><Button variant="contained" onClick={() => setDialog('create')}>Draft custom role</Button></>}
      />
      <DataTable
        rows={roles.data}
        loading={roles.isLoading}
        rowKey={(r) => r.id ?? ''}
        filterPlaceholder="Filter roles"
        onRowClick={(r) => navigate(`/roles/${r.id}`)}
        columns={[
          { key: 'name', header: 'Role', render: (r) => r.name, value: (r) => r.name },
          { key: 'code', header: 'Code', render: (r) => r.code, value: (r) => r.code },
          { key: 'published', header: 'Published', render: (r) => (r.publishedVersion ? `v${r.publishedVersion}` : '—'), value: (r) => r.publishedVersion },
          { key: 'open', header: 'In progress', render: (r) => (r.openVersion ? <>v{r.openVersion} <StatusChip status={r.openVersionStatus} /></> : '—') },
          { key: 'system', header: 'Origin', render: (r) => (r.system ? 'Bootstrap' : 'Custom') },
        ]}
      />
      {dialog === 'create' && <CreateRole onClose={() => setDialog(undefined)} onCreated={(id) => navigate(`/roles/${id}`)} />}
      {dialog === 'clone' && <CloneRole roles={roles.data ?? []} onClose={() => setDialog(undefined)} onCreated={(id) => navigate(`/roles/${id}`)} />}
    </>
  );
}

function CreateRole({ onClose, onCreated }: { onClose: () => void; onCreated: (id: string) => void }) {
  const catalogue = useQuery({ queryKey: ['permissions'], queryFn: () => api.permissions() });
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [perms, setPerms] = useState<Set<string>>(new Set());
  const action = useAction((b: api.CreateRole) => api.createRole(b), {
    invalidate: [['roles']], success: 'Draft saved. Submit it for approval when ready.', onDone: (r) => r.role?.id && onCreated(r.role.id),
  });
  return (
    <FormDialog open maxWidth="md" title="Draft custom role" submitLabel="Save draft" onClose={onClose} busy={action.isPending} error={action.error}
      onSubmit={() => action.mutate({ code: code.trim().toUpperCase(), name, description: description || undefined, permissions: [...perms], changeNote: 'Initial version' })}>
      <TextField label="Code" value={code} onChange={(e) => setCode(e.target.value)} required autoFocus helperText="For example OPTICAL_SUPERVISOR" />
      <TextField label="Name" value={name} onChange={(e) => setName(e.target.value)} required />
      <TextField label="Description" value={description} onChange={(e) => setDescription(e.target.value)} multiline minRows={2} />
      <PermissionPicker catalogue={catalogue.data ?? []} value={perms} onChange={setPerms} />
    </FormDialog>
  );
}

function CloneRole({ roles, onClose, onCreated }: { roles: api.RoleSummary[]; onClose: () => void; onCreated: (id: string) => void }) {
  const [sourceRoleId, setSource] = useState('');
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const action = useAction((b: api.CloneRole) => api.cloneRole(b), { invalidate: [['roles']], success: 'Clone saved as a draft', onDone: (r) => r.role?.id && onCreated(r.role.id) });
  return (
    <FormDialog open title="Clone role" submitLabel="Clone as draft" onClose={onClose} busy={action.isPending} error={action.error}
      onSubmit={() => action.mutate({ sourceRoleId, code: code.trim().toUpperCase(), name })}>
      <TextField select label="Source role (published)" value={sourceRoleId} onChange={(e) => setSource(e.target.value)} required>
        {roles.filter((r) => r.publishedVersion).map((r) => <MenuItem key={r.id} value={r.id}>{r.name}</MenuItem>)}
      </TextField>
      <TextField label="New code" value={code} onChange={(e) => setCode(e.target.value)} required />
      <TextField label="New name" value={name} onChange={(e) => setName(e.target.value)} required />
    </FormDialog>
  );
}
