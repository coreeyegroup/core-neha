import { Alert, Box, Button, MenuItem, Tab, Tabs, TextField, Typography } from '@mui/material';
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
import { KeyValue } from '../components/Section';
import { label } from './Departments';

export function CentreWorkspace() {
  const { centreId = '' } = useParams();
  const session = useSession();
  const centre = useQuery({ queryKey: ['centre', centreId], queryFn: () => api.centre(centreId) });
  const [tab, setTab] = useState('departments');
  if (centre.error) {
    return <ProblemAlert error={centre.error} />;
  }
  const c = centre.data;
  const canSeeWorkforce = session.hasAtCentre('workforce.read', centreId);
  const tabs = [
    { key: 'departments', label: 'Departments' },
    ...(canSeeWorkforce ? [{ key: 'positions', label: 'Positions established' }] : []),
    { key: 'details', label: 'Details' },
  ];
  return (
    <>
      <PageHeader title={c?.name ?? 'Centre'} subtitle={c ? `${c.code} · ${c.centreType} · ${c.nodeName}` : undefined} />
      <Tabs value={tab} onChange={(_, v: string) => setTab(v)} sx={{ mb: 2, borderBottom: 1, borderColor: 'divider' }}>
        {tabs.map((t) => <Tab key={t.key} value={t.key} label={t.label} />)}
      </Tabs>
      {tab === 'departments' && <CentreDepartments centreId={centreId} canConfigure={session.hasAtCentre('org.centre.configure', centreId)} />}
      {tab === 'positions' && canSeeWorkforce && (
        <Establishments centreId={centreId} canEstablish={session.hasAtCentre('workforce.establish', centreId)} />
      )}
      {tab === 'details' && c && (
        <KeyValue items={[['Code', c.code], ['Type', c.centreType], ['Location', c.nodeName], ['Legal entity', c.legalEntityName], ['Status', c.status], ['Created', c.createdAt?.slice(0, 10)]]} />
      )}
    </>
  );
}

function CentreDepartments({ centreId, canConfigure }: { centreId: string; canConfigure: boolean }) {
  const [showRetired, setShowRetired] = useState(false);
  const list = useQuery({ queryKey: ['centre-departments', centreId, showRetired], queryFn: () => api.centreDepartments(centreId, { includeRetired: showRetired }) });
  const [enable, setEnable] = useState(false);
  const [retiring, setRetiring] = useState<api.CentreDepartmentView | undefined>();
  return (
    <>
      <Box sx={{ display: 'flex', gap: 1, mb: 1 }}>
        {canConfigure && <Button variant="contained" onClick={() => setEnable(true)}>Enable department</Button>}
        <Button onClick={() => setShowRetired(!showRetired)}>{showRetired ? 'Hide retired' : 'Show retired'}</Button>
      </Box>
      <DataTable
        rows={list.data}
        loading={list.isLoading}
        rowKey={(r) => r.id ?? ''}
        empty="No departments enabled at this centre."
        columns={[
          { key: 'name', header: 'Department', render: (r) => r.localName ?? r.departmentName, value: (r) => r.departmentName },
          { key: 'category', header: 'Category', render: (r) => label(r.category), value: (r) => r.category },
          { key: 'from', header: 'Enabled from', render: (r) => r.validFrom, value: (r) => r.validFrom },
          { key: 'to', header: 'Retired from', render: (r) => r.validTo ?? '—', value: (r) => r.validTo },
          {
            key: 'actions', header: '', align: 'right',
            render: (r) => canConfigure && !r.validTo ? <Button color="warning" onClick={(e) => { e.stopPropagation(); setRetiring(r); }}>Retire…</Button> : null,
          },
        ]}
      />
      {enable && <EnableDepartment centreId={centreId} enabled={list.data ?? []} onClose={() => setEnable(false)} />}
      {retiring && <RetireDepartment centreId={centreId} department={retiring} onClose={() => setRetiring(undefined)} />}
    </>
  );
}

