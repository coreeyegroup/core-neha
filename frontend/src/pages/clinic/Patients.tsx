import { Alert, Box, Button, Grid, MenuItem, Stack, TextField, Typography } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router';

import * as api from '../../api/generated/core-neha';
import { useAction } from '../../api/useAction';
import { useSession } from '../../auth/session';
import { DataTable } from '../../components/DataTable';
import { FormDialog } from '../../components/FormDialog';
import { PageHeader } from '../../components/PageHeader';
import { ProblemAlert } from '../../components/ProblemAlert';
import { KeyValue, Section } from '../../components/Section';
import { StatusChip } from '../../components/StatusChip';
import { ageSex, dateLabel, eyeLabel, hhmm, money, StageChip, useWorkingCentre } from './common';
import { BookAppointmentDialog, RegisterPatientDialog, WalkInDialog } from './dialogs';

/** The patient database: one record per person for the whole group, searchable by name, mobile or UHID. */
export function Patients() {
  const session = useSession();
  const { centreId } = useWorkingCentre();
  const navigate = useNavigate();
  const [q, setQ] = useState('');
  const [debounced, setDebounced] = useState('');
  const [register, setRegister] = useState(false);
  useEffect(() => {
    const t = setTimeout(() => setDebounced(q.trim()), 300);
    return () => clearTimeout(t);
  }, [q]);
  const list = useQuery({ queryKey: ['patients', 'list', debounced], queryFn: () => api.searchPatients({ q: debounced || undefined, limit: 200 }) });
  return (
    <>
      <PageHeader title="Patient database" subtitle="One UHID per patient across every centre. Opening a record is logged."
        actions={centreId && session.hasAtCentre('patient.register', centreId) ? <Button variant="contained" onClick={() => setRegister(true)}>Register patient</Button> : undefined} />
      <TextField label="Search by name, mobile number or UHID" value={q} onChange={(e) => setQ(e.target.value)} sx={{ mb: 2, width: { xs: '100%', sm: 420 } }} autoFocus />
      <ProblemAlert error={list.error} />
      <Typography variant="caption" color="text.secondary" component="div" sx={{ mb: 1 }}>
        {debounced ? `${list.data?.length ?? 0} matching` : 'Most recently registered'}
      </Typography>
      <DataTable
        rows={list.data}
        loading={list.isLoading}
        rowKey={(p) => p.id ?? ''}
        empty="No patients found."
        onRowClick={(p) => navigate(`/patients/${p.id}`)}
        columns={[
          { key: 'uhid', header: 'UHID', render: (p) => p.uhid, value: (p) => p.uhid },
          { key: 'name', header: 'Name', render: (p) => <Typography variant="body2" sx={{ fontWeight: 600 }}>{p.fullName}</Typography>, value: (p) => p.fullName },
          { key: 'age', header: 'Age / sex', render: (p) => ageSex(p.ageYears, p.sex), value: (p) => p.ageYears ?? -1 },
          { key: 'mobile', header: 'Mobile', render: (p) => p.mobile ?? '—' },
          { key: 'city', header: 'Area', render: (p) => p.addressLine ?? p.city ?? '—' },
          { key: 'abha', header: 'ABHA', render: (p) => p.abhaNumber ?? '—' },
          { key: 'centre', header: 'Registered at', render: (p) => p.registeredCentreName, value: (p) => p.registeredCentreName },
          { key: 'date', header: 'Since', render: (p) => dateLabel(p.createdAt?.slice(0, 10)), value: (p) => p.createdAt },
        ]}
      />
      {register && centreId && <RegisterPatientDialog centreId={centreId} onClose={() => setRegister(false)} onDone={(p) => navigate(`/patients/${p.id}`)} />}
    </>
  );
}

