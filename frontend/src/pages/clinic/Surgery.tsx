import {
  Alert,
  Box,
  Button,
  Checkbox,
  FormControlLabel,
  Grid,
  MenuItem,
  Paper,
  Radio,
  RadioGroup,
  Stack,
  Tab,
  Tabs,
  TextField,
  Typography,
} from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link as RouterLink, useNavigate, useParams } from 'react-router';

import * as api from '../../api/generated/core-neha';
import { useAction } from '../../api/useAction';
import { useSession } from '../../auth/session';
import { DataTable } from '../../components/DataTable';
import { PageHeader } from '../../components/PageHeader';
import { ProblemAlert } from '../../components/ProblemAlert';
import { KeyValue, Section } from '../../components/Section';
import { StatusChip } from '../../components/StatusChip';
import { ageSex, clock, dateLabel, eyeLabel, hhmm, money, MyPositions, NeedPermission, PatientName, StatCard, StatRow, todayIst, useWorkingCentre } from './common';
import { CreateBillDialog, ReasonDialog, useClinicians, useTariff } from './dialogs';
import { useBoard } from './ReceptionDesk';

export function useSurgeries(centreId: string | undefined, view: string, date?: string) {
  return useQuery({ queryKey: ['surgeries', centreId, view, date], queryFn: () => api.surgeries(centreId!, { view, date }), enabled: !!centreId, refetchInterval: 30_000 });
}

export function SurgeryTable({ rows, loading, empty, showDate = true }: { rows?: api.SurgeryView[]; loading?: boolean; empty: string; showDate?: boolean }) {
  const navigate = useNavigate();
  return (
    <DataTable
      rows={rows}
      loading={loading}
      rowKey={(r) => r.id ?? ''}
      filterPlaceholder="Filter by name, UHID or procedure"
      empty={empty}
      onRowClick={(r) => navigate(`/surgeries/${r.id}`)}
      columns={[
        ...(showDate ? [{ key: 'date', header: 'Date', render: (r: api.SurgeryView) => (r.surgeryDate ? dateLabel(r.surgeryDate) : r.preferredDate ? `prefers ${dateLabel(r.preferredDate)}` : '—'), value: (r: api.SurgeryView) => r.surgeryDate ?? r.preferredDate }] : []),
        { key: 'time', header: 'Time', render: (r) => hhmm(r.slotTime), value: (r) => r.slotTime, width: 64 },
        { key: 'patient', header: 'Patient', render: (r) => <PatientName name={r.patientName} uhid={r.patientUhid} age={r.patientAge} sex={r.patientSex} />, value: (r) => `${r.patientName} ${r.patientUhid}` },
        { key: 'procedure', header: 'Procedure', render: (r) => <><Typography variant="body2">{r.procedureName}</Typography><Typography variant="caption" color="text.secondary">{eyeLabel(r.eye)} · {r.urgency?.toLowerCase()}</Typography></>, value: (r) => r.procedureName },
        { key: 'diagnosis', header: 'Diagnosis', render: (r) => r.diagnosis ?? '—' },
        { key: 'surgeon', header: 'Surgeon', render: (r) => r.surgeonName ?? '—', value: (r) => r.surgeonName },
        { key: 'package', header: 'Package', render: (r) => (r.packageName ? `${r.packageName} · ${money(r.estimatedCost)}` : '—') },
        { key: 'checklist', header: 'Checklist', render: (r) => (r.status === 'READY' || r.status === 'COMPLETED' ? 'Done' : r.consentSigned ? 'In progress' : '—') },
        { key: 'status', header: 'Status', render: (r) => <StatusChip status={r.status} />, value: (r) => r.status },
      ]}
    />
  );
}

