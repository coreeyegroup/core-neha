import { Box, Checkbox, FormControlLabel, Paper, Typography } from '@mui/material';

import type { PermissionView } from '../api/generated/core-neha';

/** Permissions grouped by module, as checkboxes. Tenant-only permissions are labelled. */
export function PermissionPicker({ catalogue, value, onChange }: { catalogue: PermissionView[]; value: Set<string>; onChange: (v: Set<string>) => void }) {
  const modules = [...new Set(catalogue.map((p) => p.module ?? ''))];
  const toggle = (code: string) => {
    const next = new Set(value);
    if (next.has(code)) {
      next.delete(code);
    } else {
      next.add(code);
    }
    onChange(next);
  };
  return (
    <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' }, gap: 1 }}>
      {modules.map((m) => (
        <Paper key={m} sx={{ p: 1.5 }}>
          <Typography variant="body2" sx={{ fontWeight: 600, mb: 0.5 }}>{m}</Typography>
          {catalogue.filter((p) => p.module === m).map((p) => (
            <FormControlLabel
              key={p.code}
              sx={{ display: 'flex', alignItems: 'flex-start', mb: 0.5 }}
              control={<Checkbox size="small" checked={value.has(p.code ?? '')} onChange={() => toggle(p.code ?? '')} />}
              label={
                <span>
                  <code>{p.code}</code>
                  <Typography variant="caption" component="div" color="text.secondary">
                    {p.description}{p.tenantOnly ? ' (effective only group-wide)' : ''}
                  </Typography>
                </span>
              }
            />
          ))}
        </Paper>
      ))}
    </Box>
  );
}
