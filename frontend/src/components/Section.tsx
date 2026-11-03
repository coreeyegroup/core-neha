import { Box, Paper, Stack, Typography } from '@mui/material';
import type { ReactNode } from 'react';

export function Section({ title, actions, children, description }: { title: string; actions?: ReactNode; children: ReactNode; description?: ReactNode }) {
  return (
    <Box sx={{ mb: 3 }}>
      <Stack direction="row" sx={{ mb: 1, alignItems: 'center', justifyContent: 'space-between' }}>
        <Box>
          <Typography variant="h3">{title}</Typography>
          {description && <Typography variant="body2" color="text.secondary">{description}</Typography>}
        </Box>
        {actions && <Stack direction="row" spacing={1}>{actions}</Stack>}
      </Stack>
      {children}
    </Box>
  );
}

export function KeyValue({ items }: { items: Array<[string, ReactNode]> }) {
  return (
    <Paper sx={{ p: 2 }}>
      <Box component="dl" sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '180px 1fr' }, columnGap: 2, rowGap: 1, m: 0 }}>
        {items.map(([k, v]) => (
          <Box key={k} sx={{ display: 'contents' }}>
            <Typography component="dt" variant="body2" color="text.secondary">{k}</Typography>
            <Typography component="dd" variant="body2" sx={{ m: 0 }}>{v ?? '—'}</Typography>
          </Box>
        ))}
      </Box>
    </Paper>
  );
}
