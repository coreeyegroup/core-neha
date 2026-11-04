import { Box, Button, Stack, Tab, Tabs, TextField, Typography } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router';

import * as api from '../../api/generated/core-neha';
import { useSession } from '../../auth/session';
import { DataTable } from '../../components/DataTable';
import { PageHeader } from '../../components/PageHeader';
import { ProblemAlert } from '../../components/ProblemAlert';
import { StatusChip } from '../../components/StatusChip';
import { BillActions } from './BillActions';
import { hhmm, MyPositions, NeedPermission, PatientName, StageChip, StatCard, StatRow, todayIst, useWorkingCentre, waited } from './common';
import { BookAppointmentDialog, CheckInDialog, PatientPicker, ReasonDialog, RegisterPatientDialog, WalkInDialog } from './dialogs';

export function useBoard(centreId: string | undefined, date?: string) {
  return useQuery({ queryKey: ['clinic-board', centreId, date], queryFn: () => api.clinicBoard(centreId!, { date }), enabled: !!centreId, refetchInterval: 30_000 });
}

export function useVisits(centreId: string | undefined, stage?: string, date?: string) {
  return useQuery({ queryKey: ['visits', centreId, stage, date], queryFn: () => api.visits(centreId!, { stage, date }), enabled: !!centreId, refetchInterval: 20_000 });
}

/** Front office: find or register patients, appointments, check-in, the queue and checkout. */
export function ReceptionDesk() {
  const session = useSession();
  const { centreId, centreName } = useWorkingCentre();
  const ok = session.hasAtCentre('opd.checkin', centreId) || session.hasAtCentre('opd.appointment.manage', centreId);
  const [tab, setTab] = useState('appointments');
  const [dialog, setDialog] = useState<'register' | 'book' | 'walkin' | undefined>();
  const [found, setFound] = useState<api.PatientView | undefined>();
  const board = useBoard(centreId);
  const navigate = useNavigate();
  const b = board.data;
  return (
    <>
      <PageHeader title="Reception desk" subtitle={<>{centreName} · patient registration, appointments, check-in and checkout</>}
        actions={ok && centreId ? (
          <>
            {session.hasAtCentre('patient.register', centreId) && <Button onClick={() => setDialog('register')}>Register patient</Button>}
            {session.hasAtCentre('opd.appointment.manage', centreId) && <Button onClick={() => setDialog('book')}>Book appointment</Button>}
            {session.hasAtCentre('opd.checkin', centreId) && <Button variant="contained" onClick={() => setDialog('walkin')}>Walk-in check-in</Button>}
          </>
        ) : undefined} />
      <MyPositions />
      <Box sx={{ mt: 2 }} />
      <NeedPermission ok={ok && !!centreId} what="front-office access">
        <StatRow>
          <StatCard label="Appointments booked" value={b?.appointmentsBooked ?? '–'} hint="not yet arrived" />
          <StatCard label="Arrived" value={b?.appointmentsArrived ?? '–'} hint={`${b?.visits ?? 0} visits today`} tone="info" />
          <StatCard label="In the clinic" value={(b?.waitingOptometry ?? 0) + (b?.waitingConsultation ?? 0) + (b?.waitingCounselling ?? 0)}
            hint={`longest wait ${waited(b?.longestWaitMinutes)}`} />
          <StatCard label="At checkout" value={b?.waitingCheckout ?? '–'} tone="warning" hint="bill and send home" />
          <StatCard label="Completed" value={b?.completed ?? '–'} tone="success" />
          <StatCard label="New patients" value={b?.newPatients ?? '–'} hint="registered today" />
        </StatRow>
        <Box sx={{ mb: 2, maxWidth: 640 }}>
          <PatientPicker value={found} onChange={setFound} label="Find a patient" required={false} />
          {found && (
            <Stack direction="row" spacing={1} sx={{ mt: 1, flexWrap: 'wrap' }}>
              <Typography variant="body2" sx={{ alignSelf: 'center' }}>{found.fullName} · {found.uhid} · {found.mobile}</Typography>
              <Button size="small" onClick={() => navigate(`/patients/${found.id}`)}>Open record</Button>
              {session.hasAtCentre('opd.checkin', centreId) && <Button size="small" variant="contained" onClick={() => setDialog('walkin')}>Check in now</Button>}
              {session.hasAtCentre('opd.appointment.manage', centreId) && <Button size="small" onClick={() => setDialog('book')}>Book appointment</Button>}
            </Stack>
          )}
        </Box>
        <Tabs value={tab} onChange={(_, v: string) => setTab(v)} sx={{ mb: 2, borderBottom: 1, borderColor: 'divider' }} variant="scrollable">
          <Tab value="appointments" label="Appointments" />
          <Tab value="queue" label={`Today's queue (${b?.visits ?? 0})`} />
          <Tab value="checkout" label={`Checkout (${b?.waitingCheckout ?? 0})`} />
        </Tabs>
        {tab === 'appointments' && centreId && <Appointments centreId={centreId} />}
        {tab === 'queue' && centreId && <Queue centreId={centreId} />}
        {tab === 'checkout' && centreId && <Checkout centreId={centreId} />}
      </NeedPermission>
      {dialog === 'register' && centreId && <RegisterPatientDialog centreId={centreId} onClose={() => setDialog(undefined)} onDone={setFound} />}
      {dialog === 'book' && centreId && <BookAppointmentDialog centreId={centreId} patient={found} onClose={() => setDialog(undefined)} />}
      {dialog === 'walkin' && centreId && <WalkInDialog centreId={centreId} patient={found} onClose={() => setDialog(undefined)} />}
    </>
  );
}