export function PatientRecord() {
  const { patientId = '' } = useParams();
  const session = useSession();
  const { centreId } = useWorkingCentre();
  const navigate = useNavigate();
  const patient = useQuery({ queryKey: ['patient', patientId], queryFn: () => api.patient(patientId) });
  const history = useQuery({
    queryKey: ['patient', patientId, 'eye-history'], queryFn: () => api.patientEyeHistory(patientId),
    enabled: session.hasAnywhere('opd.queue.read') || session.hasAnywhere('eye.clinical.read'),
  });
  const bills = useQuery({ queryKey: ['patient', patientId, 'bills'], queryFn: () => api.patientBills(patientId), enabled: session.hasAnywhere('billing.read') });
  const [dialog, setDialog] = useState<'edit' | 'book' | 'walkin' | undefined>();
  if (patient.error) {
    return <ProblemAlert error={patient.error} />;
  }
  const p = patient.data;
  if (!p) {
    return <Typography color="text.secondary">Loading…</Typography>;
  }
  const h = history.data;
  return (
    <>
      <PageHeader title={p.fullName ?? 'Patient'} subtitle={<>{p.uhid} · {ageSex(p.ageYears, p.sex)}{p.birthDate ? ` · born ${dateLabel(p.birthDate)}${p.birthDateEstimated ? ' (estimated)' : ''}` : ''} · {p.mobile ?? 'no mobile'}</>}
        actions={
          <>
            {session.hasAnywhere('patient.register') && <Button onClick={() => setDialog('edit')}>Edit details</Button>}
            {centreId && session.hasAtCentre('opd.appointment.manage', centreId) && <Button onClick={() => setDialog('book')}>Book appointment</Button>}
            {centreId && session.hasAtCentre('opd.checkin', centreId) && <Button variant="contained" onClick={() => setDialog('walkin')}>Check in now</Button>}
          </>
        } />
      {p.demo && <Alert severity="info" sx={{ mb: 2 }}>Synthetic demonstration patient.</Alert>}
      <Grid container spacing={2}>
        <Grid size={{ xs: 12, lg: 4 }}>
          <Section title="Details">
            <KeyValue items={[
              ['UHID', p.uhid], ['Mobile', p.mobile], ['Email', p.email], ['Address', [p.addressLine, p.city, p.state, p.pincode].filter(Boolean).join(', ')],
              ['ABHA number', p.abhaNumber], ['Guardian', p.guardianName], ['Emergency contact', [p.emergencyContactName, p.emergencyContactPhone].filter(Boolean).join(' · ')],
              ['Registered at', p.registeredCentreName], ['Registered on', dateLabel(p.createdAt?.slice(0, 10))],
            ]} />
          </Section>
          {(h?.upcomingAppointments ?? []).length > 0 && (
            <Section title="Upcoming appointments">
              <KeyValue items={(h?.upcomingAppointments ?? []).map((a) => [`${dateLabel(a.appointmentDate)} ${hhmm(a.slotTime)}`, `${(a.visitType ?? '').replace('_', '-').toLowerCase()} · ${a.consultantName ?? 'any doctor'}`])} />
            </Section>
          )}
        </Grid>
        <Grid size={{ xs: 12, lg: 8 }}>
          {history.error ? <ProblemAlert error={history.error} /> : null}
          {h && (
            <Section title="Visits" description={h.clinicalVisible ? 'Diagnosis and best-corrected vision from each visit.' : 'Clinical details are shown to clinical staff only.'}>
              <DataTable
                rows={h.visits}
                loading={history.isLoading}
                rowKey={(x) => x.visit?.id ?? ''}
                empty="No visits yet."
                onRowClick={(x) => navigate(`/visits/${x.visit?.id}`)}
                columns={[
                  { key: 'date', header: 'Date', render: (x) => dateLabel(x.visit?.visitDate), value: (x) => x.visit?.visitDate },
                  { key: 'centre', header: 'Centre', render: (x) => x.visit?.centreName },
                  { key: 'type', header: 'Visit', render: (x) => (x.visit?.visitType ?? '').replace('_', '-').toLowerCase() },
                  { key: 'doctor', header: 'Doctor', render: (x) => x.visit?.consultantName ?? '—' },
                  ...(h.clinicalVisible ? [
                    { key: 'dx', header: 'Diagnosis', render: (x: api.HistoryVisit) => (x.diagnosis ? `${x.diagnosis}${x.diagnosisCode ? ` (${x.diagnosisCode})` : ''}` : '—') },
                    { key: 'va', header: 'BCVA RE / LE', render: (x: api.HistoryVisit) => `${x.bcvaRe ?? '—'} / ${x.bcvaLe ?? '—'}` },
                    { key: 'iop', header: 'IOP', render: (x: api.HistoryVisit) => (x.iopRe || x.iopLe ? `${x.iopRe ?? '—'} / ${x.iopLe ?? '—'}` : '—') },
                  ] : []),
                  { key: 'stage', header: 'Stage', render: (x) => <StageChip stage={x.visit?.stage} /> },
                ]}
              />
            </Section>
          )}
          {h && (h.surgeries ?? []).length > 0 && (
            <Section title="Surgeries">
              <DataTable
                rows={h.surgeries}
                rowKey={(s) => s.id ?? ''}
                empty="None."
                onRowClick={(s) => navigate(`/surgeries/${s.id}`)}
                columns={[
                  { key: 'proc', header: 'Procedure', render: (s) => `${s.procedureName} · ${eyeLabel(s.eye)}` },
                  { key: 'date', header: 'Date', render: (s) => (s.surgeryDate ? dateLabel(s.surgeryDate) : '—') },
                  { key: 'surgeon', header: 'Surgeon', render: (s) => s.surgeonName ?? s.operatedByName ?? '—' },
                  { key: 'status', header: 'Status', render: (s) => <StatusChip status={s.status} /> },
                ]}
              />
            </Section>
          )}
          {bills.data && (
            <Section title="Bills">
              <DataTable
                rows={bills.data}
                rowKey={(b) => b.id ?? ''}
                empty="No bills."
                onRowClick={(b) => navigate(`/bills/${b.id}`)}
                columns={[
                  { key: 'no', header: 'Bill', render: (b) => b.billNo },
                  { key: 'date', header: 'Date', render: (b) => dateLabel(b.billDate) },
                  { key: 'net', header: 'Net', align: 'right', render: (b) => money(b.netAmount) },
                  { key: 'bal', header: 'Balance', align: 'right', render: (b) => money(b.balance) },
                  { key: 'status', header: 'Status', render: (b) => <StatusChip status={b.status} /> },
                ]}
              />
            </Section>
          )}
        </Grid>
      </Grid>
      {dialog === 'edit' && <EditPatientDialog patient={p} onClose={() => setDialog(undefined)} />}
      {dialog === 'book' && centreId && <BookAppointmentDialog centreId={centreId} patient={p} onClose={() => setDialog(undefined)} />}
      {dialog === 'walkin' && centreId && <WalkInDialog centreId={centreId} patient={p} onClose={() => setDialog(undefined)} />}
    </>
  );
}

