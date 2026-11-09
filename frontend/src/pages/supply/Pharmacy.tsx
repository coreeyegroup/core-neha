import { Alert, Box, Button, Chip, IconButton, MenuItem, Stack, Tab, Tabs, TextField, Typography } from '@mui/material';
import DeleteOutlined from '@mui/icons-material/DeleteOutlined';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';

import * as api from '../../api/generated/core-neha';
import { useAction } from '../../api/useAction';
import { useSession } from '../../auth/session';
import { DataTable } from '../../components/DataTable';
import { FormDialog } from '../../components/FormDialog';
import { PageHeader } from '../../components/PageHeader';
import { ProblemAlert } from '../../components/ProblemAlert';
import { StatusChip } from '../../components/StatusChip';
import { clock, dateLabel, eyeLabel, money, MyPositions, NeedPermission, StatCard, StatRow, todayIst, useWorkingCentre } from '../clinic/common';
import { CollectPaymentDialog, PatientPicker } from '../clinic/dialogs';
import { ItemPicker, ScheduleChip, StoreSelect } from './common';

/** Pharmacy counter: prescriptions waiting, dispensing against a prescription or over the counter, the day's sales and returns. */
export function Pharmacy() {
  const session = useSession();
  const { centreId, centreName } = useWorkingCentre();
  const at = (p: string) => session.hasAtCentre(p, centreId);
  const ok = at('pharmacy.read') || at('pharmacy.dispense');
  const [storeId, setStoreId] = useState('');
  const [tab, setTab] = useState('queue');
  const [date, setDate] = useState(todayIst());
  const [dispenseFor, setDispenseFor] = useState<api.PrescriptionView | 'otc' | undefined>();
  const [returning, setReturning] = useState<api.SaleView | undefined>();
  const [collect, setCollect] = useState<string | undefined>();
  const rx = useQuery({ queryKey: ['prescriptions', centreId], queryFn: () => api.prescriptions(centreId!), enabled: ok && !!centreId });
  const sales = useQuery({ queryKey: ['pharmacy-sales', centreId, date], queryFn: () => api.sales(centreId!, { date }), enabled: ok && !!centreId });
  const waiting = (rx.data ?? []).filter((p) => p.status === 'OPEN' || p.status === 'PARTLY_DISPENSED');
  const list = sales.data ?? [];
  return (
    <>
      <PageHeader title="Pharmacy" subtitle={<>{centreName} · prescriptions, dispensing, sales and returns</>}
        actions={at('pharmacy.dispense') && <Button variant="contained" onClick={() => setDispenseFor('otc')} disabled={!storeId}>Over-the-counter sale</Button>} />
      <MyPositions />
      <Box sx={{ mt: 2 }} />
      <NeedPermission ok={ok} what="pharmacy access">
        <Stack direction="row" spacing={2} sx={{ mb: 2, flexWrap: 'wrap', gap: 1 }}>
          <StoreSelect centreId={centreId} type="PHARMACY" value={storeId} onChange={setStoreId} label="Pharmacy store" />
        </Stack>
        <StatRow>
          <StatCard label="Prescriptions waiting" value={waiting.length} tone={waiting.length ? 'warning' : undefined} />
          <StatCard label="Sales" value={list.length} hint={dateLabel(date)} />
          <StatCard label="Sold" value={money(list.reduce((n, s) => n + (s.totalAmount ?? 0), 0))} tone="success" />
          <StatCard label="Unpaid bills" value={list.filter((s) => s.billStatus === 'OPEN').length} tone="warning" />
          <StatCard label="Returned" value={money(list.reduce((n, s) => n + (s.returnedAmount ?? 0), 0))} />
        </StatRow>
        <Tabs value={tab} onChange={(_, v: string) => setTab(v)} sx={{ mb: 2, borderBottom: 1, borderColor: 'divider' }}>
          <Tab value="queue" label={`Prescriptions waiting (${waiting.length})`} />
          <Tab value="sales" label="Sales" />
          <Tab value="all" label="All prescriptions" />
        </Tabs>
        {(tab === 'queue' || tab === 'all') && (
          <DataTable rows={tab === 'queue' ? waiting : rx.data} rowKey={(r) => r.id ?? ''} loading={rx.isLoading} empty="No prescriptions waiting."
            onRowClick={at('pharmacy.dispense') ? (r) => (r.status === 'OPEN' || r.status === 'PARTLY_DISPENSED') && storeId && setDispenseFor(r) : undefined}
            columns={[
              { key: 'no', header: 'Prescription', render: (r) => <>{r.prescriptionNo} <Typography component="span" variant="caption" color="text.secondary">{clock(r.prescribedAt)}</Typography></>, value: (r) => r.prescriptionNo },
              { key: 'patient', header: 'Patient', render: (r) => <>{r.patientName} <Typography component="span" variant="caption" color="text.secondary">{r.patientUhid}</Typography></>, value: (r) => `${r.patientName} ${r.patientUhid}` },
              { key: 'meds', header: 'Medicines', render: (r) => (r.lines ?? []).map((l) => l.itemName).join(', ') },
              { key: 'doctor', header: 'Doctor', render: (r) => r.prescribedByName },
              { key: 'status', header: 'Status', render: (r) => <StatusChip status={r.status} /> },
            ]} />
        )}
        {tab === 'sales' && (
          <>
            <TextField label="Day" type="date" value={date} onChange={(e) => setDate(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} sx={{ mb: 1, width: 190 }} />
            <DataTable rows={list} rowKey={(r) => r.id ?? ''} loading={sales.isLoading} empty="No sales on this day."
              columns={[
                { key: 'no', header: 'Sale', render: (r) => <>{r.saleNo} <Typography component="span" variant="caption" color="text.secondary">{clock(r.soldAt)}</Typography></>, value: (r) => r.saleNo },
                { key: 'patient', header: 'Patient', render: (r) => <>{r.patientName} <Typography component="span" variant="caption" color="text.secondary">{r.patientUhid}</Typography></>, value: (r) => `${r.patientName} ${r.patientUhid}` },
                { key: 'rx', header: 'Against', render: (r) => r.prescriptionNo ?? 'Over the counter' },
                { key: 'items', header: 'Medicines', render: (r) => (r.lines ?? []).map((l) => `${l.itemName} × ${l.quantity}`).join(', ') },
                { key: 'amt', header: 'Amount', align: 'right', render: (r) => money(r.totalAmount), value: (r) => r.totalAmount },
                { key: 'bill', header: 'Bill', render: (r) => <StatusChip status={r.billStatus} /> },
                { key: 'act', header: '', align: 'right', render: (r) => (
                  <Stack direction="row" spacing={0.5} sx={{ justifyContent: 'flex-end' }}>
                    {r.billStatus === 'OPEN' && at('billing.collect') && <Button size="small" variant="contained" onClick={() => setCollect(r.billId)}>Collect</Button>}
                    {at('pharmacy.return') && <Button size="small" onClick={() => setReturning(r)}>Return</Button>}
                  </Stack>
                ) },
              ]} />
          </>
        )}
      </NeedPermission>
      {dispenseFor && storeId && <DispenseDialog storeId={storeId} prescription={dispenseFor === 'otc' ? undefined : dispenseFor}
        onClose={() => setDispenseFor(undefined)} onDone={(s) => s.billId && at('billing.collect') && setCollect(s.billId)} />}
      {returning && <ReturnDialog sale={returning} onClose={() => setReturning(undefined)} />}
      {collect && <CollectBill billId={collect} onClose={() => setCollect(undefined)} />}
    </>
  );
}

function CollectBill({ billId, onClose }: { billId: string; onClose: () => void }) {
  const bill = useQuery({ queryKey: ['bill', billId], queryFn: () => api.bill(billId) });
  return bill.data?.bill ? <CollectPaymentDialog bill={bill.data.bill} onClose={onClose} /> : null;
}

type Line = { key: number; item?: api.ItemView; quantity: string; prescriptionLineId?: string; schedule?: string };

function DispenseDialog({ storeId, prescription, onClose, onDone }: { storeId: string; prescription?: api.PrescriptionView; onClose: () => void; onDone: (s: api.SaleView) => void }) {
  const [patient, setPatient] = useState<api.PatientView | undefined>();
  const items = useQuery({ queryKey: ['items', ''], queryFn: () => api.items({}) });
  const [lines, setLines] = useState<Line[]>(() => prescription
    ? (prescription.lines ?? []).filter((l) => (l.quantity ?? 0) > (l.dispensedQty ?? 0)).map((l, i) => ({
      key: i, quantity: String((l.quantity ?? 0) - (l.dispensedQty ?? 0)), prescriptionLineId: l.id, schedule: l.schedule,
      item: { id: l.itemId, code: l.itemCode, name: l.itemName, schedule: l.schedule } as api.ItemView,
    }))
    : [{ key: 1, quantity: '1' }]);
  const set = (k: number, patch: Partial<Line>) => setLines((ls) => ls.map((l) => (l.key === k ? { ...l, ...patch } : l)));
  const patientId = prescription?.patientId ?? patient?.id;
  const save = useAction(() => api.dispense({
    storeId, patientId: patientId!, prescriptionId: prescription?.id,
    lines: lines.filter((l) => l.item && Number(l.quantity) > 0).map((l) => ({ itemId: l.item!.id!, quantity: Number(l.quantity), prescriptionLineId: l.prescriptionLineId })),
  }), { invalidate: [['prescriptions'], ['pharmacy-sales'], ['stock'], ['batches']], success: (s) => `Dispensed: sale ${s.saleNo}, bill ${s.billNo} for ${money(s.totalAmount)}`, onDone: (s) => { onClose(); onDone(s); } });
  const rxOnly = lines.filter((l) => !l.prescriptionLineId && ['H', 'H1', 'X'].includes(l.item?.schedule ?? (items.data ?? []).find((i) => i.id === l.item?.id)?.schedule ?? ''));
  return (
    <FormDialog open title={prescription ? `Dispense ${prescription.prescriptionNo}` : 'Over-the-counter sale'} submitLabel="Dispense and bill" onClose={onClose}
      onSubmit={() => save.mutate(undefined)} busy={save.isPending} error={save.error} maxWidth="md"
      intro={<Typography variant="body2" color="text.secondary">Batches are picked automatically, earliest expiry first; expired stock is never dispensed. The bill goes to the patient&apos;s account.</Typography>}>
      {prescription ? (
        <Alert severity="info">{prescription.patientName} · {prescription.patientUhid} · prescribed by {prescription.prescribedByName} {clock(prescription.prescribedAt)}</Alert>
      ) : (
        <PatientPicker value={patient} onChange={setPatient} autoFocus />
      )}
      {lines.map((l) => {
        const rxLine = prescription?.lines?.find((x) => x.id === l.prescriptionLineId);
        return (
          <Stack key={l.key} direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ alignItems: { sm: 'center' } }}>
            <Box sx={{ flex: 3 }}>
              {rxLine ? (
                <Box>
                  <Typography variant="body2" sx={{ fontWeight: 600 }}>{rxLine.itemName} <ScheduleChip schedule={rxLine.schedule} /></Typography>
                  <Typography variant="caption" color="text.secondary">{rxLine.dose}, {rxLine.frequency}, {rxLine.durationDays} days{rxLine.eye ? `, ${eyeLabel(rxLine.eye)}` : ''} · prescribed {rxLine.quantity}, given {rxLine.dispensedQty}</Typography>
                </Box>
              ) : <ItemPicker categories={['DRUG', 'CONSUMABLE', 'CONTACT_LENS']} value={l.item} onChange={(i) => set(l.key, { item: i })} label="Medicine" />}
            </Box>
            <TextField label="Quantity" value={l.quantity} onChange={(e) => set(l.key, { quantity: e.target.value })} slotProps={{ htmlInput: { inputMode: 'numeric' } }} sx={{ width: 110 }} />
            {!prescription && <IconButton aria-label="Remove line" onClick={() => setLines((ls) => ls.filter((x) => x.key !== l.key))} disabled={lines.length === 1}><DeleteOutlined /></IconButton>}
          </Stack>
        );
      })}
      {!prescription && <Box><Button onClick={() => setLines((ls) => [...ls, { key: Date.now(), quantity: '1' }])}>Add medicine</Button></Box>}
      {rxOnly.length > 0 && <Alert severity="warning">{rxOnly.map((l) => l.item?.name).join(', ')}: Schedule H, H1 and X medicines are dispensed only against the doctor&apos;s prescription.</Alert>}
    </FormDialog>
  );
}

