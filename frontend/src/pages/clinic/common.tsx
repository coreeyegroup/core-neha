import { Alert, Box, Chip, Paper, Stack, Step, StepLabel, Stepper, Typography, type ChipProps } from '@mui/material';
import type { ReactNode } from 'react';

import { useSession } from '../../auth/session';

/** Today's date in India Standard Time (business days are IST days), as YYYY-MM-DD. */
export function todayIst(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Kolkata' }).format(new Date());
}

export function addDays(date: string, days: number): string {
  const d = new Date(`${date}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}

const rupees = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2 });

export function money(n: number | undefined | null): string {
  return rupees.format(n ?? 0);
}

export function hhmm(t: string | undefined | null): string {
  return t ? t.slice(0, 5) : '—';
}

export function clock(ts: string | undefined | null): string {
  if (!ts) {
    return '—';
  }
  return new Intl.DateTimeFormat('en-IN', { timeZone: 'Asia/Kolkata', hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(ts));
}

export function dateLabel(d: string | undefined | null): string {
  if (!d) {
    return '—';
  }
  return new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' }).format(new Date(`${d}T00:00:00Z`));
}

export function waited(min: number | undefined | null): string {
  if (min === undefined || min === null) {
    return '—';
  }
  if (min < 60) {
    return `${min} min`;
  }
  return `${Math.floor(min / 60)} h ${min % 60} min`;
}

export function ageSex(age?: number | null, sex?: string | null): string {
  const s = sex ? sex.charAt(0) : '';
  return age === undefined || age === null ? s : `${age} ${s}`.trim();
}

export function eyeLabel(e?: string | null): string {
  return e === 'RE' ? 'Right eye' : e === 'LE' ? 'Left eye' : e === 'BE' ? 'Both eyes' : '—';
}

/** The working centre chosen in the top bar; clinic desks always work on one centre. */
export function useWorkingCentre(): { centreId: string | undefined; centreName: string | undefined } {
  const session = useSession();
  const c = (session.me.centres ?? []).find((x) => x.centreId === session.centreId);
  return { centreId: session.centreId, centreName: c?.name };
}

export function NeedPermission({ ok, children, what }: { ok: boolean; children: ReactNode; what: string }) {
  if (!ok) {
    return (
      <Alert severity="info">
        You do not have {what} at the working centre. Choose another working centre in the top bar, or ask an administrator for access.
      </Alert>
    );
  }
  return <>{children}</>;
}

export function StatCard({ label, value, hint, tone }: { label: string; value: ReactNode; hint?: ReactNode; tone?: 'warning' | 'success' | 'info' }) {
  return (
    <Paper sx={{ p: 1.5, minWidth: 0, borderLeft: 4, borderLeftColor: tone ? `${tone}.main` : 'divider' }}>
      <Typography variant="caption" color="text.secondary" component="div" sx={{ textTransform: 'uppercase', letterSpacing: 0.4 }}>
        {label}
      </Typography>
      <Typography variant="h2" component="div" sx={{ mt: 0.5 }}>{value}</Typography>
      {hint && <Typography variant="caption" color="text.secondary" component="div">{hint}</Typography>}
    </Paper>
  );
}

export function StatRow({ children }: { children: ReactNode }) {
  return (
    <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr 1fr', sm: 'repeat(3, 1fr)', lg: 'repeat(6, 1fr)' }, gap: 1.5, mb: 2 }}>
      {children}
    </Box>
  );
}

const STAGES: Record<string, { label: string; color: ChipProps['color'] }> = {
  OPTOMETRY: { label: 'Optometry', color: 'info' },
  CONSULTATION: { label: 'With doctor', color: 'primary' },
  COUNSELLING: { label: 'Counselling', color: 'secondary' },
  CHECKOUT: { label: 'Checkout', color: 'warning' },
  COMPLETED: { label: 'Completed', color: 'success' },
  CANCELLED: { label: 'Cancelled', color: 'default' },
};

export function StageChip({ stage }: { stage?: string }) {
  const s = STAGES[stage ?? ''] ?? { label: stage ?? '—', color: 'default' as const };
  return <Chip size="small" label={s.label} color={s.color} variant={stage === 'COMPLETED' || stage === 'CANCELLED' ? 'outlined' : 'filled'} />;
}

export function VisitStepper({ stage, surgeryAdvised }: { stage?: string; surgeryAdvised?: boolean }) {
  const steps = ['OPTOMETRY', 'CONSULTATION', ...(surgeryAdvised || stage === 'COUNSELLING' ? ['COUNSELLING'] : []), 'CHECKOUT', 'COMPLETED'];
  const active = stage === 'CANCELLED' ? -1 : steps.indexOf(stage ?? '');
  return (
    <Stepper activeStep={stage === 'COMPLETED' ? steps.length : active} alternativeLabel sx={{ my: 2 }}>
      {steps.map((s) => (
        <Step key={s}>
          <StepLabel>{STAGES[s]?.label ?? s}</StepLabel>
        </Step>
      ))}
    </Stepper>
  );
}

export function PatientName({ name, uhid, age, sex }: { name?: string; uhid?: string; age?: number | null; sex?: string }) {
  return (
    <Box>
      <Typography variant="body2" sx={{ fontWeight: 600 }}>{name}</Typography>
      <Typography variant="caption" color="text.secondary">{uhid} · {ageSex(age, sex)}</Typography>
    </Box>
  );
}

/** Where the signed-in person works: their positions and departments. */
export function MyPositions() {
  const { me } = useSession();
  const postings = me.postings ?? [];
  if (postings.length === 0) {
    return null;
  }
  return (
    <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 0.5 }}>
      {postings.map((p, i) => (
        <Chip key={i} variant="outlined" label={`${p.positionTitle}${p.departmentName ? ` · ${p.departmentName}` : ''} · ${p.centreName}`} />
      ))}
    </Stack>
  );
}
