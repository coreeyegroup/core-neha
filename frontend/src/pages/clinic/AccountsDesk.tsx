import { Alert, Box, Button, Grid, MenuItem, Paper, Stack, Tab, Table, TableBody, TableCell, TableHead, TableRow, Tabs, TextField, Typography } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate } from 'react-router';

import * as api from '../../api/generated/core-neha';
import { useAction } from '../../api/useAction';
import { useSession } from '../../auth/session';
import { DataTable } from '../../components/DataTable';
import { FormDialog } from '../../components/FormDialog';
import { PageHeader } from '../../components/PageHeader';
import { ProblemAlert } from '../../components/ProblemAlert';
import { Section } from '../../components/Section';
import { StatusChip } from '../../components/StatusChip';
import { DecideDiscountDialog } from './BillPage';
import { addDays, clock, dateLabel, money, MyPositions, NeedPermission, StatCard, StatRow, todayIst, useWorkingCentre } from './common';

/** Accounts: collections by mode and service, outstanding balances, discount approvals, day close and the price list. */
export function AccountsDesk() {
  const session = useSession();
  const { centreId, centreName } = useWorkingCentre();
  const ok = session.hasAtCentre('billing.report.read', centreId) || session.hasAtCentre('billing.read', centreId);
  const [tab, setTab] = useState('collections');
  const [from, setFrom] = useState(todayIst());
  const [to, setTo] = useState(todayIst());
  const canReport = session.hasAtCentre('billing.report.read', centreId);
  const report = useQuery({ queryKey: ['collections', centreId, from, to], queryFn: () => api.collections(centreId!, { from, to }), enabled: !!centreId && canReport });
  const r = report.data;
  return (
    <>
      <PageHeader title="Accounts" subtitle={<>{centreName} · collections, outstanding, discounts, day close and the price list</>} />
      <MyPositions />
      <Box sx={{ mt: 2 }} />
      <NeedPermission ok={ok && !!centreId} what="accounts access">
        <Stack direction="row" spacing={1} sx={{ mb: 2, flexWrap: 'wrap', gap: 1 }}>
          <TextField label="From" type="date" value={from} onChange={(e) => setFrom(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} sx={{ width: 190 }} />
          <TextField label="To" type="date" value={to} onChange={(e) => setTo(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} sx={{ width: 190 }} />
          <Button onClick={() => { setFrom(todayIst()); setTo(todayIst()); }}>Today</Button>
          <Button onClick={() => { setFrom(addDays(todayIst(), -6)); setTo(todayIst()); }}>Last 7 days</Button>
          <Button onClick={() => { setFrom(`${todayIst().slice(0, 8)}01`); setTo(todayIst()); }}>This month</Button>
        </Stack>
        {canReport && (
          <StatRow>
            <StatCard label="Billed" value={money(r?.billed)} hint={`${r?.bills ?? 0} bills`} />
            <StatCard label="Discounts" value={money(r?.discounts)} />
            <StatCard label="Collected" value={money(r?.collected)} tone="success" />
            <StatCard label="Outstanding (all open bills)" value={money(r?.outstanding)} tone="warning" />
            <StatCard label="Discounts to decide" value={r?.pendingDiscounts ?? 0} tone={(r?.pendingDiscounts ?? 0) > 0 ? 'warning' : undefined} />
          </StatRow>
        )}
        <ProblemAlert error={report.error} />
        <Tabs value={tab} onChange={(_, v: string) => setTab(v)} sx={{ mb: 2, borderBottom: 1, borderColor: 'divider' }} variant="scrollable">
          {canReport && <Tab value="collections" label="Collections" />}
          <Tab value="bills" label="Bills" />
          {canReport && <Tab value="outstanding" label={`Outstanding (${r?.outstandingBills?.length ?? 0})`} />}
          {session.hasAtCentre('billing.discount.approve', centreId) && <Tab value="discounts" label={`Discounts (${r?.pendingDiscounts ?? 0})`} />}
          {(session.hasAtCentre('billing.dayclose.submit', centreId) || session.hasAtCentre('billing.dayclose.approve', centreId)) && <Tab value="dayclose" label="Day close" />}
          <Tab value="tariff" label="Price list" />
        </Tabs>
        {tab === 'collections' && r && <Collections report={r} />}
        {tab === 'bills' && centreId && <Bills centreId={centreId} from={from} to={to} />}
        {tab === 'outstanding' && <BillTable rows={r?.outstandingBills} empty="No balances outstanding." />}
        {tab === 'discounts' && centreId && <Discounts centreId={centreId} />}
        {tab === 'dayclose' && centreId && <DayClose centreId={centreId} />}
        {tab === 'tariff' && centreId && <PriceList centreId={centreId} />}
      </NeedPermission>
    </>
  );
}

function Breakdown({ title, rows, labels }: { title: string; rows?: api.AmountRow[]; labels?: (s?: string) => string }) {
  const total = (rows ?? []).reduce((s, x) => s + (x.amount ?? 0), 0);
  return (
    <Section title={title}>
      <Paper>
        <Table size="small">
          <TableBody>
            {(rows ?? []).length === 0 && <TableRow><TableCell><Typography variant="body2" color="text.secondary">Nothing in this period.</Typography></TableCell></TableRow>}
            {(rows ?? []).map((x) => (
              <TableRow key={x.label}>
                <TableCell>{labels ? labels(x.label) : x.label}</TableCell>
                <TableCell align="right">{x.count}</TableCell>
                <TableCell align="right">{money(x.amount)}</TableCell>
                <TableCell align="right" sx={{ width: 64, color: 'text.secondary' }}>{total ? `${Math.round(((x.amount ?? 0) * 100) / total)}%` : ''}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Paper>
    </Section>
  );
}

const lower = (s?: string) => (s ?? '').replaceAll('_', ' ').toLowerCase();

function Collections({ report }: { report: api.CollectionReport }) {
  return (
    <Grid container spacing={2}>
      <Grid size={{ xs: 12, md: 4 }}><Breakdown title="By payment mode" rows={report.byMode} labels={lower} /></Grid>
      <Grid size={{ xs: 12, md: 4 }}><Breakdown title="By service" rows={report.byCategory} labels={lower} /></Grid>
      <Grid size={{ xs: 12, md: 4 }}><Breakdown title="By collector" rows={report.byCollector} /></Grid>
      <Grid size={12}>
        <Section title="Day by day">
          <Paper>
            <Table size="small">
              <TableHead><TableRow><TableCell>Date</TableCell><TableCell align="right">Bills</TableCell><TableCell align="right">Billed (net)</TableCell><TableCell align="right">Collected</TableCell></TableRow></TableHead>
              <TableBody>
                {(report.byDay ?? []).map((d) => (
                  <TableRow key={d.date}><TableCell>{dateLabel(d.date)}</TableCell><TableCell align="right">{d.bills}</TableCell><TableCell align="right">{money(d.billed)}</TableCell><TableCell align="right">{money(d.collected)}</TableCell></TableRow>
                ))}
              </TableBody>
            </Table>
          </Paper>
        </Section>
      </Grid>
    </Grid>
  );
}

function BillTable({ rows, loading, empty }: { rows?: api.BillView[]; loading?: boolean; empty: string }) {
  const navigate = useNavigate();
  return (
    <DataTable
      rows={rows}
      loading={loading}
      rowKey={(b) => b.id ?? ''}
      filterPlaceholder="Filter by bill number or patient"
      empty={empty}
      onRowClick={(b) => navigate(`/bills/${b.id}`)}
      columns={[
        { key: 'no', header: 'Bill', render: (b) => b.billNo, value: (b) => b.billNo },
        { key: 'date', header: 'Date', render: (b) => dateLabel(b.billDate), value: (b) => b.billDate },
        { key: 'patient', header: 'Patient', render: (b) => `${b.patientName} · ${b.patientUhid}`, value: (b) => `${b.patientName} ${b.patientUhid}` },
        { key: 'net', header: 'Net', align: 'right', render: (b) => money(b.netAmount), value: (b) => b.netAmount },
        { key: 'paid', header: 'Paid', align: 'right', render: (b) => money(b.paidAmount), value: (b) => b.paidAmount },
        { key: 'balance', header: 'Balance', align: 'right', render: (b) => money(b.balance), value: (b) => b.balance },
        { key: 'discount', header: 'Discount', render: (b) => (b.discountStatus === 'NONE' ? '—' : `${lower(b.discountStatus)} ${money(b.discountRequested)}`) },
        { key: 'by', header: 'Raised by', render: (b) => b.createdByName },
        { key: 'status', header: 'Status', render: (b) => <StatusChip status={b.status} />, value: (b) => b.status },
      ]}
    />
  );
}

function Bills({ centreId, from, to }: { centreId: string; from: string; to: string }) {
  const [status, setStatus] = useState('');
  const bills = useQuery({ queryKey: ['bills', centreId, from, to, status], queryFn: () => api.bills(centreId, { from, to, status: status || undefined }) });
  return (
    <>
      <TextField select label="Status" value={status} onChange={(e) => setStatus(e.target.value)} sx={{ mb: 1, width: 200 }}>
        <MenuItem value="">All</MenuItem>
        <MenuItem value="OPEN">Open</MenuItem>
        <MenuItem value="PAID">Paid</MenuItem>
        <MenuItem value="CANCELLED">Cancelled</MenuItem>
      </TextField>
      <ProblemAlert error={bills.error} />
      <BillTable rows={bills.data} loading={bills.isLoading} empty="No bills in this period." />
    </>
  );
}

function Discounts({ centreId }: { centreId: string }) {
  const pending = useQuery({ queryKey: ['discounts', centreId], queryFn: () => api.pendingDiscounts(centreId) });
  const [deciding, setDeciding] = useState<api.BillView | undefined>();
  const { me } = useSession();
  return (
    <>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>Discounts requested at the desk. You cannot decide a discount you requested yourself.</Typography>
      <ProblemAlert error={pending.error} />
      <DataTable
        rows={pending.data}
        loading={pending.isLoading}
        rowKey={(b) => b.id ?? ''}
        empty="No discounts waiting."
        columns={[
          { key: 'no', header: 'Bill', render: (b) => b.billNo },
          { key: 'patient', header: 'Patient', render: (b) => b.patientName },
          { key: 'gross', header: 'Bill amount', align: 'right', render: (b) => money((b.grossAmount ?? 0) + (b.taxAmount ?? 0)) },
          { key: 'disc', header: 'Discount', align: 'right', render: (b) => money(b.discountRequested) },
          { key: 'reason', header: 'Reason', render: (b) => b.discountReason },
          { key: 'by', header: 'Requested by', render: (b) => b.discountRequestedByName },
          {
            key: 'act', header: '', align: 'right',
            render: (b) => b.discountRequestedByName === me.displayName ? <Typography variant="caption">Your request</Typography> : <Button size="small" variant="contained" onClick={() => setDeciding(b)}>Decide…</Button>,
          },
        ]}
      />
      {deciding && <DecideDiscountDialog bill={deciding} onClose={() => setDeciding(undefined)} />}
    </>
  );
}

function DayClose({ centreId }: { centreId: string }) {
  const session = useSession();
  const [date, setDate] = useState(todayIst());
  const preview = useQuery({ queryKey: ['day-close', centreId, date], queryFn: () => api.dayClosePreview(centreId, { date }) });
  const history = useQuery({ queryKey: ['day-close', centreId, 'list'], queryFn: () => api.dayCloses(centreId) });
  const [counted, setCounted] = useState('');
  const [notes, setNotes] = useState('');
  const [deciding, setDeciding] = useState<api.DayCloseView | undefined>();
  const p = preview.data;
  const variance = counted === '' ? undefined : Number(counted) - (p?.systemCash ?? 0);
  const submit = useAction(() => api.submitDayClose(centreId, { businessDate: date, countedCash: Number(counted), notes: notes || undefined }),
    { invalidate: [['day-close']], success: 'Day close submitted for approval' });
  const canSubmit = session.hasAtCentre('billing.dayclose.submit', centreId);
  return (
    <Grid container spacing={2}>
      <Grid size={{ xs: 12, md: 5 }}>
        <Section title="Close a day" description="Count the cash drawer and compare it with the system. Another person approves.">
          <Paper sx={{ p: 2 }}>
            <TextField label="Business day" type="date" value={date} onChange={(e) => setDate(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} sx={{ mb: 2, width: 190 }} />
            <Table size="small">
              <TableBody>
                <TableRow><TableCell>Cash (system)</TableCell><TableCell align="right">{money(p?.systemCash)}</TableCell></TableRow>
                <TableRow><TableCell>Card</TableCell><TableCell align="right">{money(p?.systemCard)}</TableCell></TableRow>
                <TableRow><TableCell>UPI</TableCell><TableCell align="right">{money(p?.systemUpi)}</TableCell></TableRow>
                <TableRow><TableCell>Insurance and corporate</TableCell><TableCell align="right">{money(p?.systemOther)}</TableCell></TableRow>
                <TableRow><TableCell>Payments</TableCell><TableCell align="right">{p?.payments ?? 0}</TableCell></TableRow>
              </TableBody>
            </Table>
            {p?.existing ? (
              <Alert severity={p.existing.status === 'APPROVED' ? 'success' : 'info'} sx={{ mt: 2 }}>
                {p.existing.status === 'APPROVED' ? 'Closed' : 'Submitted'} by {p.existing.submittedByName}: counted {money(p.existing.countedCash)}, difference {money(p.existing.variance)}.
              </Alert>
            ) : canSubmit ? (
              <Stack spacing={2} sx={{ mt: 2 }}>
                <TextField label="Cash counted (₹)" type="number" value={counted} onChange={(e) => setCounted(e.target.value)} required />
                {variance !== undefined && (
                  <Alert severity={variance === 0 ? 'success' : 'warning'}>Difference: {money(variance)}{variance !== 0 ? ' — explain it in the notes' : ''}</Alert>
                )}
                <TextField label="Notes" value={notes} onChange={(e) => setNotes(e.target.value)} multiline />
                <Button variant="contained" onClick={() => submit.mutate(undefined)} disabled={submit.isPending || counted === ''}>Submit day close</Button>
                <ProblemAlert error={submit.error} />
              </Stack>
            ) : (
              <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>Not submitted yet.</Typography>
            )}
          </Paper>
        </Section>
      </Grid>
      <Grid size={{ xs: 12, md: 7 }}>
        <Section title="Day closes">
          <DataTable
            rows={history.data}
            loading={history.isLoading}
            rowKey={(d) => d.id ?? ''}
            empty="No day closes yet."
            columns={[
              { key: 'date', header: 'Day', render: (d) => dateLabel(d.businessDate), value: (d) => d.businessDate },
              { key: 'cash', header: 'System cash', align: 'right', render: (d) => money(d.systemCash) },
              { key: 'counted', header: 'Counted', align: 'right', render: (d) => money(d.countedCash) },
              { key: 'var', header: 'Difference', align: 'right', render: (d) => money(d.variance) },
              { key: 'by', header: 'Submitted by', render: (d) => `${d.submittedByName} ${clock(d.submittedAt)}` },
              { key: 'status', header: 'Status', render: (d) => <StatusChip status={d.status} /> },
              {
                key: 'act', header: '', align: 'right',
                render: (d) => d.status === 'SUBMITTED' && session.hasAtCentre('billing.dayclose.approve', centreId)
                  ? <Button size="small" variant="contained" onClick={() => setDeciding(d)}>Review…</Button> : d.decidedByName ? <Typography variant="caption">{d.decidedByName}</Typography> : null,
              },
            ]}
          />
        </Section>
      </Grid>
      {deciding && <DecideDayClose d={deciding} onClose={() => setDeciding(undefined)} />}
    </Grid>
  );
}

function DecideDayClose({ d, onClose }: { d: api.DayCloseView; onClose: () => void }) {
  const [note, setNote] = useState('');
  const decide = useAction((approve: boolean) => api.decideDayClose(d.id!, { approve, note: note || undefined, expectedVersion: d.version ?? 0 }),
    { invalidate: [['day-close']], success: (r) => `Day close ${r.status?.toLowerCase()}`, onDone: onClose });
  return (
    <FormDialog open title={`Day close · ${dateLabel(d.businessDate)}`} submitLabel="Approve" onClose={onClose} onSubmit={() => decide.mutate(true)} busy={decide.isPending} error={decide.error}
      intro={<Typography variant="body2">System cash {money(d.systemCash)}, counted {money(d.countedCash)}, difference <b>{money(d.variance)}</b>. Submitted by {d.submittedByName}. {d.notes}</Typography>}>
      <TextField label="Decision note" value={note} onChange={(e) => setNote(e.target.value)} />
      <Button color="error" onClick={() => decide.mutate(false)} disabled={decide.isPending}>Reject (recount needed)</Button>
    </FormDialog>
  );
}

function PriceList({ centreId }: { centreId: string }) {
  const session = useSession();
  const canManage = session.hasAtCentre('billing.tariff.manage', centreId);
  const list = useQuery({ queryKey: ['tariff', centreId, 'all'], queryFn: () => api.tariff(centreId, { includeRetired: true }) });
  const [editing, setEditing] = useState<api.TariffItemView | 'new' | undefined>();
  return (
    <>
      {canManage && <Button variant="contained" sx={{ mb: 1 }} onClick={() => setEditing('new')}>Add price list item</Button>}
      <ProblemAlert error={list.error} />
      <DataTable
        rows={list.data}
        loading={list.isLoading}
        rowKey={(t) => t.id ?? ''}
        filterPlaceholder="Filter the price list"
        empty="No price list items."
        onRowClick={canManage ? (t) => setEditing(t) : undefined}
        columns={[
          { key: 'code', header: 'Code', render: (t) => t.code, value: (t) => t.code },
          { key: 'name', header: 'Service', render: (t) => t.name, value: (t) => t.name },
          { key: 'cat', header: 'Category', render: (t) => lower(t.category), value: (t) => t.category },
          { key: 'price', header: 'Price', align: 'right', render: (t) => money(t.price), value: (t) => t.price },
          { key: 'gst', header: 'GST', align: 'right', render: (t) => `${t.gstRate ?? 0}%` },
          { key: 'status', header: 'Status', render: (t) => <StatusChip status={t.status} /> },
        ]}
      />
      {editing && <TariffDialog centreId={centreId} item={editing === 'new' ? undefined : editing} onClose={() => setEditing(undefined)} />}
    </>
  );
}

function TariffDialog({ centreId, item, onClose }: { centreId: string; item?: api.TariffItemView; onClose: () => void }) {
  const [f, setF] = useState({ code: item?.code ?? '', name: item?.name ?? '', category: item?.category ?? 'INVESTIGATION', price: item?.price?.toString() ?? '', gstRate: item?.gstRate?.toString() ?? '0', status: item?.status ?? 'ACTIVE' });
  const save = useAction(() => (item
    ? api.updateTariffItem(centreId, item.id!, { name: f.name, price: Number(f.price), gstRate: Number(f.gstRate), status: f.status, expectedVersion: item.version ?? 0 })
    : api.createTariffItem(centreId, { code: f.code.toUpperCase(), name: f.name, category: f.category, price: Number(f.price), gstRate: Number(f.gstRate) })),
  { invalidate: [['tariff']], success: 'Price list updated', onDone: onClose });
  return (
    <FormDialog open title={item ? `Edit ${item.code}` : 'New price list item'} submitLabel="Save" onClose={onClose} onSubmit={() => save.mutate(undefined)} busy={save.isPending} error={save.error}>
      {!item && <TextField label="Code" value={f.code} onChange={(e) => setF({ ...f, code: e.target.value })} required placeholder="OCT_DISC" />}
      <TextField label="Service name" value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} required />
      {!item && (
        <TextField select label="Category" value={f.category} onChange={(e) => setF({ ...f, category: e.target.value })}>
          {['REGISTRATION', 'CONSULTATION', 'INVESTIGATION', 'PROCEDURE', 'SURGERY_PACKAGE', 'OPTICAL', 'PHARMACY', 'OTHER'].map((c) => <MenuItem key={c} value={c}>{lower(c)}</MenuItem>)}
        </TextField>
      )}
      <Stack direction="row" spacing={2}>
        <TextField label="Price (₹)" type="number" value={f.price} onChange={(e) => setF({ ...f, price: e.target.value })} required fullWidth />
        <TextField select label="GST" value={f.gstRate} onChange={(e) => setF({ ...f, gstRate: e.target.value })} fullWidth>
          {['0', '5', '12', '18', '28'].map((g) => <MenuItem key={g} value={g}>{g}%</MenuItem>)}
        </TextField>
      </Stack>
      {item && (
        <TextField select label="Status" value={f.status} onChange={(e) => setF({ ...f, status: e.target.value })}>
          <MenuItem value="ACTIVE">Active</MenuItem>
          <MenuItem value="RETIRED">Retired (no longer billed)</MenuItem>
        </TextField>
      )}
    </FormDialog>
  );
}