/** Surgery counselling: patients advised surgery, packages and costs, the decision, and the OT date. */
export function CounsellingDesk() {
  const session = useSession();
  const { centreId, centreName } = useWorkingCentre();
  const ok = session.hasAtCentre('eye.counselling.record', centreId) || session.hasAtCentre('eye.surgery.schedule', centreId);
  const [tab, setTab] = useState('counsel');
  const board = useBoard(centreId);
  const counsel = useSurgeries(ok ? centreId : undefined, 'COUNSELLING');
  const schedule = useSurgeries(ok ? centreId : undefined, 'SCHEDULING');
  const upcoming = useSurgeries(ok ? centreId : undefined, 'UPCOMING', todayIst());
  const recent = useSurgeries(ok && tab === 'recent' ? centreId : undefined, 'RECENT');
  const b = board.data;
  return (
    <>
      <PageHeader title="Surgery counselling" subtitle={<>{centreName} · explain the surgery, package and cost, record the decision, book the OT date</>} />
      <MyPositions />
      <Box sx={{ mt: 2 }} />
      <NeedPermission ok={ok} what="counselling access">
        <StatRow>
          <StatCard label="Waiting for counselling" value={b?.waitingCounselling ?? '–'} tone="warning" hint="patients in the clinic now" />
          <StatCard label="Advised, not counselled" value={b?.surgeriesAdvised ?? '–'} />
          <StatCard label="Accepted, to schedule" value={b?.surgeriesToSchedule ?? '–'} tone="info" />
          <StatCard label="Surgeries today" value={b?.surgeriesToday ?? '–'} />
          <StatCard label="Upcoming" value={b?.surgeriesUpcoming ?? '–'} />
        </StatRow>
        <Tabs value={tab} onChange={(_, v: string) => setTab(v)} sx={{ mb: 2, borderBottom: 1, borderColor: 'divider' }} variant="scrollable">
          <Tab value="counsel" label={`To counsel (${counsel.data?.length ?? 0})`} />
          <Tab value="schedule" label={`To schedule (${schedule.data?.length ?? 0})`} />
          <Tab value="upcoming" label={`Booked (${upcoming.data?.length ?? 0})`} />
          <Tab value="recent" label="All recent" />
        </Tabs>
        <ProblemAlert error={counsel.error ?? schedule.error} />
        {tab === 'counsel' && <SurgeryTable rows={counsel.data} loading={counsel.isLoading} empty="Nobody is waiting for counselling." showDate={false} />}
        {tab === 'schedule' && <SurgeryTable rows={schedule.data} loading={schedule.isLoading} empty="No accepted surgery waits for a date." />}
        {tab === 'upcoming' && <SurgeryTable rows={upcoming.data} loading={upcoming.isLoading} empty="No surgeries booked." />}
        {tab === 'recent' && <SurgeryTable rows={recent.data} loading={recent.isLoading} empty="No surgeries yet." />}
      </NeedPermission>
    </>
  );
}

