import { Button, Chip, TextField } from '@mui/material';
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

export function People() {
  const session = useSession();
  const navigate = useNavigate();
  const list = useQuery({ queryKey: ['people'], queryFn: () => api.people() });
  const [open, setOpen] = useState(false);
  return (
    <>
      <PageHeader
        title="People and postings"
        subtitle="People you can see through your centres. One person can hold several postings with their own dates."
        actions={session.hasAnywhere('workforce.person.manage') && <Button variant="contained" onClick={() => setOpen(true)}>Add person</Button>}
      />
      <DataTable
        rows={list.data}
        loading={list.isLoading}
        rowKey={(r) => r.id ?? ''}
        filterPlaceholder="Filter by name or email"
        onRowClick={(r) => navigate(`/people/${r.id}`)}
        columns={[
          { key: 'no', header: 'Employee no.', render: (r) => r.employeeNo, value: (r) => r.employeeNo },
          { key: 'name', header: 'Name', render: (r) => `${r.givenName} ${r.familyName}`, value: (r) => `${r.familyName} ${r.givenName}` },
          { key: 'email', header: 'Email', render: (r) => r.email, value: (r) => r.email },
          { key: 'demo', header: '', render: (r) => (r.demo ? <Chip label="Synthetic" variant="outlined" color="secondary" /> : null) },
          { key: 'status', header: 'Status', render: (r) => <StatusChip status={r.status} /> },
        ]}
      />
      {open && <AddPerson onClose={() => setOpen(false)} onCreated={(id) => navigate(`/people/${id}`)} />}
    </>
  );
}

function AddPerson({ onClose, onCreated }: { onClose: () => void; onCreated: (id: string) => void }) {
  const [form, setForm] = useState({ givenName: '', familyName: '', email: '' });
  const action = useAction((b: api.CreatePerson) => api.createPerson(b), {
    invalidate: [['people']], success: 'Person added. Add a posting next.', onDone: (p) => p.id && onCreated(p.id),
  });
  const set = (k: keyof typeof form) => (e: { target: { value: string } }) => setForm({ ...form, [k]: e.target.value });
  return (
    <FormDialog open title="Add person" submitLabel="Add person" onClose={onClose} busy={action.isPending} error={action.error} onSubmit={() => action.mutate(form)}>
      <TextField label="Given name" value={form.givenName} onChange={set('givenName')} required autoFocus />
      <TextField label="Family name" value={form.familyName} onChange={set('familyName')} required />
      <TextField label="Work email" type="email" value={form.email} onChange={set('email')} required />
    </FormDialog>
  );
}
