import { Box, FormControlLabel, Switch, Tab, Tabs, Typography } from '@mui/material';
import { useState } from 'react';

import { useSession } from '../../auth/session';
import { PageHeader } from '../../components/PageHeader';
import { ProblemAlert } from '../../components/ProblemAlert';
import { MyPositions, NeedPermission, StatCard, StatRow, useWorkingCentre, waited } from './common';
import { useBoard, useVisits, VisitTable } from './ReceptionDesk';

/** Optometry: the work-up queue (vision, refraction, eye pressure) before the doctor. */
export function OptometryDesk() {
  const session = useSession();
  const { centreId, centreName } = useWorkingCentre();
  const ok = session.hasAtCentre('eye.optometry.record', centreId);
  const [tab, setTab] = useState('waiting');
  const board = useBoard(centreId);
  const waiting = useVisits(ok ? centreId : undefined, 'OPTOMETRY');
  const doctor = useVisits(ok ? centreId : undefined, 'CONSULTATION');
  const all = useVisits(ok && tab === 'today' ? centreId : undefined);
  const b = board.data;
  const longest = Math.max(0, ...(waiting.data ?? []).map((v) => v.waitingMinutes ?? 0));
  return (
    <>
      <PageHeader title="Optometry" subtitle={<>{centreName} · vision, refraction and eye pressure before the consultation</>} />
      <MyPositions />
      <Box sx={{ mt: 2 }} />
      <NeedPermission ok={ok} what="optometry access">
        <StatRow>
          <StatCard label="Waiting for optometry" value={b?.waitingOptometry ?? '–'} tone="warning" hint={`longest ${waited(longest)}`} />
          <StatCard label="Sent to the doctor" value={b?.waitingConsultation ?? '–'} tone="info" />
          <StatCard label="Visits today" value={b?.visits ?? '–'} />
          <StatCard label="Completed today" value={b?.completed ?? '–'} tone="success" />
        </StatRow>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
          Open a patient, record vision for each eye, refraction and IOP, then choose <b>Complete and send to doctor</b>.
        </Typography>
        <Tabs value={tab} onChange={(_, v: string) => setTab(v)} sx={{ mb: 2, borderBottom: 1, borderColor: 'divider' }}>
          <Tab value="waiting" label={`My queue (${waiting.data?.length ?? 0})`} />
          <Tab value="doctor" label={`With the doctor (${doctor.data?.length ?? 0})`} />
          <Tab value="today" label="All today" />
        </Tabs>
        <ProblemAlert error={waiting.error ?? doctor.error} />
        {tab === 'waiting' && <VisitTable rows={waiting.data} loading={waiting.isLoading} empty="Nobody is waiting for optometry." />}
        {tab === 'doctor' && <VisitTable rows={doctor.data} loading={doctor.isLoading} empty="Nobody is with the doctor." />}
        {tab === 'today' && <VisitTable rows={all.data} loading={all.isLoading} empty="No visits today." />}
      </NeedPermission>
    </>
  );
}

/** Consultation: the doctor's queue, with optometry already done, and the day's surgery advice. */
export function ConsultationDesk() {
  const session = useSession();
  const { centreId, centreName } = useWorkingCentre();
  const ok = session.hasAtCentre('eye.consultation.record', centreId);
  const [tab, setTab] = useState('ready');
  const [mine, setMine] = useState(false);
  const board = useBoard(centreId);
  const ready = useVisits(ok ? centreId : undefined, 'CONSULTATION');
  const workup = useVisits(ok ? centreId : undefined, 'OPTOMETRY');
  const all = useVisits(ok && tab === 'today' ? centreId : undefined);
  const b = board.data;
  const me = session.me.displayName;
  const filter = <T extends { consultantName?: string }>(rows?: T[]) => (mine ? (rows ?? []).filter((r) => !r.consultantName || r.consultantName === me) : rows);
  return (
    <>
      <PageHeader title="Consultation" subtitle={<>{centreName} · examination, diagnosis, treatment and surgery advice</>} />
      <MyPositions />
      <Box sx={{ mt: 2 }} />
      <NeedPermission ok={ok} what="consultation access">
        <StatRow>
          <StatCard label="Ready for you" value={b?.waitingConsultation ?? '–'} tone="warning" hint="optometry done" />
          <StatCard label="In optometry" value={b?.waitingOptometry ?? '–'} tone="info" hint="coming next" />
          <StatCard label="Sent to counselling" value={b?.waitingCounselling ?? '–'} hint="surgery advised today" />
          <StatCard label="Surgeries advised (open)" value={b?.surgeriesAdvised ?? '–'} />
          <StatCard label="Surgeries today" value={b?.surgeriesToday ?? '–'} hint={`${b?.surgeriesCompletedToday ?? 0} done`} />
          <StatCard label="Completed visits" value={b?.completed ?? '–'} tone="success" />
        </StatRow>
        <FormControlLabel control={<Switch checked={mine} onChange={(e) => setMine(e.target.checked)} />} label="Only my patients (and unassigned)" />
        <Tabs value={tab} onChange={(_, v: string) => setTab(v)} sx={{ mb: 2, borderBottom: 1, borderColor: 'divider' }}>
          <Tab value="ready" label={`Ready for consultation (${filter(ready.data)?.length ?? 0})`} />
          <Tab value="workup" label={`In optometry (${filter(workup.data)?.length ?? 0})`} />
          <Tab value="today" label="All today" />
        </Tabs>
        <ProblemAlert error={ready.error ?? workup.error} />
        {tab === 'ready' && <VisitTable rows={filter(ready.data)} loading={ready.isLoading} empty="Nobody is waiting for you." />}
        {tab === 'workup' && <VisitTable rows={filter(workup.data)} loading={workup.isLoading} empty="Nobody is in optometry." />}
        {tab === 'today' && <VisitTable rows={filter(all.data)} loading={all.isLoading} empty="No visits today." />}
      </NeedPermission>
    </>
  );
}