/** Operation theatre: the day's list, the pre-operative checklist and the operative note. */
export function OtDesk() {
  const session = useSession();
  const { centreId, centreName } = useWorkingCentre();
  const ok = session.hasAtCentre('eye.surgery.prepare', centreId) || session.hasAtCentre('eye.surgery.operate', centreId);
  const [date, setDate] = useState(todayIst());
  const list = useSurgeries(ok ? centreId : undefined, 'OT_LIST', date);
  const upcoming = useSurgeries(ok ? centreId : undefined, 'UPCOMING', todayIst());
  const rows = list.data ?? [];
  const count = (s: string) => rows.filter((r) => r.status === s).length;
  return (
    <>
      <PageHeader title="Operation theatre" subtitle={<>{centreName} · OT list, pre-operative checklist and operative notes</>} />
      <MyPositions />
      <Box sx={{ mt: 2 }} />
      <NeedPermission ok={ok} what="operation theatre access">
        <StatRow>
          <StatCard label="On the list" value={rows.length} />
          <StatCard label="Checklist pending" value={count('SCHEDULED')} tone="warning" hint="consent, marking, fitness" />
          <StatCard label="Ready for theatre" value={count('READY')} tone="info" />
          <StatCard label="Completed" value={count('COMPLETED')} tone="success" />
          <StatCard label="Booked ahead" value={(upcoming.data ?? []).filter((r) => r.surgeryDate !== todayIst()).length} />
        </StatRow>
        <TextField label="OT date" type="date" value={date} onChange={(e) => setDate(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} sx={{ mb: 1, width: 200 }} />
        <ProblemAlert error={list.error} />
        <SurgeryTable rows={list.data} loading={list.isLoading} empty="No surgeries on this day." showDate={false} />
        <Section title="Booked ahead">
          <SurgeryTable rows={(upcoming.data ?? []).filter((r) => r.surgeryDate !== todayIst())} loading={upcoming.isLoading} empty="Nothing booked after today." />
        </Section>
      </NeedPermission>
    </>
  );
}

// ---------------------------------------------------------------- one surgery

export function SurgeryPage() {
  const { surgeryId = '' } = useParams();
  const session = useSession();
  const s = useQuery({ queryKey: ['surgery', surgeryId], queryFn: () => api.surgery(surgeryId) });
  const bills = useQuery({ queryKey: ['surgery', surgeryId, 'bills'], queryFn: () => api.surgeryBills(surgeryId), enabled: !!s.data });
  const [dialog, setDialog] = useState<'cancel' | 'bill' | undefined>();
  if (s.error) {
    return <ProblemAlert error={s.error} />;
  }
  const x = s.data;
  if (!x) {
    return <Typography color="text.secondary">Loading…</Typography>;
  }
  const c = x.centreId;
  const closed = x.status === 'COMPLETED' || x.status === 'CANCELLED';
  return (
    <>
      <PageHeader title={`${x.procedureName} · ${eyeLabel(x.eye)}`}
        subtitle={<>{x.patientName} · {x.patientUhid} · {ageSex(x.patientAge, x.patientSex)} · {x.patientMobile ?? 'no mobile'}</>}
        actions={
          <>
            <Button component={RouterLink} to={`/visits/${x.visitId}`}>Visit</Button>
            <Button component={RouterLink} to={`/patients/${x.patientId}`}>Patient record</Button>
            {x.packageItemId && (bills.data ?? []).length === 0 && session.hasAtCentre('billing.charge', c) && !closed && (
              <Button onClick={() => setDialog('bill')}>Raise package bill</Button>
            )}
            {!closed && session.hasAtCentre('eye.surgery.schedule', c) && <Button color="warning" onClick={() => setDialog('cancel')}>Cancel surgery…</Button>}
          </>
        } />
      <Stack direction="row" spacing={1} sx={{ mb: 2, alignItems: 'center' }}>
        <StatusChip status={x.status} />
        <Typography variant="body2" color="text.secondary">Advised by {x.advisedByName ?? '—'} · {x.urgency?.toLowerCase()} · diagnosis: {x.diagnosis ?? '—'}</Typography>
      </Stack>
      {x.status === 'CANCELLED' && <Alert severity="info" sx={{ mb: 2 }}>Cancelled: {x.cancelReason}</Alert>}
      <Grid container spacing={2}>
        <Grid size={{ xs: 12, lg: 8 }}>
          <Counselling s={x} />
          <Schedule s={x} />
          <PreOp s={x} />
          <Operation s={x} />
        </Grid>
        <Grid size={{ xs: 12, lg: 4 }}>
          <Section title="Package bills">
            <Paper sx={{ p: 1.5 }}>
              {(bills.data ?? []).length === 0 && <Typography variant="body2" color="text.secondary">No package bill yet.</Typography>}
              {(bills.data ?? []).map((b) => (
                <Stack key={b.id} direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', py: 0.5 }}>
                  <RouterLink to={`/bills/${b.id}`}>{b.billNo}</RouterLink>
                  <Typography variant="body2">{money(b.paidAmount)} of {money(b.netAmount)}</Typography>
                  <StatusChip status={b.status} />
                </Stack>
              ))}
            </Paper>
          </Section>
        </Grid>
      </Grid>
      {dialog === 'cancel' && (
        <ReasonDialog title="Cancel this surgery" submitLabel="Cancel surgery" onClose={() => setDialog(undefined)} success="Surgery cancelled"
          run={(reason) => api.cancelSurgery(x.id!, { reason, expectedVersion: x.version ?? 0 })} />
      )}
      {dialog === 'bill' && (
        <CreateBillDialog centreId={c!} patientId={x.patientId!} patientName={x.patientName} surgeryId={x.id} packageItemId={x.packageItemId} onClose={() => setDialog(undefined)} />
      )}
    </>
  );
}

const invalidate = [['surgery'], ['surgeries'], ['visit'], ['visits'], ['clinic-board'], ['appointments']];

function Counselling({ s }: { s: api.SurgeryView }) {
  const session = useSession();
  const canEdit = session.hasAtCentre('eye.counselling.record', s.centreId) && ['ADVISED', 'ACCEPTED', 'DECLINED'].includes(s.status ?? '');
  const tariff = useTariff(canEdit ? s.centreId : undefined);
  const packages = (tariff.data ?? []).filter((t) => t.category === 'SURGERY_PACKAGE');
  const [f, setF] = useState({
    packageItemId: s.packageItemId ?? '', estimatedCost: s.estimatedCost?.toString() ?? '', paymentPlan: s.paymentPlan ?? 'SELF_PAY',
    insurerName: s.insurerName ?? '', notes: s.counsellingNotes ?? '', preferredDate: s.preferredDate ?? '',
    decision: s.status === 'ACCEPTED' ? 'ACCEPTED' : s.status === 'DECLINED' ? 'DECLINED' : 'ACCEPTED',
  });
  const save = useAction(() => api.counselSurgery(s.id!, {
    packageItemId: f.packageItemId || undefined, estimatedCost: f.estimatedCost ? Number(f.estimatedCost) : undefined, paymentPlan: f.paymentPlan || undefined,
    insurerName: f.insurerName || undefined, notes: f.notes || undefined, preferredDate: f.preferredDate || undefined, decision: f.decision, expectedVersion: s.version ?? 0,
  }), { invalidate, success: (r) => (r.status === 'ACCEPTED' ? 'Accepted; ready to schedule. The patient goes to checkout.' : 'Counselling recorded; the patient goes to checkout.') });
  if (!canEdit) {
    return s.counselledAt ? (
      <Section title="Counselling">
        <KeyValue items={[
          ['Package', s.packageName ? `${s.packageName} · ${money(s.estimatedCost)}` : '—'],
          ['Payment', `${(s.paymentPlan ?? '—').replaceAll('_', ' ').toLowerCase()}${s.insurerName ? ` · ${s.insurerName}` : ''}`],
          ['Preferred date', dateLabel(s.preferredDate)],
          ['Notes', s.counsellingNotes],
          ['Counselled by', `${s.counselledByName} at ${clock(s.counselledAt)}`],
        ]} />
      </Section>
    ) : null;
  }
  return (
    <Section title="Counselling" description="Explain the procedure, risks, recovery and cost; record what the patient decides."
      actions={<Button variant="contained" onClick={() => save.mutate(undefined)} disabled={save.isPending}>Save decision</Button>}>
      <Paper sx={{ p: 2 }}>
        <Stack spacing={2}>
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '2fr 1fr' }, gap: 2 }}>
            <TextField select label="Surgery package" value={f.packageItemId} onChange={(e) => {
              const p = packages.find((x) => x.id === e.target.value);
              setF({ ...f, packageItemId: e.target.value, estimatedCost: p ? String(p.price) : f.estimatedCost });
            }}>
              <MenuItem value="">—</MenuItem>
              {packages.map((p) => <MenuItem key={p.id} value={p.id}>{p.name} · {money(p.price)}</MenuItem>)}
            </TextField>
            <TextField label="Estimated cost (₹)" type="number" value={f.estimatedCost} onChange={(e) => setF({ ...f, estimatedCost: e.target.value })} />
          </Box>
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr 1fr' }, gap: 2 }}>
            <TextField select label="Payment" value={f.paymentPlan} onChange={(e) => setF({ ...f, paymentPlan: e.target.value })}>
              <MenuItem value="SELF_PAY">Self pay</MenuItem>
              <MenuItem value="INSURANCE">Insurance / TPA</MenuItem>
              <MenuItem value="CORPORATE">Corporate</MenuItem>
              <MenuItem value="GOVERNMENT_SCHEME">Government scheme</MenuItem>
            </TextField>
            <TextField label="Insurer / TPA" value={f.insurerName} onChange={(e) => setF({ ...f, insurerName: e.target.value })} disabled={f.paymentPlan !== 'INSURANCE'} />
            <TextField label="Preferred date" type="date" value={f.preferredDate} onChange={(e) => setF({ ...f, preferredDate: e.target.value })} slotProps={{ inputLabel: { shrink: true } }} />
          </Box>
          <TextField label="Counselling notes" value={f.notes} onChange={(e) => setF({ ...f, notes: e.target.value })} multiline minRows={3} />
          <RadioGroup row value={f.decision} onChange={(e) => setF({ ...f, decision: e.target.value })} aria-label="Patient's decision">
            <FormControlLabel value="ACCEPTED" control={<Radio />} label="Accepts surgery" />
            <FormControlLabel value="UNDECIDED" control={<Radio />} label="Will decide later" />
            <FormControlLabel value="DECLINED" control={<Radio />} label="Declines" />
          </RadioGroup>
        </Stack>
        <ProblemAlert error={save.error} />
      </Paper>
    </Section>
  );
}

