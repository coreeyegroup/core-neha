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
import { label } from './Departments';

const CATEGORIES = ['CLINICAL', 'NURSING', 'ALLIED_HEALTH', 'TECHNICAL', 'ADMINISTRATIVE', 'MANAGEMENT', 'SUPPORT'];

export function Positions() {
  const session = useSession();
  const list = useQuery({ queryKey: ['positions'], queryFn: () => api.positions() });
  const [open, setOpen] = useState(false);
  return (
    <>
      <PageHeader
        title="Position library"
        subtitle="Jobs defined once for the group and established at selected centres. A position is not a permission role."
        actions={session.hasTenantWide('workforce.position.define') && <Button variant="contained" onClick={() => setOpen(true)}>Define position</Button>}
      />
      <DataTable
        rows={list.data}
        loading={list.isLoading}
        rowKey={(r) => r.id ?? ''}
        filterPlaceholder="Filter positions"
        columns={[
          { key: 'title', header: 'Position', render: (r) => r.title, value: (r) => r.title },
          { key: 'code', header: 'Code', render: (r) => r.code, value: (r) => r.code },
          { key: 'category', header: 'Category', render: (r) => label(r.category), value: (r) => r.category },
          { key: 'grade', header: 'Grade', render: (r) => r.grade ?? '—', value: (r) => r.grade },
          { key: 'status', header: 'Status', render: (r) => <StatusChip status={r.status} /> },
        ]}
      />
      {open && <DefinePosition onClose={() => setOpen(false)} />}
    </>
  );
}

function DefinePosition({ onClose }: { onClose: () => void }) {
  const [code, setCode] = useState('');
  const [title, setTitle] = useState('');
  const [category, setCategory] = useState('CLINICAL');
  const [grade, setGrade] = useState('');
  const action = useAction((b: api.DefinePosition) => api.definePosition(b), { invalidate: [['positions']], success: 'Position defined', onDone: onClose });
  return (
    <FormDialog open title="Define position" submitLabel="Define" onClose={onClose} busy={action.isPending} error={action.error}
      intro={<Typography variant="body2" color="text.secondary">Defining a position does not create it at any centre.</Typography>}
      onSubmit={() => action.mutate({ code: code.trim().toUpperCase(), title, category, grade: grade || undefined })}>
      <TextField label="Code" value={code} onChange={(e) => setCode(e.target.value)} required autoFocus helperText="For example OPTOMETRIST" />
      <TextField label="Title" value={title} onChange={(e) => setTitle(e.target.value)} required />
      <TextField select label="Category" value={category} onChange={(e) => setCategory(e.target.value)}>
        {CATEGORIES.map((c) => <MenuItem key={c} value={c}>{label(c)}</MenuItem>)}
      </TextField>
      <TextField label="Grade (optional)" value={grade} onChange={(e) => setGrade(e.target.value)} />
    </FormDialog>
  );
}