function Appointments({ centreId }: { centreId: string }) {
  const session = useSession();
  const [date, setDate] = useState(todayIst());
  const list = useQuery({ queryKey: ['appointments', centreId, date], queryFn: () => api.appointments(centreId, { date }) });
  const [checkIn, setCheckIn] = useState<api.AppointmentView | undefined>();
  const [cancel, setCancel] = useState<api.AppointmentView | undefined>();
  const navigate = useNavigate();
  const isToday = date === todayIst();
  return (
    <>
      <TextField label="Date" type="date" value={date} onChange={(e) => setDate(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} sx={{ mb: 1, width: 200 }} />
      <ProblemAlert error={list.error} />
      <DataTable
        rows={list.data}
        loading={list.isLoading}
        rowKey={(r) => r.id ?? ''}
        filterPlaceholder="Filter by name, UHID or doctor"
        empty="No appointments on this day."
        onRowClick={(r) => (r.visitId ? navigate(`/visits/${r.visitId}`) : navigate(`/patients/${r.patientId}`))}
        columns={[
          { key: 'time', header: 'Time', render: (r) => hhmm(r.slotTime), value: (r) => r.slotTime, width: 70 },
          { key: 'patient', header: 'Patient', render: (r) => <PatientName name={r.patientName} uhid={r.patientUhid} age={r.patientAge} sex={r.patientSex} />, value: (r) => `${r.patientName} ${r.patientUhid}` },
          { key: 'mobile', header: 'Mobile', render: (r) => r.patientMobile ?? '—' },
          { key: 'type', header: 'Visit', render: (r) => (r.visitType ?? '').replace('_', '-').toLowerCase(), value: (r) => r.visitType },
          { key: 'doctor', header: 'Doctor', render: (r) => r.consultantName ?? 'Any', value: (r) => r.consultantName },
          { key: 'reason', header: 'Reason', render: (r) => r.reason ?? '—' },
          { key: 'status', header: 'Status', render: (r) => <StatusChip status={r.status} />, value: (r) => r.status },
          {
            key: 'actions', header: '', align: 'right',
            render: (r) => r.status === 'BOOKED' ? (
              <Stack direction="row" spacing={0.5} sx={{ justifyContent: 'flex-end' }}>
                {isToday && session.hasAtCentre('opd.checkin', centreId) && (
                  <Button size="small" variant="contained" onClick={(e) => { e.stopPropagation(); setCheckIn(r); }}>Check in</Button>
                )}
                {session.hasAtCentre('opd.appointment.manage', centreId) && (
                  <Button size="small" onClick={(e) => { e.stopPropagation(); setCancel(r); }}>Cancel…</Button>
                )}
              </Stack>
            ) : null,
          },
        ]}
      />
      {checkIn && <CheckInDialog appointment={checkIn} centreId={centreId} onClose={() => setCheckIn(undefined)} />}
      {cancel && (
        <ReasonDialog title={`Cancel appointment · ${cancel.patientName}`} submitLabel="Cancel appointment" onClose={() => setCancel(undefined)}
          extra="The patient did not come (record as no-show)" success="Appointment updated"
          run={(reason, noShow) => api.cancelAppointment(cancel.id!, { reason, noShow, expectedVersion: cancel.version ?? 0 })} />
      )}
    </>
  );
}

export function VisitTable({ rows, loading, empty, extra }: { rows?: api.VisitView[]; loading?: boolean; empty: string; extra?: (r: api.VisitView) => ReactNode }) {
  const navigate = useNavigate();
  return (
    <DataTable
      rows={rows}
      loading={loading}
      rowKey={(r) => r.id ?? ''}
      filterPlaceholder="Filter by name, UHID or token"
      empty={empty}
      onRowClick={(r) => navigate(`/visits/${r.id}`)}
      columns={[
        { key: 'token', header: 'Token', render: (r) => <Typography variant="h3" component="span">{r.tokenNo}</Typography>, value: (r) => r.tokenNo, width: 64 },
        { key: 'patient', header: 'Patient', render: (r) => <PatientName name={r.patientName} uhid={r.patientUhid} age={r.patientAge} sex={r.patientSex} />, value: (r) => `${r.patientName} ${r.patientUhid}` },
        { key: 'type', header: 'Visit', render: (r) => (r.visitType ?? '').replace('_', '-').toLowerCase(), value: (r) => r.visitType },
        { key: 'complaint', header: 'Complaint', render: (r) => r.chiefComplaint ?? '—' },
        { key: 'doctor', header: 'Doctor', render: (r) => r.consultantName ?? '—', value: (r) => r.consultantName },
        { key: 'stage', header: 'Stage', render: (r) => <StageChip stage={r.stage} />, value: (r) => r.stage },
        { key: 'wait', header: 'Waiting', align: 'right', render: (r) => waited(r.waitingMinutes), value: (r) => r.waitingMinutes ?? -1 },
        ...(extra ? [{ key: 'extra', header: '', align: 'right' as const, render: extra }] : []),
      ]}
    />
  );
}

function Queue({ centreId }: { centreId: string }) {
  const visits = useVisits(centreId);
  return (
    <>
      <ProblemAlert error={visits.error} />
      <VisitTable rows={visits.data} loading={visits.isLoading} empty="No patients checked in today." />
    </>
  );
}

function Checkout({ centreId }: { centreId: string }) {
  const visits = useVisits(centreId, 'CHECKOUT');
  return (
    <>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
        Patients whose consultation (and counselling) is finished. Raise the bill, collect payment, then complete the checkout.
      </Typography>
      <ProblemAlert error={visits.error} />
      <VisitTable rows={visits.data} loading={visits.isLoading} empty="Nobody is waiting at checkout."
        extra={(r) => <BillActions visit={r} />} />
    </>
  );
}