function EditPatientDialog({ patient, onClose }: { patient: api.PatientView; onClose: () => void }) {
  const [f, setF] = useState({
    givenName: patient.givenName ?? '', familyName: patient.familyName ?? '', sex: patient.sex ?? 'UNKNOWN', birthDate: patient.birthDate ?? '',
    mobile: patient.mobile ?? '', email: patient.email ?? '', addressLine: patient.addressLine ?? '', city: patient.city ?? '', state: patient.state ?? '',
    pincode: patient.pincode ?? '', abhaNumber: patient.abhaNumber ?? '', guardianName: patient.guardianName ?? '',
    emergencyContactName: patient.emergencyContactName ?? '', emergencyContactPhone: patient.emergencyContactPhone ?? '',
  });
  const save = useAction(() => api.updatePatient(patient.id!, {
    ...Object.fromEntries(Object.entries(f).map(([k, v]) => [k, v === '' ? undefined : v])),
    givenName: f.givenName, sex: f.sex, expectedVersion: patient.version ?? 0,
    // An estimated date of birth stays estimated unless someone enters the real date.
    ...(patient.birthDateEstimated && f.birthDate === patient.birthDate ? { birthDate: undefined, ageYears: patient.ageYears } : {}),
  } as api.UpdatePatient), { invalidate: [['patient'], ['patients']], success: 'Patient details updated', onDone: onClose });
  const t = (k: keyof typeof f, label: string, type?: string) => (
    <TextField label={label} type={type} value={f[k]} onChange={(e) => setF({ ...f, [k]: e.target.value })} slotProps={type === 'date' ? { inputLabel: { shrink: true } } : undefined} />
  );
  return (
    <FormDialog open title={`Edit ${patient.fullName}`} submitLabel="Save" onClose={onClose} onSubmit={() => save.mutate(undefined)} busy={save.isPending} error={save.error} maxWidth="md">
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr 1fr' }, gap: 2 }}>
        {t('givenName', 'Given name')}
        {t('familyName', 'Family name')}
        <TextField select label="Sex" value={f.sex} onChange={(e) => setF({ ...f, sex: e.target.value })}>
          <MenuItem value="FEMALE">Female</MenuItem><MenuItem value="MALE">Male</MenuItem><MenuItem value="OTHER">Other</MenuItem><MenuItem value="UNKNOWN">Not stated</MenuItem>
        </TextField>
        {t('birthDate', 'Date of birth', 'date')}
        {t('mobile', 'Mobile')}
        {t('email', 'Email')}
        {t('addressLine', 'Address')}
        {t('city', 'City')}
        {t('state', 'State')}
        {t('pincode', 'PIN code')}
        {t('abhaNumber', 'ABHA number')}
        {t('guardianName', 'Guardian')}
        {t('emergencyContactName', 'Emergency contact')}
        {t('emergencyContactPhone', 'Emergency contact phone')}
      </Box>
      <Stack />
    </FormDialog>
  );
}
