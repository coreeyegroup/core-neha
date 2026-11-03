import { Autocomplete, Button, Chip, MenuItem, TextField } from '@mui/material';
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

export function Centres() {
  const session = useSession();
  const navigate = useNavigate();
  const list = useQuery({ queryKey: ['centres'], queryFn: () => api.centres() });
  const [open, setOpen] = useState(false);
  return (
    <>
      <PageHeader
        title="Centres"
        subtitle="Only centres you have access to are listed. Each centre runs only the departments enabled for it."
        actions={session.hasAnywhere('org.centre.create') && <Button variant="contained" onClick={() => setOpen(true)}>Create centre</Button>}
      />
      <DataTable
        rows={list.data}
        loading={list.isLoading}
        rowKey={(r) => r.id ?? ''}
        filterPlaceholder="Filter by name, code, type or location"
        onRowClick={(r) => navigate(`/centres/${r.id}`)}
        columns={[
          { key: 'name', header: 'Centre', render: (r) => r.name, value: (r) => r.name },
          { key: 'code', header: 'Code', render: (r) => r.code, value: (r) => r.code },
          { key: 'type', header: 'Type', render: (r) => r.centreType, value: (r) => r.centreType },
          { key: 'node', header: 'Location', render: (r) => r.nodeName, value: (r) => r.nodeName },
          { key: 'entity', header: 'Legal entity', render: (r) => r.legalEntityName, value: (r) => r.legalEntityName },
          { key: 'status', header: 'Status', render: (r) => <StatusChip status={r.status} /> },
        ]}
      />
      {open && <CreateCentre onClose={() => setOpen(false)} onCreated={(id) => navigate(`/centres/${id}`)} />}
    </>
  );
}

function CreateCentre({ onClose, onCreated }: { onClose: () => void; onCreated: (id: string) => void }) {
  const nodes = useQuery({ queryKey: ['nodes'], queryFn: () => api.nodes() });
  const entities = useQuery({ queryKey: ['legal-entities'], queryFn: () => api.legalEntities() });
  const departments = useQuery({ queryKey: ['departments'], queryFn: () => api.departments() });
  const [form, setForm] = useState({ nodeId: '', legalEntityId: '', code: '', name: '', centreType: '' });
  const [chosen, setChosen] = useState<api.DepartmentView[]>([]);
  const action = useAction((b: api.CreateCentre) => api.createCentre(b), {
    invalidate: [['centres']],
    success: (c) => `Centre ${c.name} created with ${chosen.length} department(s)`,
    onDone: (c) => c.id && onCreated(c.id),
  });
  const set = (k: keyof typeof form) => (e: { target: { value: string } }) => setForm({ ...form, [k]: e.target.value });
  return (
    <FormDialog open maxWidth="md" title="Create centre" submitLabel="Create centre" onClose={onClose} busy={action.isPending} error={action.error}
      onSubmit={() => action.mutate({ ...form, departmentIds: chosen.map((d) => d.id ?? '') })}>
      <TextField select label="Location (hierarchy node)" value={form.nodeId} onChange={set('nodeId')} required>
        {(nodes.data ?? []).map((n) => <MenuItem key={n.id} value={n.id}>{n.name} ({n.levelCode})</MenuItem>)}
      </TextField>
      <TextField select label="Legal entity" value={form.legalEntityId} onChange={set('legalEntityId')} required>
        {(entities.data ?? []).map((l) => <MenuItem key={l.id} value={l.id}>{l.name}</MenuItem>)}
      </TextField>
      <TextField label="Code" value={form.code} onChange={set('code')} required />
      <TextField label="Name" value={form.name} onChange={set('name')} required />
      <TextField label="Centre type" value={form.centreType} onChange={set('centreType')} required helperText="Free classification, for example EYE_CLINIC or OPTICAL_LAB" />
      <Autocomplete
        multiple
        options={(departments.data ?? []).filter((d) => d.status === 'ACTIVE')}
        getOptionLabel={(d) => d.name ?? ''}
        value={chosen}
        onChange={(_, v) => setChosen(v)}
        renderValue={(value, getItemProps) => value.map((d, i) => <Chip {...getItemProps({ index: i })} key={d.id} label={d.name} />)}
        renderInput={(params) => <TextField {...params} label="Departments to enable now" helperText="You can enable more later" />}
      />
    </FormDialog>
  );
}
