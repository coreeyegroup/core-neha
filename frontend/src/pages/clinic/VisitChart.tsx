import {
  Alert,
  Autocomplete,
  Box,
  Button,
  Checkbox,
  FormControlLabel,
  Grid,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { useState, type ReactNode } from 'react';
import { Link as RouterLink, useNavigate, useParams } from 'react-router';

import * as api from '../../api/generated/core-neha';
import { useAction } from '../../api/useAction';
import { useSession } from '../../auth/session';
import { PageHeader } from '../../components/PageHeader';
import { ProblemAlert } from '../../components/ProblemAlert';
import { KeyValue, Section } from '../../components/Section';
import { StatusChip } from '../../components/StatusChip';
import { BillActions } from './BillActions';
import { ageSex, clock, dateLabel, eyeLabel, money, StageChip, VisitStepper } from './common';
import { ReasonDialog } from './dialogs';
import { PatientPrescriptions, PrescribeDialog } from '../supply/Pharmacy';

export const VISUAL_ACUITY = ['6/5', '6/6', '6/6P', '6/7.5', '6/9', '6/9P', '6/12', '6/18', '6/24', '6/36', '6/60', '5/60', '4/60', '3/60', '2/60', '1/60', 'CF', 'HM', 'PL', 'PR', 'NPL'];
export const NEAR_VISION = ['N5', 'N6', 'N8', 'N10', 'N12', 'N18', 'N24', 'N36'];
export const PROCEDURES = [
  'Phacoemulsification with foldable IOL',
  'Phacoemulsification with toric IOL',
  'Small incision cataract surgery (SICS) with IOL',
  'Pterygium excision with conjunctival autograft',
  'Trabeculectomy',
  'YAG laser capsulotomy',
  'Intravitreal anti-VEGF injection',
  'Chalazion incision and curettage',
];

export function useVisitChart(visitId: string) {
  return useQuery({ queryKey: ['visit', visitId], queryFn: () => api.visitChart(visitId), enabled: !!visitId });
}

/** One visit as every department sees it: each department works on its own section of the same record. */
export function VisitChartPage() {
  const { visitId = '' } = useParams();
  const session = useSession();
  const chart = useVisitChart(visitId);
  const [cancel, setCancel] = useState(false);
  const [prescribing, setPrescribing] = useState(false);
  if (chart.error) {
    return <ProblemAlert error={chart.error} />;
  }
  const c = chart.data;
  const v = c?.visit;
  if (!c || !v) {
    return <Typography color="text.secondary">Loading…</Typography>;
  }
  const centreId = v.centreId;
  const open = v.stage !== 'COMPLETED' && v.stage !== 'CANCELLED';
  return (
    <>
      <PageHeader
        title={`Token ${v.tokenNo} · ${v.patientName}`}
        subtitle={<>{v.patientUhid} · {ageSex(v.patientAge, v.patientSex)} · {v.patientMobile ?? 'no mobile'} · {(v.visitType ?? '').replace('_', '-').toLowerCase()} visit on {dateLabel(v.visitDate)} · {v.centreName}</>}
        actions={
          <>
            <Button component={RouterLink} to={`/patients/${v.patientId}`}>Patient record</Button>
            {open && session.hasAtCentre('opd.checkin', centreId) && <Button color="warning" onClick={() => setCancel(true)}>Cancel visit…</Button>}
          </>
        }
      />
      <Paper sx={{ px: 2, py: 0.5, mb: 2 }}>
        <VisitStepper stage={v.stage} surgeryAdvised={v.surgeryAdvised} />
        {v.stage === 'CANCELLED' && <Alert severity="warning" sx={{ mb: 1 }}>Cancelled: {v.cancelReason}</Alert>}
      </Paper>
      {!c.clinicalVisible && (
        <Alert severity="info" sx={{ mb: 2 }}>Clinical findings are shown to clinical staff only. You see the visit’s progress and bills.</Alert>
      )}
      <Grid container spacing={2}>
        <Grid size={{ xs: 12, lg: 8 }}>
          {c.clinicalVisible && <OptometrySection chart={c} />}
          {c.clinicalVisible && <ConsultationSection chart={c} />}
          {c.clinicalVisible && c.surgery && <SurgerySummary surgery={c.surgery} />}
          {(session.hasAtCentre('pharmacy.prescribe', v.centreId) || session.hasAtCentre('pharmacy.read', v.centreId)) && (
            <Section title="Medicines" actions={session.hasAtCentre('pharmacy.prescribe', v.centreId) && <Button onClick={() => setPrescribing(true)}>Prescribe</Button>}>
              <Paper sx={{ p: 1.5 }}><PatientPrescriptions patientId={v.patientId!} /></Paper>
            </Section>
          )}
          {prescribing && <PrescribeDialog centreId={v.centreId!} patientId={v.patientId!} patientName={v.patientName} visitId={v.id} onClose={() => setPrescribing(false)} />}
        </Grid>
        <Grid size={{ xs: 12, lg: 4 }}>
          <Section title="Visit">
            <KeyValue items={[
              ['Stage', <StageChip key="s" stage={v.stage} />],
              ['Chief complaint', v.chiefComplaint],
              ['Doctor', v.consultantName],
              ['Checked in', clock(v.checkedInAt)],
              ['Optometry done', clock(v.optometryDoneAt)],
              ['Consultation done', clock(v.consultationDoneAt)],
              ...(v.counsellingDoneAt ? [['Counselling done', clock(v.counsellingDoneAt)] as [string, ReactNode]] : []),
              ['Completed', clock(v.completedAt)],
              ...(v.noChargeReason ? [['No charge', v.noChargeReason] as [string, ReactNode]] : []),
            ]} />
          </Section>
          <Section title="Bills">
            <Paper sx={{ p: 1.5 }}>
              {(c.bills ?? []).length === 0 && <Typography variant="body2" color="text.secondary">No bill yet.</Typography>}
              {(c.bills ?? []).map((b) => (
                <Stack key={b.id} direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', py: 0.5 }}>
                  <RouterLink to={`/bills/${b.id}`}>{b.billNo}</RouterLink>
                  <Typography variant="body2">{money(b.netAmount)}</Typography>
                  <StatusChip status={b.status} />
                </Stack>
              ))}
              {open && <Box sx={{ mt: 1 }}><BillActions visit={v} chart={c} /></Box>}
            </Paper>
          </Section>
          {(c.previousVisits ?? []).length > 0 && (
            <Section title="Earlier visits">
              <Paper sx={{ p: 1.5 }}>
                {(c.previousVisits ?? []).map((p) => (
                  <Stack key={p.id} direction="row" sx={{ justifyContent: 'space-between', py: 0.25 }}>
                    <RouterLink to={`/visits/${p.id}`}>{dateLabel(p.visitDate)}</RouterLink>
                    <Typography variant="caption" color="text.secondary">{p.centreName}</Typography>
                    <StageChip stage={p.stage} />
                  </Stack>
                ))}
              </Paper>
            </Section>
          )}
        </Grid>
      </Grid>
      {cancel && (
        <ReasonDialog title="Cancel this visit" submitLabel="Cancel visit" onClose={() => setCancel(false)} success="Visit cancelled"
          run={(reason) => api.cancelVisit(v.id!, { reason, expectedVersion: v.version ?? 0 })} />
      )}
    </>
  );
}

// ---------------------------------------------------------------- optometry

function fromOptometry(o?: api.OptometryView) {
  const s = (x: unknown) => (x === undefined || x === null ? '' : String(x));
  // Dioptres with sign and two decimals, as optometrists write them (+1.00, -0.75).
  const d = (x?: number) => (x === undefined || x === null ? '' : `${x > 0 ? '+' : ''}${x.toFixed(2)}`);
  return {
    ucvaRe: s(o?.ucvaRe), ucvaLe: s(o?.ucvaLe), pgvaRe: s(o?.pgvaRe), pgvaLe: s(o?.pgvaLe), nearRe: s(o?.nearRe), nearLe: s(o?.nearLe),
    sphRe: d(o?.sphRe), cylRe: d(o?.cylRe), axisRe: s(o?.axisRe), addRe: d(o?.addRe), bcvaRe: s(o?.bcvaRe),
    sphLe: d(o?.sphLe), cylLe: d(o?.cylLe), axisLe: s(o?.axisLe), addLe: d(o?.addLe), bcvaLe: s(o?.bcvaLe),
    iopRe: s(o?.iopRe), iopLe: s(o?.iopLe), iopMethod: s(o?.iopMethod), colourVision: s(o?.colourVision), notes: s(o?.notes),
  };
}

type Opto = ReturnType<typeof fromOptometry>;
type OptoKey = keyof Opto;

const num = (x: string) => (x.trim() === '' ? undefined : Number(x));
const txt = (x: string) => (x.trim() === '' ? undefined : x.trim());

function OptometrySection({ chart }: { chart: api.VisitChart }) {
  const session = useSession();
  const v = chart.visit!;
  const o = chart.optometry;
  const canEdit = session.hasAtCentre('eye.optometry.record', v.centreId) && (v.stage === 'OPTOMETRY' || v.stage === 'CONSULTATION');
  const [f, setF] = useState<Opto>(() => fromOptometry(o));
  const save = useAction(
    (complete: boolean) => api.saveOptometry(v.id!, {
      ucvaRe: txt(f.ucvaRe), ucvaLe: txt(f.ucvaLe), pgvaRe: txt(f.pgvaRe), pgvaLe: txt(f.pgvaLe), nearRe: txt(f.nearRe), nearLe: txt(f.nearLe),
      sphRe: num(f.sphRe), cylRe: num(f.cylRe), axisRe: num(f.axisRe), addRe: num(f.addRe), bcvaRe: txt(f.bcvaRe),
      sphLe: num(f.sphLe), cylLe: num(f.cylLe), axisLe: num(f.axisLe), addLe: num(f.addLe), bcvaLe: txt(f.bcvaLe),
      iopRe: num(f.iopRe), iopLe: num(f.iopLe), iopMethod: txt(f.iopMethod), colourVision: txt(f.colourVision), notes: txt(f.notes),
      complete, expectedVersion: o?.version,
    }),
    { invalidate: [['visit', v.id!], ['visits'], ['clinic-board']], success: (r) => (r.visit?.stage === 'CONSULTATION' && v.stage === 'OPTOMETRY' ? 'Optometry complete; sent to the doctor' : 'Optometry saved') },
  );
  const field = (k: OptoKey, label: string, options?: string[], type?: string) => options ? (
    <Autocomplete freeSolo options={options} value={f[k]} disabled={!canEdit}
      onInputChange={(_, val) => setF((x) => ({ ...x, [k]: val }))}
      renderInput={(p) => <TextField {...p} hiddenLabel slotProps={{ ...p.slotProps, htmlInput: { ...p.slotProps.htmlInput, 'aria-label': label } }} />} />
  ) : (
    <TextField hiddenLabel value={f[k]} type={type === 'dioptre' ? 'text' : type} disabled={!canEdit} onChange={(e) => setF((x) => ({ ...x, [k]: e.target.value }))}
      placeholder={type === 'dioptre' ? '±0.00' : undefined}
      slotProps={{ htmlInput: { 'aria-label': label, inputMode: type === 'dioptre' ? 'decimal' : undefined } }} />
  );
  const rows: Array<[string, string, string[] | undefined, string | undefined]> = [
    ['Unaided distance vision', 'ucva', VISUAL_ACUITY, undefined],
    ['With present glasses', 'pgva', VISUAL_ACUITY, undefined],
    ['Near vision', 'near', NEAR_VISION, undefined],
    ['Sphere (D)', 'sph', undefined, 'dioptre'],
    ['Cylinder (D)', 'cyl', undefined, 'dioptre'],
    ['Axis (°)', 'axis', undefined, 'number'],
    ['Near add (D)', 'add', undefined, 'dioptre'],
    ['Best corrected vision', 'bcva', VISUAL_ACUITY, undefined],
    ['Eye pressure, IOP (mmHg)', 'iop', undefined, 'number'],
  ];
  return (
    <Section title="Optometry" description={o ? `Recorded by ${o.recordedByName} at ${clock(o.recordedAt)}` : 'Vision, refraction and eye pressure'}
      actions={canEdit ? (
        <>
          <Button onClick={() => save.mutate(false)} disabled={save.isPending}>Save</Button>
          {v.stage === 'OPTOMETRY' && <Button variant="contained" onClick={() => save.mutate(true)} disabled={save.isPending}>Complete and send to doctor</Button>}
        </>
      ) : undefined}>
      <Paper sx={{ p: 1.5 }}>
        <Table size="small" aria-label="Optometry findings">
          <TableHead>
            <TableRow>
              <TableCell />
              <TableCell sx={{ width: '36%' }}>Right eye (RE)</TableCell>
              <TableCell sx={{ width: '36%' }}>Left eye (LE)</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.map(([label, k, opts, type]) => (
              <TableRow key={k}>
                <TableCell component="th" scope="row"><Typography variant="body2">{label}</Typography></TableCell>
                <TableCell>{field(`${k}Re` as OptoKey, `${label}, right eye`, opts, type)}</TableCell>
                <TableCell>{field(`${k}Le` as OptoKey, `${label}, left eye`, opts, type)}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mt: 2 }}>
          <TextField select label="IOP method" value={f.iopMethod} disabled={!canEdit} onChange={(e) => setF({ ...f, iopMethod: e.target.value })} sx={{ minWidth: 180 }}>
            <MenuItem value="">—</MenuItem>
            <MenuItem value="NCT">Non-contact (NCT)</MenuItem>
            <MenuItem value="APPLANATION">Applanation</MenuItem>
            <MenuItem value="TONOPEN">Tono-Pen</MenuItem>
            <MenuItem value="ICARE">iCare</MenuItem>
          </TextField>
          <TextField label="Colour vision" value={f.colourVision} disabled={!canEdit} onChange={(e) => setF({ ...f, colourVision: e.target.value })} />
          <TextField label="Notes" value={f.notes} disabled={!canEdit} onChange={(e) => setF({ ...f, notes: e.target.value })} fullWidth />
        </Stack>
        <ProblemAlert error={save.error} />
      </Paper>
    </Section>
  );
}

// ---------------------------------------------------------------- consultation

function ConsultationSection({ chart }: { chart: api.VisitChart }) {
  const session = useSession();
  const v = chart.visit!;
  const c = chart.consultation;
  const canEdit = session.hasAtCentre('eye.consultation.record', v.centreId) && (v.stage === 'OPTOMETRY' || v.stage === 'CONSULTATION');
  const [f, setF] = useState(() => ({
    history: c?.history ?? '', anteriorSegmentRe: c?.anteriorSegmentRe ?? '', anteriorSegmentLe: c?.anteriorSegmentLe ?? '',
    posteriorSegmentRe: c?.posteriorSegmentRe ?? '', posteriorSegmentLe: c?.posteriorSegmentLe ?? '', diagnosis: c?.diagnosis ?? '',
    diagnosisCode: c?.diagnosisCode ?? '', diagnosisEye: c?.diagnosisEye ?? '', medications: c?.medications ?? '',
    glassesPrescribed: c?.glassesPrescribed ?? false, advice: c?.advice ?? '', followUpDays: c?.followUpDays?.toString() ?? '',
    surgeryAdvised: c?.surgeryAdvised ?? false, surgeryProcedure: c?.surgeryProcedure ?? '', surgeryEye: c?.surgeryEye ?? '',
    surgeryUrgency: c?.surgeryUrgency ?? 'ROUTINE',
  }));
  const save = useAction(
    (complete: boolean) => api.saveConsultation(v.id!, {
      history: txt(f.history), anteriorSegmentRe: txt(f.anteriorSegmentRe), anteriorSegmentLe: txt(f.anteriorSegmentLe),
      posteriorSegmentRe: txt(f.posteriorSegmentRe), posteriorSegmentLe: txt(f.posteriorSegmentLe), diagnosis: txt(f.diagnosis),
      diagnosisCode: txt(f.diagnosisCode.toUpperCase()), diagnosisEye: txt(f.diagnosisEye), medications: txt(f.medications),
      glassesPrescribed: f.glassesPrescribed, advice: txt(f.advice), followUpDays: num(f.followUpDays), surgeryAdvised: f.surgeryAdvised,
      surgeryProcedure: f.surgeryAdvised ? txt(f.surgeryProcedure) : undefined, surgeryEye: f.surgeryAdvised ? txt(f.surgeryEye) : undefined,
      surgeryUrgency: f.surgeryAdvised ? txt(f.surgeryUrgency) : undefined, complete, expectedVersion: c?.version,
    }),
    {
      invalidate: [['visit', v.id!], ['visits'], ['clinic-board'], ['surgeries']],
      success: (r) => r.visit?.stage === 'COUNSELLING' ? 'Consultation complete; sent to surgery counselling' : r.visit?.stage === 'CHECKOUT' ? 'Consultation complete; sent to checkout' : 'Consultation saved',
    },
  );
  const t = (k: keyof typeof f, label: string, rows = 1) => (
    <TextField label={label} value={f[k] as string} disabled={!canEdit} onChange={(e) => setF({ ...f, [k]: e.target.value })} multiline={rows > 1} minRows={rows} fullWidth />
  );
  return (
    <Section title="Consultation" description={c ? `By ${c.consultantName ?? c.recordedByName} at ${clock(c.recordedAt)}` : 'Examination, diagnosis and treatment'}
      actions={canEdit ? (
        <>
          <Button onClick={() => save.mutate(false)} disabled={save.isPending}>Save</Button>
          <Button variant="contained" onClick={() => save.mutate(true)} disabled={save.isPending}>
            {f.surgeryAdvised ? 'Complete and send to counselling' : 'Complete and send to checkout'}
          </Button>
        </>
      ) : undefined}>
      <Paper sx={{ p: 2 }}>
        {v.stage === 'OPTOMETRY' && canEdit && <Alert severity="info" sx={{ mb: 2 }}>Optometry is not complete yet. You can still examine the patient now.</Alert>}
        <Stack spacing={2}>
          {t('history', 'History and presenting complaint', 2)}
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2 }}>
            {t('anteriorSegmentRe', 'Anterior segment, right eye')}
            {t('anteriorSegmentLe', 'Anterior segment, left eye')}
            {t('posteriorSegmentRe', 'Fundus / posterior segment, right eye')}
            {t('posteriorSegmentLe', 'Fundus / posterior segment, left eye')}
          </Box>
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '3fr 1fr 1fr' }, gap: 2 }}>
            {t('diagnosis', 'Diagnosis')}
            <TextField label="ICD-10" value={f.diagnosisCode} disabled={!canEdit} onChange={(e) => setF({ ...f, diagnosisCode: e.target.value })} placeholder="H25.1" />
            <TextField select label="Eye" value={f.diagnosisEye} disabled={!canEdit} onChange={(e) => setF({ ...f, diagnosisEye: e.target.value })}>
              <MenuItem value="">—</MenuItem>
              <MenuItem value="RE">Right eye</MenuItem>
              <MenuItem value="LE">Left eye</MenuItem>
              <MenuItem value="BE">Both eyes</MenuItem>
            </TextField>
          </Box>
          {t('medications', 'Medicines (one per line: drug, dose, eye, how often, how long)', 3)}
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '3fr 1fr' }, gap: 2 }}>
            {t('advice', 'Advice')}
            <TextField label="Follow-up in (days)" type="number" value={f.followUpDays} disabled={!canEdit} onChange={(e) => setF({ ...f, followUpDays: e.target.value })} />
          </Box>
          <FormControlLabel control={<Checkbox checked={f.glassesPrescribed} disabled={!canEdit} onChange={(e) => setF({ ...f, glassesPrescribed: e.target.checked })} />}
            label="Spectacles prescribed (from the refraction above)" />
          <FormControlLabel control={<Checkbox checked={f.surgeryAdvised} disabled={!canEdit} onChange={(e) => setF({ ...f, surgeryAdvised: e.target.checked })} />}
            label="Surgery advised (the patient goes to surgery counselling)" />
          {f.surgeryAdvised && (
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '3fr 1fr 1fr' }, gap: 2 }}>
              <Autocomplete freeSolo options={PROCEDURES} value={f.surgeryProcedure} disabled={!canEdit}
                onInputChange={(_, val) => setF((x) => ({ ...x, surgeryProcedure: val }))}
                renderInput={(p) => <TextField {...p} label="Procedure" required />} />
              <TextField select label="Operative eye" value={f.surgeryEye} disabled={!canEdit} required onChange={(e) => setF({ ...f, surgeryEye: e.target.value })}>
                <MenuItem value="RE">Right eye</MenuItem>
                <MenuItem value="LE">Left eye</MenuItem>
                <MenuItem value="BE">Both eyes</MenuItem>
              </TextField>
              <TextField select label="Urgency" value={f.surgeryUrgency} disabled={!canEdit} onChange={(e) => setF({ ...f, surgeryUrgency: e.target.value })}>
                <MenuItem value="ROUTINE">Routine</MenuItem>
                <MenuItem value="SOON">Within 2 weeks</MenuItem>
                <MenuItem value="URGENT">Urgent</MenuItem>
              </TextField>
            </Box>
          )}
        </Stack>
        <ProblemAlert error={save.error} />
      </Paper>
    </Section>
  );
}

function SurgerySummary({ surgery }: { surgery: api.SurgeryView }) {
  const navigate = useNavigate();
  return (
    <Section title="Surgery" actions={<Button onClick={() => navigate(`/surgeries/${surgery.id}`)}>Open surgery</Button>}>
      <KeyValue items={[
        ['Procedure', `${surgery.procedureName} · ${eyeLabel(surgery.eye)}`],
        ['Status', <StatusChip key="s" status={surgery.status} />],
        ['Package', surgery.packageName ? `${surgery.packageName} · ${money(surgery.estimatedCost)}` : '—'],
        ['Scheduled', surgery.surgeryDate ? `${dateLabel(surgery.surgeryDate)} at ${(surgery.slotTime ?? '').slice(0, 5)} with ${surgery.surgeonName}` : '—'],
      ]} />
    </Section>
  );
}
