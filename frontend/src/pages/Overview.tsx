import { Alert, Box, Chip, Grid, Paper, Stack, Typography } from '@mui/material';
import { Link as RouterLink } from 'react-router';

import { useSession } from '../auth/session';
import { PageHeader } from '../components/PageHeader';
import { visibleItems } from '../layout/navigation';
import { MyPositions } from './clinic/common';

const DESK_HINTS: Record<string, string> = {
  '/reception': 'Register patients, appointments, check-in, bills and checkout',
  '/optometry': 'Vision, refraction and eye pressure for the waiting patients',
  '/consultation': 'Examine, diagnose, prescribe and advise surgery',
  '/counselling': 'Surgery packages, costs, decisions and OT dates',
  '/ot': 'Today’s OT list, pre-operative checklist and operative notes',
  '/accounts': 'Collections, outstanding, discounts, day close and prices',
  '/patients': 'Find any patient by name, mobile or UHID',
};

export function Overview() {
  const { me } = useSession();
  const centres = me.centres ?? [];
  const session = useSession();
  const desks = visibleItems(session.hasAnywhere).filter((i) => i.group === 'Clinic');
  const showAdmin = (me.tenantPermissions ?? []).length > 0 || (me.pendingApprovals ?? 0) > 0 || desks.length === 0;
  return (
    <>
      <PageHeader title={`Welcome, ${me.displayName ?? me.username}`} subtitle={me.tenantName} />
      <Box sx={{ mb: 2 }}><MyPositions /></Box>
      {desks.length > 0 && (
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr', lg: 'repeat(4, 1fr)' }, gap: 1.5, mb: 2 }}>
          {desks.map((d) => (
            <Paper key={d.to} component={RouterLink} to={d.to} sx={{ p: 2, textDecoration: 'none', color: 'inherit', borderLeft: 4, borderLeftColor: 'primary.main', '&:hover': { bgcolor: 'action.hover' } }}>
              <Typography variant="h3">{d.label}</Typography>
              <Typography variant="body2" color="text.secondary">{DESK_HINTS[d.to]}</Typography>
            </Paper>
          ))}
        </Box>
      )}
      {me.demoTenant && (
        <Alert severity="info" sx={{ mb: 2 }}>
          This is a demonstration environment with synthetic people and centres. Do not enter real patient or staff information.
        </Alert>
      )}
      <Grid container spacing={2}>
        {showAdmin && (
          <>
        <Grid size={{ xs: 12, md: 4 }}>
          <Paper sx={{ p: 2 }}>
            <Typography variant="h3" gutterBottom>Pending approvals</Typography>
            <Typography variant="h1">{me.pendingApprovals ?? 0}</Typography>
            {(me.pendingApprovals ?? 0) > 0 && <RouterLink to="/approvals">Review requests</RouterLink>}
          </Paper>
        </Grid>
        <Grid size={{ xs: 12, md: 8 }}>
          <Paper sx={{ p: 2 }}>
            <Typography variant="h3" gutterBottom>Tenant-wide permissions</Typography>
            {(me.tenantPermissions ?? []).length === 0 ? (
              <Typography variant="body2" color="text.secondary">None. Your access is limited to the centres below.</Typography>
            ) : (
              <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 0.5 }}>
                {(me.tenantPermissions ?? []).map((p) => <Chip key={p} label={p} variant="outlined" />)}
              </Stack>
            )}
          </Paper>
        </Grid>
          </>
        )}
        <Grid size={12}>
          <Paper sx={{ p: 2 }}>
            <Typography variant="h3" gutterBottom>Your centres ({centres.length})</Typography>
            {centres.length === 0 && <Typography variant="body2" color="text.secondary">No centre-level access.</Typography>}
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1fr 1fr', lg: '1fr 1fr 1fr' }, gap: 1 }}>
              {centres.slice(0, 30).map((c) => (
                <Paper key={c.centreId} sx={{ p: 1.5 }}>
                  <RouterLink to={`/centres/${c.centreId}`}>{c.name}</RouterLink>
                  <Typography variant="caption" color="text.secondary" component="div">
                    {c.code} · {(c.permissions ?? []).length} permissions
                  </Typography>
                </Paper>
              ))}
            </Box>
          </Paper>
        </Grid>
        {(me.responsibilities ?? []).length > 0 && (
          <Grid size={12}>
            <Paper sx={{ p: 2 }}>
              <Typography variant="h3" gutterBottom>Your responsibilities</Typography>
              <Typography variant="body2" color="text.secondary" gutterBottom>Accountability only; they do not give access.</Typography>
              {(me.responsibilities ?? []).map((r) => (
                <Chip key={r.id} label={`${r.name} · ${r.scopeName}`} sx={{ mr: 0.5 }} />
              ))}
            </Paper>
          </Grid>
        )}
      </Grid>
    </>
  );
}