function Schedule({ s }: { s: api.SurgeryView }) {
  const session = useSession();
  const canEdit = session.hasAtCentre('eye.surgery.schedule', s.centreId) && ['ACCEPTED', 'SCHEDULED'].includes(s.status ?? '');
  const surgeons = useClinicians(canEdit ? s.centreId : undefined, 'SURGERY_DAYCARE');
  const [f, setF] = useState({ date: s.surgeryDate ?? s.preferredDate ?? todayIst(), time: hhmm(s.slotTime) === '—' ? '09:00' : hhmm(s.slotTime), room: s.otRoom ?? 'OT 1', surgeon: s.surgeonPersonId ?? '' });
  const save = useAction(() => api.scheduleSurgery(s.id!, { surgeryDate: f.date, slotTime: `${f.time}:00`, otRoom: f.room || undefined, surgeonPersonId: f.surgeon, expectedVersion: s.version ?? 0 }),
    { invalidate, success: `Surgery booked on ${dateLabel(f.date)} at ${f.time}` });
  if (!canEdit) {
    return s.surgeryDate ? (
      <Section title="Schedule">
        <KeyValue items={[['Date', `${dateLabel(s.surgeryDate)} at ${hhmm(s.slotTime)}`], ['Theatre', s.otRoom], ['Surgeon', s.surgeonName]]} />
      </Section>
    ) : null;
  }
  return (
    <Section title="Schedule" actions={<Button variant="contained" onClick={() => save.mutate(undefined)} disabled={save.isPending || !f.surgeon}>{s.status === 'SCHEDULED' ? 'Reschedule' : 'Book OT slot'}</Button>}>
      <Paper sx={{ p: 2 }}>
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr 1fr 2fr' }, gap: 2 }}>
          <TextField label="Date" type="date" value={f.date} onChange={(e) => setF({ ...f, date: e.target.value })} slotProps={{ inputLabel: { shrink: true } }} />
          <TextField label="Time" type="time" value={f.time} onChange={(e) => setF({ ...f, time: e.target.value })} slotProps={{ inputLabel: { shrink: true }, htmlInput: { step: 900 } }} />
          <TextField label="Theatre" value={f.room} onChange={(e) => setF({ ...f, room: e.target.value })} />
          <TextField select label="Surgeon" value={f.surgeon} onChange={(e) => setF({ ...f, surgeon: e.target.value })} required>
            {(surgeons.data ?? []).map((d) => <MenuItem key={d.personId} value={d.personId}>{d.name} · {d.positionTitle}</MenuItem>)}
          </TextField>
        </Box>
        <ProblemAlert error={save.error} />
      </Paper>
    </Section>
  );
}

