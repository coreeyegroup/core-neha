import { Button, Grid, MenuItem, TextField, Typography } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';

import * as api from '../api/generated/core-neha';
import { useAction } from '../api/useAction';
import { useSession } from '../auth/session';
import { DataTable } from '../components/DataTable';
import { FormDialog } from '../components/FormDialog';
import { PageHeader } from '../components/PageHeader';
import { Section } from '../components/Section';
import { StatusChip } from '../components/StatusChip';

export function Organisation() {
  const session = useSession();
  const canManage = session.hasTenantWide('org.structure.manage');
  const levels = useQuery({ queryKey: ['levels'], queryFn: () => api.levels() });
  const nodes = useQuery({ queryKey: ['nodes'], queryFn: () => api.nodes() });
  const entities = useQuery({ queryKey: ['legal-entities'], queryFn: () => api.legalEntities() });
  const [dialog, setDialog] = useState<'level' | 'node' | 'entity' | undefined>();

  const depth = (path?: string) => Math.max(0, (path ?? '/').split('/').filter(Boolean).length - 1);
  const sortedNodes = [...(nodes.data ?? [])].sort((a, b) => (a.path ?? '').localeCompare(b.path ?? ''));

  return (
    <>
      <PageHeader
        title="Hierarchy and legal entities"
        subtitle="Levels are defined by this group (not a fixed State/City/Area set). Centres attach to a node; access given at a node covers everything below it."
      />
      <Grid container spacing={3}>
        <Grid size={{ xs: 12, lg: 5 }}>
          <Section title="Hierarchy levels" actions={canManage && <Button onClick={() => setDialog('level')}>Add level</Button>}>
            <DataTable
              rows={levels.data}
              loading={levels.isLoading}
              rowKey={(r) => r.id ?? ''}
              columns={[
                { key: 'rank', header: 'Rank', render: (r) => r.rank, value: (r) => r.rank, width: 60 },
                { key: 'code', header: 'Code', render: (r) => r.code, value: (r) => r.code },
                { key: 'name', header: 'Name', render: (r) => r.name, value: (r) => r.name },
                { key: 'status', header: 'Status', render: (r) => <StatusChip status={r.status} /> },
              ]}
            />
          </Section>
          <Section title="Legal entities" actions={canManage && <Button onClick={() => setDialog('entity')}>Add legal entity</Button>}>
            <DataTable
              rows={entities.data}
              loading={entities.isLoading}
              rowKey={(r) => r.id ?? ''}
              columns={[
                { key: 'code', header: 'Code', render: (r) => r.code, value: (r) => r.code },
                { key: 'name', header: 'Name', render: (r) => r.name, value: (r) => r.name },
                { key: 'status', header: 'Status', render: (r) => <StatusChip status={r.status} /> },
              ]}
            />
          </Section>
        </Grid>
        <Grid size={{ xs: 12, lg: 7 }}>
          <Section title="Hierarchy" actions={canManage && <Button onClick={() => setDialog('node')}>Add node</Button>}>
            <DataTable
              rows={sortedNodes}
              loading={nodes.isLoading}
              rowKey={(r) => r.id ?? ''}
              filterPlaceholder="Filter nodes"
              columns={[
                {
                  key: 'name',
                  header: 'Node',
                  render: (r) => <span style={{ paddingLeft: depth(r.path) * 20 }}>{r.name}</span>,
                  value: (r) => r.name,
                },
                { key: 'level', header: 'Level', render: (r) => r.levelCode, value: (r) => r.levelCode },
                { key: 'code', header: 'Code', render: (r) => r.code, value: (r) => r.code },
              ]}
            />
          </Section>
        </Grid>
      </Grid>
      {dialog === 'level' && <LevelDialog onClose={() => setDialog(undefined)} />}
      {dialog === 'entity' && <EntityDialog onClose={() => setDialog(undefined)} />}
      {dialog === 'node' && <NodeDialog onClose={() => setDialog(undefined)} levels={levels.data ?? []} nodes={sortedNodes} />}
    </>
  );
}

function LevelDialog({ onClose }: { onClose: () => void }) {
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [rank, setRank] = useState('');
  const action = useAction((b: api.CreateLevel) => api.createLevel(b), { invalidate: [['levels']], success: 'Level added', onDone: onClose });
  return (
    <FormDialog open title="Add hierarchy level" submitLabel="Add level" onClose={onClose} busy={action.isPending} error={action.error}
      onSubmit={() => action.mutate({ code: code.trim().toUpperCase(), name, rank: Number(rank) })}
      intro={<Typography variant="body2" color="text.secondary">Rank 1 is the top level (for example STATE); higher ranks sit below it.</Typography>}>
      <TextField label="Code" value={code} onChange={(e) => setCode(e.target.value)} required helperText="Capitals, for example STATE or ZONE" autoFocus />
      <TextField label="Name" value={name} onChange={(e) => setName(e.target.value)} required />
      <TextField label="Rank" type="number" value={rank} onChange={(e) => setRank(e.target.value)} required />
    </FormDialog>
  );
}

function EntityDialog({ onClose }: { onClose: () => void }) {
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const action = useAction((b: api.CreateLegalEntity) => api.createLegalEntity(b), { invalidate: [['legal-entities']], success: 'Legal entity added', onDone: onClose });
  return (
    <FormDialog open title="Add legal entity" submitLabel="Add legal entity" onClose={onClose} busy={action.isPending} error={action.error}
      onSubmit={() => action.mutate({ code: code.trim(), name })}>
      <TextField label="Code" value={code} onChange={(e) => setCode(e.target.value)} required autoFocus />
      <TextField label="Registered name" value={name} onChange={(e) => setName(e.target.value)} required />
    </FormDialog>
  );
}

function NodeDialog({ onClose, levels, nodes }: { onClose: () => void; levels: api.LevelView[]; nodes: api.NodeView[] }) {
  const [levelId, setLevelId] = useState('');
  const [parentId, setParentId] = useState('');
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const action = useAction((b: api.CreateNode) => api.createNode(b), { invalidate: [['nodes']], success: 'Node added', onDone: onClose });
  return (
    <FormDialog open title="Add hierarchy node" submitLabel="Add node" onClose={onClose} busy={action.isPending} error={action.error}
      onSubmit={() => action.mutate({ levelId, parentId: parentId || undefined, code: code.trim(), name })}>
      <TextField select label="Level" value={levelId} onChange={(e) => setLevelId(e.target.value)} required>
        {levels.map((l) => <MenuItem key={l.id} value={l.id}>{l.rank}. {l.name}</MenuItem>)}
      </TextField>
      <TextField select label="Parent (leave empty for a top-level node)" value={parentId} onChange={(e) => setParentId(e.target.value)}>
        <MenuItem value="">None</MenuItem>
        {nodes.map((n) => <MenuItem key={n.id} value={n.id}>{n.name} ({n.levelCode})</MenuItem>)}
      </TextField>
      <TextField label="Code" value={code} onChange={(e) => setCode(e.target.value)} required />
      <TextField label="Name" value={name} onChange={(e) => setName(e.target.value)} required />
    </FormDialog>
  );
}
