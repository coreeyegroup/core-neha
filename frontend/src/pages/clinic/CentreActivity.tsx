import { Box, Button, Chip, Stack, Tab, Tabs, TextField, Typography } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate } from 'react-router';

import * as api from '../../api/generated/core-neha';
import { useSession } from '../../auth/session';
import { DataTable } from '../../components/DataTable';
import { PageHeader } from '../../components/PageHeader';
import { ProblemAlert } from '../../components/ProblemAlert';
import { Section } from '../../components/Section';
import { addDays, money, MyPositions, NeedPermission, StatCard, StatRow, todayIst, useWorkingCentre, waited } from './common';
import { useBoard, useVisits, VisitTable } from './ReceptionDesk';
import { SurgeryTable, useSurgeries } from './Surgery';

/**
 * Centre activity: one page for the people who run the centre (centre director, medical
 * director, operations manager). Everything happening at the working centre on a day: the
 * patient flow, every visit, surgeries, money and staffing. Each part shows only what the
 * person's own roles allow; nothing here bypasses the access rules.
 */
export function CentreActivity() {
  const session = useSession();
  const navigate = useNavigate();
  const { centreId, centreName } = useWorkingCentre();
  const at = (p: string) => session.hasAtCentre(p, centreId);
  const canFlow = at('opd.queue.read') || at('eye.clinical.read');
  const canClinical = at('eye.clinical.read');
  const canMoney = at('billing.report.read');
  const canStaff = at('workforce.read');
  const [date, setDate] = useState(todayIst());
  const [tab, setTab] = useState('visits');

  const board = useBoard(canFlow ? centreId : undefined, date);
  const visits = useVisits(canFlow ? centreId : undefined, undefined, date);
  const otList = useSurgeries(canClinical ? centreId : undefined, 'OT_LIST', date);
  const upcoming = useSurgeries(canClinical ? centreId : undefined, 'UPCOMING', date);
  const toCounsel = useSurgeries(canClinical ? centreId : undefined, 'COUNSELLING');
  const toSchedule = useSurgeries(canClinical ? centreId : undefined, 'SCHEDULING');
  const money_ = useQuery({ queryKey: ['collections', centreId, date, date], queryFn: () => api.collections(centreId!, { from: date, to: date }), enabled: !!centreId && canMoney });
  const staff = useQuery({ queryKey: ['establishments', centreId], queryFn: () => api.establishments(centreId!), enabled: !!centreId && canStaff });
  const b = board.data;
  const m = money_.data;
  const posted = (staff.data ?? []).reduce((n, e) => n + (e.activePostings ?? 0), 0);
  const approved = (staff.data ?? []).reduce((n, e) => n + (e.approvedHeadcount ?? 0), 0);

  return (
    <>
      <PageHeader title="Centre activity" subtitle={<>{centreName} · patient flow, visits, surgeries, collections and staffing for one day</>} />
      <MyPositions />
      <Box sx={{ mt: 2 }} />
      <NeedPermission ok={!!centreId && (canFlow || canMoney || canStaff)} what="access to this centre's activity">
        <Stack direction="row" spacing={1} sx={{ mb: 2, flexWrap: 'wrap', gap: 1, alignItems: 'center' }}>
          <TextField label="Day" type="date" value={date} onChange={(e) => setDate(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} sx={{ width: 190 }} />
          <Button onClick={() => setDate(todayIst())}>Today</Button>
          <Button onClick={() => setDate(addDays(date, -1))}>Previous day</Button>
          <Button onClick={() => setDate(addDays(date, 1))}>Next day</Button>
        </Stack>

        {canFlow && (
          <>
            <Typography variant="h3" sx={{ mb: 1 }}>Patient flow</Typography>
            <StatRow>
              <StatCard label="Visits" value={b?.visits ?? 0} hint={`${b?.newPatients ?? 0} new patients`} />
              <StatCard label="Appointments" value={b?.appointmentsBooked ?? 0} hint={`${b?.appointmentsArrived ?? 0} arrived, ${b?.appointmentsCancelled ?? 0} cancelled`} />
              <StatCard label="Waiting now" value={(b?.waitingOptometry ?? 0) + (b?.waitingConsultation ?? 0) + (b?.waitingCounselling ?? 0) + (b?.waitingCheckout ?? 0)} tone="warning"
                hint={`optometry ${b?.waitingOptometry ?? 0}, doctor ${b?.waitingConsultation ?? 0}, counselling ${b?.waitingCounselling ?? 0}, checkout ${b?.waitingCheckout ?? 0}`} />
              <StatCard label="Completed" value={b?.completed ?? 0} tone="success" hint={`${b?.cancelled ?? 0} cancelled`} />
              <StatCard label="Average wait" value={waited(b?.averageWaitMinutes)} hint={`longest ${waited(b?.longestWaitMinutes)}`} />
              <StatCard label="Surgeries" value={b?.surgeriesToday ?? 0} tone="info" hint={`${b?.surgeriesCompletedToday ?? 0} done, ${b?.surgeriesReadyToday ?? 0} ready, ${b?.surgeriesUpcoming ?? 0} booked ahead`} />
            </StatRow>
            <ProblemAlert error={board.error} />
          </>
        )}

        {canMoney && (
          <>
            <Typography variant="h3" sx={{ mb: 1, mt: 1 }}>Money</Typography>
            <StatRow>
              <StatCard label="Billed" value={money(m?.billed)} hint={`${m?.bills ?? 0} bills`} />
              <StatCard label="Collected" value={money(m?.collected)} tone="success" />
              <StatCard label="Discounts" value={money(m?.discounts)} hint={`${m?.pendingDiscounts ?? 0} waiting for approval`} tone={(m?.pendingDiscounts ?? 0) > 0 ? 'warning' : undefined} />
              <StatCard label="Outstanding (all open bills)" value={money(m?.outstanding)} tone="warning" />
              {(m?.byMode ?? []).slice(0, 2).map((r) => <StatCard key={r.label} label={`Collected by ${r.label?.toLowerCase()}`} value={money(r.amount)} />)}
            </StatRow>
            <ProblemAlert error={money_.error} />
          </>
        )}

        {canStaff && (
          <>
            <Typography variant="h3" sx={{ mb: 1, mt: 1 }}>Staffing</Typography>
            <StatRow>
              <StatCard label="Positions" value={(staff.data ?? []).length} />
              <StatCard label="People posted" value={posted} hint={`of ${approved} approved`} tone={posted < approved ? 'warning' : 'success'} />
              <StatCard label="Vacancies" value={Math.max(0, approved - posted)} tone={approved > posted ? 'warning' : undefined} />
            </StatRow>
          </>
        )}

        <Tabs value={tab} onChange={(_, v: string) => setTab(v)} sx={{ mb: 2, mt: 1, borderBottom: 1, borderColor: 'divider' }} variant="scrollable">
          {canFlow && <Tab value="visits" label={`Visits (${(visits.data ?? []).length})`} />}
          {canClinical && <Tab value="surgery" label={`Surgeries (${(otList.data ?? []).length})`} />}
          {canClinical && <Tab value="pipeline" label={`Surgery pipeline (${(toCounsel.data ?? []).length + (toSchedule.data ?? []).length})`} />}
          {canMoney && <Tab value="outstanding" label={`Outstanding bills (${m?.outstandingBills?.length ?? 0})`} />}
          {canStaff && <Tab value="staff" label="Staff and positions" />}
        </Tabs>

        {tab === 'visits' && canFlow && (
          <>
            <ProblemAlert error={visits.error} />
            <VisitTable rows={visits.data} loading={visits.isLoading} empty="No visits on this day." />
          </>
        )}
        {tab === 'surgery' && canClinical && (
          <>
            <SurgeryTable rows={otList.data} loading={otList.isLoading} empty="No surgeries on this day." showDate={false} />
            <Section title="Booked ahead">
              <SurgeryTable rows={(upcoming.data ?? []).filter((r) => r.surgeryDate !== date)} loading={upcoming.isLoading} empty="Nothing booked after this day." />
            </Section>
          </>
        )}
        {tab === 'pipeline' && canClinical && (
          <>
            <Section title="Advised, waiting for counselling">
              <SurgeryTable rows={toCounsel.data} loading={toCounsel.isLoading} empty="Nobody is waiting for counselling." />
            </Section>
            <Section title="Accepted, waiting for a date">
              <SurgeryTable rows={toSchedule.data} loading={toSchedule.isLoading} empty="Nobody is waiting for a date." />
            </Section>
          </>
        )}
        {tab === 'outstanding' && canMoney && (
          <DataTable rows={m?.outstandingBills} rowKey={(r) => r.id ?? ''} loading={money_.isLoading} empty="No open balances."
            onRowClick={(r) => navigate(`/bills/${r.id}`)}
            columns={[
              { key: 'no', header: 'Bill', render: (r) => r.billNo, value: (r) => r.billNo },
              { key: 'patient', header: 'Patient', render: (r) => <>{r.patientName} <Typography component="span" variant="caption" color="text.secondary">{r.patientUhid}</Typography></>, value: (r) => `${r.patientName} ${r.patientUhid}` },
              { key: 'net', header: 'Net', align: 'right', render: (r) => money(r.netAmount), value: (r) => r.netAmount },
              { key: 'balance', header: 'Balance', align: 'right', render: (r) => money(r.balance), value: (r) => r.balance },
            ]} />
        )}
        {tab === 'staff' && canStaff && (
          <DataTable rows={staff.data} rowKey={(r) => r.id ?? ''} loading={staff.isLoading} empty="No positions at this centre."
            columns={[
              { key: 'dept', header: 'Department', render: (r) => r.departmentName ?? 'Centre management', value: (r) => r.departmentName ?? 'Centre management' },
              { key: 'pos', header: 'Position', render: (r) => r.positionTitle, value: (r) => r.positionTitle },
              { key: 'filled', header: 'Posted', align: 'right', render: (r) => (
                <Chip size="small" label={`${r.activePostings} of ${r.approvedHeadcount}`} color={(r.activePostings ?? 0) < (r.approvedHeadcount ?? 0) ? 'warning' : 'success'} variant="outlined" />
              ), value: (r) => r.activePostings },
            ]} />
        )}
      </NeedPermission>
    </>
  );
}