function PreOp({ s }: { s: api.SurgeryView }) {
  const session = useSession();
  const canEdit = session.hasAtCentre('eye.surgery.prepare', s.centreId) && ['SCHEDULED', 'READY'].includes(s.status ?? '');
  const [f, setF] = useState({
    consentSigned: !!s.consentSigned, siteMarked: !!s.siteMarked, fitnessCleared: !!s.fitnessCleared, biometryDone: !!s.biometryDone,
    pupilDilated: !!s.pupilDilated, iolPower: s.iolPower ?? '', preOpBp: s.preOpBp ?? '', preOpBloodSugar: s.preOpBloodSugar ?? '', preOpNotes: s.preOpNotes ?? '',
  });
  const save = useAction((ready: boolean) => api.saveSurgeryPreOp(s.id!, {
    ...f, iolPower: f.iolPower || undefined, preOpBp: f.preOpBp || undefined, preOpBloodSugar: f.preOpBloodSugar || undefined, preOpNotes: f.preOpNotes || undefined,
    ready, expectedVersion: s.version ?? 0,
  }), { invalidate, success: (r) => (r.status === 'READY' ? 'Checklist complete: ready for theatre' : 'Checklist saved') });
  if (!canEdit && !s.preparedAt) {
    return null;
  }
  const box = (k: 'consentSigned' | 'siteMarked' | 'fitnessCleared' | 'biometryDone' | 'pupilDilated', label: string) => (
    <FormControlLabel control={<Checkbox checked={f[k]} disabled={!canEdit} onChange={(e) => setF({ ...f, [k]: e.target.checked })} />} label={label} />
  );
  return (
    <Section title="Pre-operative checklist" description={s.preparedAt ? `Last updated by ${s.preparedByName} at ${clock(s.preparedAt)}` : 'Consent, operative-eye marking and fitness are required before theatre.'}
      actions={canEdit ? (
        <>
          <Button onClick={() => save.mutate(false)} disabled={save.isPending}>Save</Button>
          <Button variant="contained" onClick={() => save.mutate(true)} disabled={save.isPending}>Mark ready for theatre</Button>
        </>
      ) : undefined}>
      <Paper sx={{ p: 2 }}>
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' } }}>
          {box('consentSigned', 'Informed consent signed')}
          {box('siteMarked', `Operative eye marked (${eyeLabel(s.eye)})`)}
          {box('fitnessCleared', 'Medically fit (BP, sugar, ECG as needed)')}
          {box('biometryDone', 'Biometry done, IOL power chosen')}
          {box('pupilDilated', 'Pupil dilated')}
        </Box>
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr 1fr' }, gap: 2, mt: 1 }}>
          <TextField label="IOL power" value={f.iolPower} disabled={!canEdit} onChange={(e) => setF({ ...f, iolPower: e.target.value })} placeholder="+21.5 D" />
          <TextField label="Blood pressure" value={f.preOpBp} disabled={!canEdit} onChange={(e) => setF({ ...f, preOpBp: e.target.value })} placeholder="130/80" />
          <TextField label="Blood sugar" value={f.preOpBloodSugar} disabled={!canEdit} onChange={(e) => setF({ ...f, preOpBloodSugar: e.target.value })} placeholder="140 mg/dL" />
        </Box>
        <TextField label="Notes" value={f.preOpNotes} disabled={!canEdit} onChange={(e) => setF({ ...f, preOpNotes: e.target.value })} fullWidth multiline sx={{ mt: 2 }} />
        <ProblemAlert error={save.error} />
      </Paper>
    </Section>
  );
}