function ReturnDialog({ sale, onClose }: { sale: api.SaleView; onClose: () => void }) {
  const lines = (sale.lines ?? []).filter((l) => (l.quantity ?? 0) > (l.returnedQty ?? 0));
  const [lineId, setLineId] = useState(lines[0]?.id ?? '');
  const [qty, setQty] = useState('1');
  const [reason, setReason] = useState('');
  const save = useAction(() => api.returnSale(sale.id!, { lines: [{ saleLineId: lineId, quantity: Number(qty), reason }] }),
    { invalidate: [['pharmacy-sales'], ['stock'], ['batches']], success: 'Returned to stock; refund the amount through accounts', onDone: onClose });
  return (
    <FormDialog open title={`Return from ${sale.saleNo}`} submitLabel="Return to stock" onClose={onClose} onSubmit={() => save.mutate(undefined)} busy={save.isPending} error={save.error}
      intro={<Typography variant="body2" color="text.secondary">Unopened medicines can go back into stock within 30 days. Expired medicines cannot.</Typography>}>
      <TextField select label="Medicine" value={lineId} onChange={(e) => setLineId(e.target.value)}>
        {lines.map((l) => <MenuItem key={l.id} value={l.id}>{l.itemName} · batch {l.batchNo} · sold {l.quantity}, returned {l.returnedQty}</MenuItem>)}
      </TextField>
      <TextField label="Quantity" value={qty} onChange={(e) => setQty(e.target.value)} />
      <TextField label="Reason" value={reason} onChange={(e) => setReason(e.target.value)} required />
    </FormDialog>
  );
}

