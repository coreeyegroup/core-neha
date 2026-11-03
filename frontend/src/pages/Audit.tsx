import { Box, Button, MenuItem, Stack, TextField, Tooltip } from '@mui/material';
import { useInfiniteQuery } from '@tanstack/react-query';
import { useState } from 'react';

import * as api from '../api/generated/core-neha';
import { DataTable } from '../components/DataTable';
import { PageHeader } from '../components/PageHeader';
import { ProblemAlert } from '../components/ProblemAlert';
import { StatusChip } from '../components/StatusChip';

export function Audit() {
  const [outcome, setOutcome] = useState('');
  const [action, setAction] = useState('');
  const [objectType, setObjectType] = useState('');
  const filters = { outcome: outcome || undefined, action: action || undefined, objectType: objectType || undefined };
  const query = useInfiniteQuery({
    queryKey: ['audit', filters],
    initialPageParam: undefined as number | undefined,
    queryFn: ({ pageParam }) => api.search({ ...filters, beforeId: pageParam, limit: 100 }),
    getNextPageParam: (last) => (last.length === 100 ? last[last.length - 1]?.id : undefined),
  });
  const rows = query.data?.pages.flat() ?? [];
  return (
    <>
      <PageHeader title="Audit trail" subtitle="Who did what, to which object, where, and with what outcome. Records cannot be edited." />
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={1} sx={{ mb: 1 }}>
        <TextField select label="Outcome" value={outcome} onChange={(e) => setOutcome(e.target.value)} sx={{ maxWidth: 180 }}>
          <MenuItem value="">All</MenuItem>
          <MenuItem value="SUCCESS">Success</MenuItem>
          <MenuItem value="DENIED">Denied</MenuItem>
          <MenuItem value="FAILED">Failed</MenuItem>
        </TextField>
        <TextField label="Action (exact)" value={action} onChange={(e) => setAction(e.target.value)} placeholder="e.g. iam.assignment.approve" sx={{ maxWidth: 280 }} />
        <TextField label="Object type (exact)" value={objectType} onChange={(e) => setObjectType(e.target.value)} placeholder="e.g. centre" sx={{ maxWidth: 220 }} />
      </Stack>
      <ProblemAlert error={query.error} />
      <DataTable
        rows={rows}
        loading={query.isLoading}
        rowKey={(r) => String(r.id)}
        columns={[
          { key: 'time', header: 'When (UTC)', render: (r) => r.occurredAt?.replace('T', ' ').slice(0, 19), value: (r) => r.id },
          { key: 'actor', header: 'Actor', render: (r) => r.actorLabel, value: (r) => r.actorLabel },
          { key: 'action', header: 'Action', render: (r) => <code>{r.action}</code>, value: (r) => r.action },
          { key: 'object', header: 'Object', render: (r) => `${r.objectType}${r.objectId ? ` ${r.objectId.slice(0, 8)}…` : ''}`, value: (r) => r.objectType },
          { key: 'scope', header: 'Scope', render: (r) => (r.scopeType ? `${r.scopeType.toLowerCase()} ${r.scopeId?.slice(0, 8)}…` : '—') },
          { key: 'outcome', header: 'Outcome', render: (r) => <StatusChip status={r.outcome} />, value: (r) => r.outcome },
          { key: 'reason', header: 'Reason', render: (r) => (r.reason ? <Tooltip title={r.reason}><span>{r.reason.slice(0, 40)}{r.reason.length > 40 ? '…' : ''}</span></Tooltip> : '') },
        ]}
      />
      <Box sx={{ mt: 1 }}>
        {query.hasNextPage && <Button onClick={() => query.fetchNextPage()} disabled={query.isFetchingNextPage}>Load older events</Button>}
      </Box>
    </>
  );
}