function Operation({ s }: { s: api.SurgeryView }) {
  const session = useSession();
  const canEdit = session.hasAtCentre('eye.surgery.operate', s.centreId) && s.status === 'READY';
  const [f, setF] = useState({
    anaesthesia: s.anaesthesia ?? 'TOPICAL', iolImplanted: s.iolImplanted ?? '', operativeNotes: s.operativeNotes ?? '', complications: s.complications ?? 'None',
    postOpInstructions: s.postOpInstructions ?? '',
  });
  const save = useAction((complete: boolean) => api.saveSurgeryOperation(s.id!, {
    anaesthesia: f.anaesthesia || undefined, iolImplanted: f.iolImplanted || undefined, operativeNotes: f.operativeNotes || undefined,
    complications: f.complications || undefined, postOpInstructions: f.postOpInstructions || undefined, complete, expectedVersion: s.version ?? 0,
  }), { invalidate, success: (r) => (r.status === 'COMPLETED' ? 'Surgery completed; day-1 review booked for tomorrow' : 'Operative note saved') });
  if (!canEdit && !s.operativeNotes) {
    return s.status === 'SCHEDULED' && session.hasAtCentre('eye.surgery.operate', s.centreId) ? (
      <Alert severity="info">The operative note opens when the pre-operative checklist is complete.</Alert>
    ) : null;
  }
  const t = (k: keyof typeof f, label: string, rows = 1) => (
    <TextField label={label} value={f[k]} disabled={!canEdit} onChange={(e) => setF({ ...f, [k]: e.target.value })} multiline={rows > 1} minRows={rows} fullWidth />
  );
  return (
    <Section title="Operation" description={s.completedAt ? `Completed by ${s.operatedByName} at ${clock(s.completedAt)}` : 'Operative note'}
      actions={canEdit ? (
        <>
          <Button onClick={() => save.mutate(false)} disabled={save.isPending}>Save</Button>
          <Button variant="contained" color="success" onClick={() => save.mutate(true)} disabled={save.isPending}>Complete surgery</Button>
        </>
      ) : undefined}>
      <Paper sx={{ p: 2 }}>
        <Stack spacing={2}>
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 2fr' }, gap: 2 }}>
            <TextField select label="Anaesthesia" value={f.anaesthesia} disabled={!canEdit} onChange={(e) => setF({ ...f, anaesthesia: e.target.value })}>
              <MenuItem value="TOPICAL">Topical</MenuItem>
              <MenuItem value="PERIBULBAR">Peribulbar block</MenuItem>
              <MenuItem value="RETROBULBAR">Retrobulbar block</MenuItem>
              <MenuItem value="SUBTENON">Sub-Tenon</MenuItem>
              <MenuItem value="GENERAL">General</MenuItem>
            </TextField>
            {t('iolImplanted', 'IOL implanted (type, power, lot)')}
          </Box>
          {t('operativeNotes', 'Operative notes', 4)}
          {t('complications', 'Complications')}
          {t('postOpInstructions', 'Post-operative instructions', 2)}
        </Stack>
        <ProblemAlert error={save.error} />
      </Paper>
    </Section>
  );
}
