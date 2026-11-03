import { MenuItem, TextField } from '@mui/material';
import { useQuery } from '@tanstack/react-query';

import * as api from '../api/generated/core-neha';
import { useSession } from '../auth/session';

export interface ScopeValue {
  scopeType: string;
  scopeId: string;
}

/** Choose where something applies: one centre, a hierarchy node and everything below it, or the whole tenant. */
export function ScopeFields({ value, onChange }: { value: ScopeValue; onChange: (v: ScopeValue) => void }) {
  const session = useSession();
  const nodes = useQuery({ queryKey: ['nodes'], queryFn: () => api.nodes(), enabled: value.scopeType === 'ORG_NODE' });
  const centres = useQuery({ queryKey: ['centres'], queryFn: () => api.centres(), enabled: value.scopeType === 'CENTRE' });
  return (
    <>
      <TextField select label="Applies to" value={value.scopeType}
        onChange={(e) => onChange({ scopeType: e.target.value, scopeId: e.target.value === 'TENANT' ? session.me.tenantId ?? '' : '' })}>
        <MenuItem value="CENTRE">One centre</MenuItem>
        <MenuItem value="ORG_NODE">A hierarchy node and everything below it</MenuItem>
        <MenuItem value="TENANT">The whole group</MenuItem>
      </TextField>
      {value.scopeType === 'CENTRE' && (
        <TextField select label="Centre" value={value.scopeId} onChange={(e) => onChange({ ...value, scopeId: e.target.value })} required>
          {(centres.data ?? []).map((c) => <MenuItem key={c.id} value={c.id}>{c.name}</MenuItem>)}
        </TextField>
      )}
      {value.scopeType === 'ORG_NODE' && (
        <TextField select label="Hierarchy node" value={value.scopeId} onChange={(e) => onChange({ ...value, scopeId: e.target.value })} required>
          {(nodes.data ?? []).map((n) => <MenuItem key={n.id} value={n.id}>{n.name} ({n.levelCode})</MenuItem>)}
        </TextField>
      )}
    </>
  );
}
