import { Button, MenuItem, TextField, Typography } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';

import * as api from '../api/generated/core-neha';
import { useAction } from '../api/useAction';
import { useSession } from '../auth/session';
import { DataTable } from '../components/DataTable';
import { FormDialog } from '../components/FormDialog';
import { PageHeader } from '../components/PageHeader';
import { StatusChip } from '../components/StatusChip';

export const CATEGORIES = ['CLINICAL', 'CLINICAL_SUPPORT', 'ADMINISTRATIVE', 'OPERATIONS'];
export const label = (s?: string) => (s ? s.charAt(0) + s.slice(1).toLowerCase().replaceAll('_', ' ') : '');

export function Departments() {
  const session = useSession();
  const list = useQuery({ queryKey: ['departments'], queryFn: () => api.departments() });
  const [open, setOpen] = useState(false);
  return (
    <>
      <PageHeader
        title="Department library"
        subtitle="Group-wide definitions. A centre has a department only when it is enabled at that centre."
        actions={session.hasTenantWide('org.structure.manage') && <Button variant="contained" onClick={() => setOpen(true)}>Define department</Button>}
      />
      <DataTable
        rows={list.data}
        loading={list.isLoading}
        rowKey={(r) => r.id ?? ''}
        filterPlaceholder="Filter departments"
        columns={[
          { key: 'name', header: 'Department', render: (r) => r.name, value: (r) => r.name },
          { key: 'code', header: 'Code', render: (r) => r.code, value: (r) => r.code },
          { key: 'category', header: 'Category', render: (r) => label(r.category), value: (r) => r.category },
          { key: 'status', header: 'Status', render: (r) => <StatusChip status={r.status} /> },
        ]}
      />
      {open && <DefineDepartment onClose={() => setOpen(false)} />}
    </>
  );
}

function DefineDepartment({ onClose }: { onClose: () => void }) {
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [category, setCategory] = useState('CLINICAL');
  const action = useAction((b: api.CreateDepartment) => api.createDepartment(b), { invalidate: [['departments']], success: 'Department defined', onDone: onClose });
  return (
    <FormDialog open title="Define department" submitLabel="Define" onClose={onClose} busy={action.isPending} error={action.error}
      intro={<Typography variant="body2" color="text.secondary">Defining a department does not add it to any centre.</Typography>}
      onSubmit={() => action.mutate({ code: code.trim().toUpperCase(), name, category })}>
      <TextField label="Code" value={code} onChange={(e) => setCode(e.target.value)} required autoFocus helperText="For example OPTICAL_RETAIL" />
      <TextField label="Name" value={name} onChange={(e) => setName(e.target.value)} required />
      <TextField select label="Category" value={category} onChange={(e) => setCategory(e.target.value)}>
        {CATEGORIES.map((c) => <MenuItem key={c} value={c}>{label(c)}</MenuItem>)}
      </TextField>
    </FormDialog>
  );
}