function EnableDepartment({ centreId, enabled, onClose }: { centreId: string; enabled: api.CentreDepartmentView[]; onClose: () => void }) {
  const library = useQuery({ queryKey: ['departments'], queryFn: () => api.departments() });
  const open = new Set(enabled.filter((e) => !e.validTo).map((e) => e.departmentId));
  const [departmentId, setDepartmentId] = useState('');
  const [localName, setLocalName] = useState('');
  const [validFrom, setValidFrom] = useState('');
  const action = useAction((b: api.EnableDepartment) => api.enableDepartment(centreId, b), {
    invalidate: [['centre-departments', centreId]], success: 'Department enabled at this centre', onDone: onClose,
  });
  return (
    <FormDialog open title="Enable department at this centre" submitLabel="Enable" onClose={onClose} busy={action.isPending} error={action.error}
      onSubmit={() => action.mutate({ departmentId, localName: localName || undefined, validFrom: validFrom || undefined })}>
      <TextField select label="Department from the group library" value={departmentId} onChange={(e) => setDepartmentId(e.target.value)} required>
        {(library.data ?? []).filter((d) => d.status === 'ACTIVE' && !open.has(d.id)).map((d) => (
          <MenuItem key={d.id} value={d.id}>{d.name}</MenuItem>
        ))}
      </TextField>
      <TextField label="Local name (optional)" value={localName} onChange={(e) => setLocalName(e.target.value)} />
      <TextField label="Effective from" type="date" value={validFrom} onChange={(e) => setValidFrom(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} helperText="Leave empty for today" />
    </FormDialog>
  );
}

function RetireDepartment({ centreId, department, onClose }: { centreId: string; department: api.CentreDepartmentView; onClose: () => void }) {
  const impact = useQuery({ queryKey: ['retire-impact', department.id], queryFn: () => api.retireImpact(centreId, department.id ?? '') });
  const [validTo, setValidTo] = useState(new Date().toISOString().slice(0, 10));
  const action = useAction((b: api.RetireDepartment) => api.retire(centreId, department.id ?? '', b), {
    invalidate: [['centre-departments', centreId]], success: 'Department retired; history is kept', onDone: onClose,
  });
  const blockers = impact.data?.blockingReasons ?? [];
  return (
    <FormDialog open title={`Retire ${department.departmentName}`} submitLabel="Retire department" onClose={onClose} busy={action.isPending} error={action.error}
      onSubmit={() => action.mutate({ validTo, expectedVersion: department.version ?? 0 })}
      intro={
        impact.isLoading ? <Typography variant="body2">Checking active work…</Typography> :
        blockers.length > 0 ? (
          <Alert severity="warning">Active work must be ended first: {blockers.join('; ')}.</Alert>
        ) : (
          <Alert severity="info">No active work found. Retiring closes new use from the date below and keeps all history.</Alert>
        )
      }>
      <TextField label="Retire from" type="date" value={validTo} onChange={(e) => setValidTo(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
    </FormDialog>
  );
}

function Establishments({ centreId, canEstablish }: { centreId: string; canEstablish: boolean }) {
  const list = useQuery({ queryKey: ['establishments', centreId], queryFn: () => api.establishments(centreId) });
  const [open, setOpen] = useState(false);
  return (
    <>
      {canEstablish && <Button variant="contained" sx={{ mb: 1 }} onClick={() => setOpen(true)}>Establish position</Button>}
      <DataTable
        rows={list.data}
        loading={list.isLoading}
        rowKey={(r) => r.id ?? ''}
        empty="No positions established at this centre."
        columns={[
          { key: 'title', header: 'Position', render: (r) => r.positionTitle, value: (r) => r.positionTitle },
          { key: 'dept', header: 'Department', render: (r) => r.departmentName ?? '—', value: (r) => r.departmentName },
          { key: 'filled', header: 'Filled / approved', align: 'right', render: (r) => `${r.activePostings ?? 0} / ${r.approvedHeadcount ?? 0}`, value: (r) => r.activePostings },
          { key: 'from', header: 'From', render: (r) => r.validFrom, value: (r) => r.validFrom },
        ]}
      />
      {open && <Establish centreId={centreId} onClose={() => setOpen(false)} />}
    </>
  );
}

function Establish({ centreId, onClose }: { centreId: string; onClose: () => void }) {
  const positions = useQuery({ queryKey: ['positions'], queryFn: () => api.positions() });
  const depts = useQuery({ queryKey: ['centre-departments', centreId, false], queryFn: () => api.centreDepartments(centreId, { includeRetired: false }) });
  const [positionId, setPositionId] = useState('');
  const [centreDepartmentId, setDept] = useState('');
  const [headcount, setHeadcount] = useState('1');
  const action = useAction((b: api.Establish) => api.establish(centreId, b), {
    invalidate: [['establishments', centreId]], success: 'Position established', onDone: onClose,
  });
  return (
    <FormDialog open title="Establish a position at this centre" submitLabel="Establish" onClose={onClose} busy={action.isPending} error={action.error}
      intro={<Typography variant="body2" color="text.secondary">Only departments enabled at this centre are offered. A position is a job; it gives no system access.</Typography>}
      onSubmit={() => action.mutate({ positionId, centreDepartmentId: centreDepartmentId || undefined, approvedHeadcount: Number(headcount) })}>
      <TextField select label="Position" value={positionId} onChange={(e) => setPositionId(e.target.value)} required>
        {(positions.data ?? []).filter((p) => p.status === 'ACTIVE').map((p) => <MenuItem key={p.id} value={p.id}>{p.title}</MenuItem>)}
      </TextField>
      <TextField select label="Department at this centre" value={centreDepartmentId} onChange={(e) => setDept(e.target.value)}>
        <MenuItem value="">Not tied to a department</MenuItem>
        {(depts.data ?? []).map((d) => <MenuItem key={d.id} value={d.id}>{d.localName ?? d.departmentName}</MenuItem>)}
      </TextField>
      <TextField label="Approved headcount" type="number" value={headcount} onChange={(e) => setHeadcount(e.target.value)} required />
    </FormDialog>
  );
}