/** For the doctor: write a prescription for a patient (opened from the visit chart). */
export function PrescribeDialog({ centreId, patientId, patientName, visitId, onClose }: { centreId: string; patientId: string; patientName?: string; visitId?: string; onClose: () => void }) {
  type RxLine = { key: number; item?: api.ItemView; dose: string; frequency: string; days: string; eye: string; quantity: string; instructions: string };
  const blank = (key: number): RxLine => ({ key, dose: '1 drop', frequency: '4 times a day', days: '7', eye: 'BE', quantity: '1', instructions: '' });
  const [lines, setLines] = useState<RxLine[]>([blank(1)]);
  const [notes, setNotes] = useState('');
  const set = (k: number, patch: Partial<RxLine>) => setLines((ls) => ls.map((l) => (l.key === k ? { ...l, ...patch } : l)));
  const save = useAction(() => api.prescribe(centreId, {
    patientId, visitRef: visitId, notes: notes || undefined,
    lines: lines.filter((l) => l.item).map((l) => ({ itemId: l.item!.id!, dose: l.dose, frequency: l.frequency, durationDays: Number(l.days), eye: l.eye || undefined, route: l.item?.dosageForm === 'Tablet' ? 'ORAL' : 'TOPICAL', quantity: Number(l.quantity), instructions: l.instructions || undefined })),
  }), { invalidate: [['prescriptions'], ['patient-prescriptions']], success: (r) => `Prescription ${r.prescriptionNo} sent to the pharmacy`, onDone: onClose });
  return (
    <FormDialog open title={`Prescribe medicines${patientName ? ` for ${patientName}` : ''}`} submitLabel="Send to pharmacy" onClose={onClose} onSubmit={() => save.mutate(undefined)} busy={save.isPending} error={save.error} maxWidth="lg">
      {lines.map((l) => (
        <Stack key={l.key} direction={{ xs: 'column', md: 'row' }} spacing={1} sx={{ alignItems: { md: 'center' } }}>
          <Box sx={{ flex: 3, minWidth: 240 }}><ItemPicker categories={['DRUG', 'CONSUMABLE', 'CONTACT_LENS']} value={l.item} onChange={(i) => set(l.key, { item: i })} label="Medicine" /></Box>
          <TextField label="Dose" value={l.dose} onChange={(e) => set(l.key, { dose: e.target.value })} sx={{ width: 110 }} />
          <TextField label="How often" value={l.frequency} onChange={(e) => set(l.key, { frequency: e.target.value })} sx={{ flex: 1.5 }} />
          <TextField label="Days" value={l.days} onChange={(e) => set(l.key, { days: e.target.value })} sx={{ width: 80 }} />
          <TextField select label="Eye" value={l.eye} onChange={(e) => set(l.key, { eye: e.target.value })} sx={{ width: 110 }}>
            <MenuItem value="">Not for the eye</MenuItem><MenuItem value="RE">Right</MenuItem><MenuItem value="LE">Left</MenuItem><MenuItem value="BE">Both</MenuItem>
          </TextField>
          <TextField label="Qty" value={l.quantity} onChange={(e) => set(l.key, { quantity: e.target.value })} sx={{ width: 70 }} />
          <IconButton aria-label="Remove medicine" onClick={() => setLines((ls) => ls.filter((x) => x.key !== l.key))} disabled={lines.length === 1}><DeleteOutlined /></IconButton>
        </Stack>
      ))}
      <Box><Button onClick={() => setLines((ls) => [...ls, blank(Math.max(0, ...ls.map((l) => l.key)) + 1)])}>Add medicine</Button></Box>
      <TextField label="Notes for the patient or pharmacist" value={notes} onChange={(e) => setNotes(e.target.value)} multiline minRows={2} />
    </FormDialog>
  );
}

/** The patient's prescriptions, for the visit chart. */
export function PatientPrescriptions({ patientId }: { patientId: string }) {
  const q = useQuery({ queryKey: ['patient-prescriptions', patientId], queryFn: () => api.patientPrescriptions(patientId) });
  if (!q.data?.length) {
    return <Typography variant="body2" color="text.secondary">No prescriptions.</Typography>;
  }
  return (
    <Stack spacing={1}>
      <ProblemAlert error={q.error} />
      {q.data.map((p) => (
        <Box key={p.id}>
          <Typography variant="body2" sx={{ fontWeight: 600 }}>{p.prescriptionNo} · {clock(p.prescribedAt)} · {p.prescribedByName} <Chip size="small" label={p.status?.toLowerCase().replace('_', ' ')} /></Typography>
          {(p.lines ?? []).map((l) => (
            <Typography key={l.id} variant="body2" color="text.secondary">{l.itemName}: {l.dose}, {l.frequency}, {l.durationDays} days{l.eye ? `, ${eyeLabel(l.eye)}` : ''}</Typography>
          ))}
        </Box>
      ))}
    </Stack>
  );
}
