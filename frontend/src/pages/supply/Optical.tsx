import { Alert, Box, Button, Chip, Divider, MenuItem, Stack, Tab, Tabs, TextField, Typography } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';

import * as api from '../../api/generated/core-neha';
import { useAction } from '../../api/useAction';
import { useSession } from '../../auth/session';
import { DataTable } from '../../components/DataTable';
import { FormDialog } from '../../components/FormDialog';
import { PageHeader } from '../../components/PageHeader';
import { StatusChip } from '../../components/StatusChip';
import { clock, dateLabel, money, MyPositions, NeedPermission, StatCard, StatRow, addDays, todayIst, useWorkingCentre } from '../clinic/common';
import { CollectPaymentDialog, PatientPicker, ReasonDialog } from '../clinic/dialogs';
import { ItemPicker, parseDioptre, power, StoreSelect } from './common';

function rxText(sph?: number | null, cyl?: number | null, axis?: number | null, add?: number | null): string {
  if (sph == null && cyl == null) {
    return '–';
  }
  return `${power(sph)}${cyl ? ` / ${power(cyl)} × ${axis ?? 0}°` : ''}${add ? ` add ${power(add)}` : ''}`;
}

const STATUS_LABEL: Record<string, string> = { ORDERED: 'Fitting in shop', IN_LAB: 'In the lab', READY: 'Ready for patient', DELIVERED: 'Delivered', CANCELLED: 'Cancelled' };

/** Optical shop: spectacle orders from the optometry prescription, frames and lenses, the lab, delivery. */
export function OpticalShop() {
  const session = useSession();
  const { centreId, centreName } = useWorkingCentre();
  const at = (p: string) => session.hasAtCentre(p, centreId);
  const ok = at('optical.read') || at('optical.order');
  const [storeId, setStoreId] = useState('');
  const [tab, setTab] = useState('open');
  const [dialog, setDialog] = useState<'new' | undefined>();
  const [cancel, setCancel] = useState<api.OrderView | undefined>();
  const [collect, setCollect] = useState<string | undefined>();
  const orders = useQuery({ queryKey: ['spectacle-orders', centreId], queryFn: () => api.spectacleOrders(centreId!), enabled: ok && !!centreId });
  const all = orders.data ?? [];
  const open = all.filter((o) => o.status !== 'DELIVERED' && o.status !== 'CANCELLED');
  const ready = useAction((o: api.OrderView) => api.markSpectaclesReady(o.id!, { expectedVersion: o.version ?? 0 }), { invalidate: [['spectacle-orders']], success: 'Marked ready for the patient' });
  const deliver = useAction((o: api.OrderView) => api.deliverSpectacles(o.id!, { expectedVersion: o.version ?? 0 }), { invalidate: [['spectacle-orders']], success: (o) => `Order ${o.orderNo} delivered` });
  const rows = tab === 'open' ? open : all;
  return (
    <>
      <PageHeader title="Optical shop" subtitle={<>{centreName} · spectacle orders, frames, lenses, lab and delivery</>}
        actions={at('optical.order') && <Button variant="contained" onClick={() => setDialog('new')} disabled={!storeId}>New spectacle order</Button>} />
      <MyPositions />
      <Box sx={{ mt: 2 }} />
      <NeedPermission ok={ok} what="optical shop access">
        <Box sx={{ mb: 2 }}><StoreSelect centreId={centreId} type="OPTICAL" value={storeId} onChange={setStoreId} label="Optical store" /></Box>
        <StatRow>
          <StatCard label="In the lab" value={all.filter((o) => o.status === 'IN_LAB').length} tone="info" />
          <StatCard label="Fitting in shop" value={all.filter((o) => o.status === 'ORDERED').length} />
          <StatCard label="Ready for patient" value={all.filter((o) => o.status === 'READY').length} tone="success" />
          <StatCard label="Overdue" value={open.filter((o) => o.promisedDate && o.promisedDate < todayIst()).length} tone="warning" hint="past the promised date" />
          <StatCard label="Balance to collect" value={money(open.reduce((n, o) => n + (o.billBalance ?? 0), 0))} />
        </StatRow>
        <Tabs value={tab} onChange={(_, v: string) => setTab(v)} sx={{ mb: 2, borderBottom: 1, borderColor: 'divider' }}>
          <Tab value="open" label={`Open orders (${open.length})`} />
          <Tab value="all" label="All (30 days)" />
        </Tabs>
        <DataTable rows={rows} rowKey={(r) => r.id ?? ''} loading={orders.isLoading} empty="No spectacle orders."
          columns={[
            { key: 'no', header: 'Order', render: (r) => <>{r.orderNo} <Typography component="span" variant="caption" color="text.secondary">{clock(r.orderedAt)}</Typography></>, value: (r) => r.orderNo },
            { key: 'patient', header: 'Patient', render: (r) => <>{r.patientName} <Typography component="span" variant="caption" color="text.secondary">{r.patientUhid}</Typography></>, value: (r) => `${r.patientName} ${r.patientUhid}` },
            { key: 'rx', header: 'Prescription (R / L)', render: (r) => <Typography variant="caption">R {rxText(r.sphRe, r.cylRe, r.axisRe, r.addRe)}<br />L {rxText(r.sphLe, r.cylLe, r.axisLe, r.addLe)}</Typography> },
            { key: 'what', header: 'Frame and lenses', render: (r) => <Typography variant="caption">{r.frameSource === 'PATIENT_OWN' ? "Patient's own frame" : r.frameName}<br />{r.lensName} · {r.lensSource === 'LAB' ? 'lab' : 'stock'}</Typography> },
            { key: 'due', header: 'Promised', render: (r) => r.promisedDate ? <Chip size="small" variant="outlined" color={r.promisedDate < todayIst() && r.status !== 'DELIVERED' ? 'warning' : 'default'} label={dateLabel(r.promisedDate)} /> : '–', value: (r) => r.promisedDate },
            { key: 'amt', header: 'Amount', align: 'right', render: (r) => <>{money(r.totalAmount)}{(r.billBalance ?? 0) > 0 && r.status !== 'CANCELLED' && <Typography variant="caption" color="warning.main" sx={{ display: 'block' }}>due {money(r.billBalance)}</Typography>}</>, value: (r) => r.totalAmount },
            { key: 'status', header: 'Status', render: (r) => <><Chip size="small" label={STATUS_LABEL[r.status ?? ''] ?? r.status} color={r.status === 'READY' ? 'success' : r.status === 'IN_LAB' ? 'info' : 'default'} />{r.labJobStatus && r.status === 'IN_LAB' && <Typography variant="caption" sx={{ display: 'block' }}>lab: {r.labJobStatus.toLowerCase()}</Typography>}</> },
            { key: 'act', header: '', align: 'right', render: (r) => (
              <Stack direction="row" spacing={0.5} sx={{ justifyContent: 'flex-end', flexWrap: 'wrap', gap: 0.5 }}>
                {(r.billBalance ?? 0) > 0 && r.billStatus === 'OPEN' && at('billing.collect') && <Button size="small" onClick={() => setCollect(r.billId)}>Collect</Button>}
                {r.status === 'ORDERED' && at('optical.order') && <Button size="small" onClick={() => ready.mutate(r)}>Mark ready</Button>}
                {r.status === 'READY' && at('optical.deliver') && <Button size="small" variant="contained" onClick={() => deliver.mutate(r)} disabled={(r.billBalance ?? 0) > 0}>Deliver</Button>}
                {(r.status === 'ORDERED' || r.status === 'IN_LAB' || r.status === 'READY') && at('optical.order') && <Button size="small" color="inherit" onClick={() => setCancel(r)}>Cancel</Button>}
              </Stack>
            ) },
          ]} />
      </NeedPermission>
      {dialog === 'new' && storeId && <NewOrderDialog storeId={storeId} onClose={() => setDialog(undefined)} onDone={(o) => o.billId && at('billing.collect') && setCollect(o.billId)} />}
      {cancel && <ReasonDialog title={`Cancel order ${cancel.orderNo}`} submitLabel="Cancel order" onClose={() => setCancel(undefined)}
        run={(reason) => api.cancelSpectacleOrder(cancel.id!, { expectedVersion: cancel.version ?? 0, reason })} success="Order cancelled; frame and lenses are back in stock" />}
      {collect && <CollectBill billId={collect} onClose={() => setCollect(undefined)} />}
    </>
  );
}

function CollectBill({ billId, onClose }: { billId: string; onClose: () => void }) {
  const bill = useQuery({ queryKey: ['bill', billId], queryFn: () => api.bill(billId) });
  return bill.data?.bill ? <CollectPaymentDialog bill={bill.data.bill} onClose={onClose} /> : null;
}

function NewOrderDialog({ storeId, onClose, onDone }: { storeId: string; onClose: () => void; onDone: (o: api.OrderView) => void }) {
  const [patient, setPatient] = useState<api.PatientView | undefined>();
  const history = useQuery({ queryKey: ['eye-history', patient?.id], queryFn: () => api.patientEyeHistory(patient!.id!), enabled: !!patient?.id });
  const [visitId, setVisitId] = useState('');
  const [rx, setRx] = useState({ sphRe: '', cylRe: '', axisRe: '', addRe: '', sphLe: '', cylLe: '', axisLe: '', addLe: '' });
  const [pdRe, setPdRe] = useState('32');
  const [pdLe, setPdLe] = useState('32');
  const [fh, setFh] = useState('');
  const [frameSource, setFrameSource] = useState('STOCK');
  const [frameBatch, setFrameBatch] = useState('');
  const [lens, setLens] = useState<api.ItemView | undefined>();
  const [lensSource, setLensSource] = useState('LAB');
  const [lensBatch, setLensBatch] = useState('');
  const [promised, setPromised] = useState(addDays(todayIst(), 3));
  const stock = useQuery({ queryKey: ['batches', storeId], queryFn: () => api.batches(storeId, { inStock: true }) });
  const frames = (stock.data ?? []).filter((b) => b.category === 'FRAME');
  const lensBatches = (stock.data ?? []).filter((b) => b.itemId === lens?.id);
  const visits = (history.data?.visits ?? []).filter((v) => v.bcvaRe || v.bcvaLe);
  const num = (s: string) => (s.trim() ? Number(s) : undefined);
  const save = useAction(() => api.createSpectacleOrder({
    storeId, patientId: patient!.id!, visitRef: visitId || undefined,
    sphRe: visitId ? undefined : parseDioptre(rx.sphRe), cylRe: visitId ? undefined : parseDioptre(rx.cylRe), axisRe: visitId ? undefined : num(rx.axisRe), addRe: visitId ? undefined : parseDioptre(rx.addRe),
    sphLe: visitId ? undefined : parseDioptre(rx.sphLe), cylLe: visitId ? undefined : parseDioptre(rx.cylLe), axisLe: visitId ? undefined : num(rx.axisLe), addLe: visitId ? undefined : parseDioptre(rx.addLe),
    pdRe: Number(pdRe), pdLe: Number(pdLe), fittingHeight: num(fh), frameSource, frameBatchId: frameSource === 'STOCK' ? frameBatch : undefined,
    lensSource, lensItemId: lens?.id ?? '', lensBatchId: lensSource === 'STOCK' ? lensBatch : undefined, promisedDate: promised || undefined,
  }), { invalidate: [['spectacle-orders'], ['batches'], ['stock'], ['lab-jobs']], success: (o) => `Order ${o.orderNo} placed; bill ${o.billNo} for ${money(o.totalAmount)}`, onDone: (o) => { onClose(); onDone(o); } });
  const r = (k: keyof typeof rx) => (e: { target: { value: string } }) => setRx({ ...rx, [k]: e.target.value });
  return (
    <FormDialog open title="New spectacle order" submitLabel="Place order and bill" onClose={onClose} onSubmit={() => save.mutate(undefined)} busy={save.isPending} error={save.error} maxWidth="md">
      <PatientPicker value={patient} onChange={(p) => { setPatient(p); setVisitId(''); }} autoFocus />
      <TextField select label="Prescription" value={visitId} onChange={(e) => setVisitId(e.target.value)} disabled={!patient}
        helperText={visitId ? 'Copied from the optometry record of that visit when the order is placed; it cannot be changed afterwards.' : 'An outside prescription: enter it below.'}>
        <MenuItem value="">Outside prescription (enter below)</MenuItem>
        {visits.map((v) => <MenuItem key={v.visit?.id} value={v.visit?.id}>Refraction at the visit on {dateLabel(v.visit?.visitDate)}</MenuItem>)}
      </TextField>
      {!visitId && (
        <>
          <Stack direction="row" spacing={1}><Typography sx={{ width: 50, pt: 2 }}>Right</Typography>
            <TextField label="Sph" value={rx.sphRe} onChange={r('sphRe')} slotProps={{ htmlInput: { inputMode: 'decimal' } }} /><TextField label="Cyl" value={rx.cylRe} onChange={r('cylRe')} slotProps={{ htmlInput: { inputMode: 'decimal' } }} />
            <TextField label="Axis" value={rx.axisRe} onChange={r('axisRe')} slotProps={{ htmlInput: { inputMode: 'numeric' } }} /><TextField label="Add" value={rx.addRe} onChange={r('addRe')} slotProps={{ htmlInput: { inputMode: 'decimal' } }} /></Stack>
          <Stack direction="row" spacing={1}><Typography sx={{ width: 50, pt: 2 }}>Left</Typography>
            <TextField label="Sph" value={rx.sphLe} onChange={r('sphLe')} slotProps={{ htmlInput: { inputMode: 'decimal' } }} /><TextField label="Cyl" value={rx.cylLe} onChange={r('cylLe')} slotProps={{ htmlInput: { inputMode: 'decimal' } }} />
            <TextField label="Axis" value={rx.axisLe} onChange={r('axisLe')} slotProps={{ htmlInput: { inputMode: 'numeric' } }} /><TextField label="Add" value={rx.addLe} onChange={r('addLe')} slotProps={{ htmlInput: { inputMode: 'decimal' } }} /></Stack>
        </>
      )}
      <Stack direction="row" spacing={1}>
        <TextField label="PD right (mm)" value={pdRe} onChange={(e) => setPdRe(e.target.value)} required />
        <TextField label="PD left (mm)" value={pdLe} onChange={(e) => setPdLe(e.target.value)} required />
        <TextField label="Fitting height (mm)" value={fh} onChange={(e) => setFh(e.target.value)} helperText="Needed for progressive lenses" />
      </Stack>
      <Divider />
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
        <TextField select label="Frame" value={frameSource} onChange={(e) => setFrameSource(e.target.value)} sx={{ minWidth: 200 }}>
          <MenuItem value="STOCK">From our stock</MenuItem><MenuItem value="PATIENT_OWN">Patient&apos;s own frame</MenuItem>
        </TextField>
        {frameSource === 'STOCK' && (
          <TextField select label="Frame from stock" value={frameBatch} onChange={(e) => setFrameBatch(e.target.value)} required sx={{ flex: 1 }}>
            {frames.map((b) => <MenuItem key={b.id} value={b.id}>{b.itemName} · {money(b.mrp)} · {b.qtyOnHand} in stock</MenuItem>)}
          </TextField>
        )}
      </Stack>
      <ItemPicker categories={['SPECTACLE_LENS']} value={lens} onChange={setLens} label="Lenses" />
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
        <TextField select label="Lenses made" value={lensSource} onChange={(e) => setLensSource(e.target.value)} sx={{ minWidth: 200 }}>
          <MenuItem value="LAB">In our optical lab</MenuItem><MenuItem value="STOCK">From stock, fitted in the shop</MenuItem>
        </TextField>
        {lensSource === 'STOCK' && (
          <TextField select label="Stock lens pair" value={lensBatch} onChange={(e) => setLensBatch(e.target.value)} required sx={{ flex: 1 }}>
            {lensBatches.map((b) => <MenuItem key={b.id} value={b.id}>Batch {b.batchNo} · {b.qtyOnHand} pairs</MenuItem>)}
          </TextField>
        )}
        <TextField label="Promised date" type="date" value={promised} onChange={(e) => setPromised(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
      </Stack>
      {lens && <Alert severity="info">Lenses {money(lens.mrp)}{frameSource === 'STOCK' && frameBatch ? ` + frame ${money(frames.find((f) => f.id === frameBatch)?.mrp)}` : ''}. The bill is raised now; spectacles are handed over when it is paid.</Alert>}
    </FormDialog>
  );
}

// ---------------------------------------------------------------- optical lab

const LAB_COLUMNS = ['RECEIVED', 'SURFACING', 'COATING', 'EDGING', 'FITTING', 'QC', 'READY'];
const NEXT: Record<string, string[]> = { RECEIVED: ['SURFACING'], SURFACING: ['COATING', 'EDGING'], COATING: ['EDGING'], EDGING: ['FITTING'], FITTING: ['QC'], READY: ['DISPATCHED'] };

/** Optical lab: the job board from received to dispatched, with blank consumption and quality check. */
export function OpticalLab() {
  const session = useSession();
  const { centreId, centreName } = useWorkingCentre();
  const at = (p: string) => session.hasAtCentre(p, centreId);
  const ok = at('opticallab.read') || at('opticallab.process') || at('opticallab.qc');
  const jobs = useQuery({ queryKey: ['lab-jobs', centreId], queryFn: () => api.labJobs(centreId!), enabled: ok && !!centreId });
  const [open, setOpen] = useState<string | undefined>();
  const all = jobs.data ?? [];
  return (
    <>
      <PageHeader title="Optical lab" subtitle={<>{centreName} · lens surfacing, coating, edging, fitting, quality check and dispatch</>} />
      <MyPositions />
      <Box sx={{ mt: 2 }} />
      <NeedPermission ok={ok} what="optical lab access">
        <StatRow>
          <StatCard label="Jobs in the lab" value={all.filter((j) => LAB_COLUMNS.includes(j.status ?? '')).length} />
          <StatCard label="Waiting for QC" value={all.filter((j) => j.status === 'QC').length} tone="warning" />
          <StatCard label="Ready to dispatch" value={all.filter((j) => j.status === 'READY').length} tone="success" />
          <StatCard label="Remakes" value={all.reduce((n, j) => n + (j.remakeCount ?? 0), 0)} hint="failed quality checks" />
        </StatRow>
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(4, 1fr)', xl: 'repeat(7, 1fr)' }, gap: 1.5 }}>
          {LAB_COLUMNS.map((c) => (
            <Box key={c} sx={{ bgcolor: 'background.paper', border: 1, borderColor: 'divider', borderRadius: 1, p: 1, minHeight: 120 }}>
              <Typography variant="subtitle2" sx={{ mb: 1 }}>{c === 'QC' ? 'Quality check' : c.charAt(0) + c.slice(1).toLowerCase()} ({all.filter((j) => j.status === c).length})</Typography>
              <Stack spacing={1}>
                {all.filter((j) => j.status === c).map((j) => (
                  <Button key={j.id} variant="outlined" onClick={() => setOpen(j.id)} sx={{ justifyContent: 'flex-start', textAlign: 'left', display: 'block', textTransform: 'none' }}>
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>{j.jobNo?.split('/').pop()}</Typography>
                    <Typography variant="caption" sx={{ display: 'block' }}>{j.patientName}</Typography>
                    <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>{String(j.specification?.lens ?? '')}</Typography>
                    {(j.remakeCount ?? 0) > 0 && <Chip size="small" color="warning" label={`remake ${j.remakeCount}`} />}
                  </Button>
                ))}
              </Stack>
            </Box>
          ))}
        </Box>
      </NeedPermission>
      {open && <JobDialog jobId={open} onClose={() => setOpen(undefined)} />}
    </>
  );
}

function eyeSpec(e: unknown): string {
  const x = (e ?? {}) as { sph?: number; cyl?: number; axis?: number; add?: number };
  return rxText(x.sph, x.cyl, x.axis, x.add);
}

function JobDialog({ jobId, onClose }: { jobId: string; onClose: () => void }) {
  const session = useSession();
  const q = useQuery({ queryKey: ['lab-job', jobId], queryFn: () => api.labJob(jobId) });
  const j = q.data;
  const [blank, setBlank] = useState<api.ItemView | undefined>();
  const [note, setNote] = useState('');
  const [m, setM] = useState({ sphRe: '', cylRe: '', axisRe: '', sphLe: '', cylLe: '', axisLe: '' });
  const can = (p: string) => !!j && session.hasAtCentre(p, j.labCentreId);
  const advance = useAction((to: string) => api.advanceLabJob(jobId, { toStatus: to, blankItemId: to === 'SURFACING' ? blank?.id : undefined, note: note || undefined, expectedVersion: j?.version ?? 0 }),
    { invalidate: [['lab-jobs'], ['lab-job'], ['spectacle-orders']], success: (x) => `Job ${x.jobNo} is now ${x.status?.toLowerCase()}`, onDone: onClose });
  const qc = useAction((pass: boolean) => api.qualityCheckLabJob(jobId, {
    pass, measuredSphRe: parseDioptre(m.sphRe), measuredCylRe: parseDioptre(m.cylRe), measuredAxisRe: m.axisRe ? Number(m.axisRe) : undefined,
    measuredSphLe: parseDioptre(m.sphLe), measuredCylLe: parseDioptre(m.cylLe), measuredAxisLe: m.axisLe ? Number(m.axisLe) : undefined,
    note: note || (pass ? 'Within tolerance' : 'Out of tolerance'), expectedVersion: j?.version ?? 0,
  }), { invalidate: [['lab-jobs'], ['lab-job']], success: (x) => x.status === 'READY' ? 'Passed quality check' : 'Failed: sent back for remake', onDone: onClose });
  const s = j?.specification ?? {};
  const next = NEXT[j?.status ?? ''] ?? [];
  const mm = (k: keyof typeof m) => (e: { target: { value: string } }) => setM({ ...m, [k]: e.target.value });
  return (
    <FormDialog open title={j ? `Job ${j.jobNo}` : 'Job'} submitLabel="Close" onClose={onClose} onSubmit={onClose} busy={advance.isPending || qc.isPending} error={advance.error ?? qc.error} maxWidth="md">
      {j && (
        <>
          <Typography variant="body2">{j.patientName} · order {j.orderNo} from {j.shopCentreName} · <StatusChip status={j.status} /></Typography>
          <Box sx={{ p: 1.5, bgcolor: 'action.hover', borderRadius: 1 }}>
            <Typography variant="subtitle2">Specification (frozen when the order was placed)</Typography>
            <Typography variant="body2">Right {eyeSpec(s.right)} · Left {eyeSpec(s.left)}</Typography>
            <Typography variant="body2">PD {String(s.pdRight ?? '–')} / {String(s.pdLeft ?? '–')} mm{s.fittingHeight ? ` · fitting height ${String(s.fittingHeight)} mm` : ''}</Typography>
            <Typography variant="body2">{String(s.lens ?? '')}{s.tint ? ` · tint ${String(s.tint)}` : ''} · frame: {String(s.frame ?? '')}</Typography>
            {s.promisedDate ? <Typography variant="body2">Promised {dateLabel(String(s.promisedDate))}</Typography> : null}
          </Box>
          {j.status === 'RECEIVED' && can('opticallab.process') && <ItemPicker categories={['LENS_BLANK']} value={blank} onChange={setBlank} label="Lens blank (a pair is taken from the lab store)" />}
          {j.status === 'QC' && can('opticallab.qc') && (
            <>
              <Typography variant="subtitle2">Measured on the lensmeter</Typography>
              <Stack direction="row" spacing={1}><Typography sx={{ width: 50, pt: 2 }}>Right</Typography>
                <TextField label="Sph" value={m.sphRe} onChange={mm('sphRe')} /><TextField label="Cyl" value={m.cylRe} onChange={mm('cylRe')} /><TextField label="Axis" value={m.axisRe} onChange={mm('axisRe')} /></Stack>
              <Stack direction="row" spacing={1}><Typography sx={{ width: 50, pt: 2 }}>Left</Typography>
                <TextField label="Sph" value={m.sphLe} onChange={mm('sphLe')} /><TextField label="Cyl" value={m.cylLe} onChange={mm('cylLe')} /><TextField label="Axis" value={m.axisLe} onChange={mm('axisLe')} /></Stack>
              <Typography variant="caption" color="text.secondary">Pass is refused if a power is off by more than 0.13 D (0.25 D above 6 D) or an axis by more than 3°. The fitter cannot check their own work.</Typography>
            </>
          )}
          {(next.length > 0 || j.status === 'QC') && <TextField label="Note" value={note} onChange={(e) => setNote(e.target.value)} />}
          <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', gap: 1 }}>
            {next.map((to) => {
              const perm = to === 'DISPATCHED' ? 'opticallab.qc' : 'opticallab.process';
              return can(perm) ? <Button key={to} variant={to === next[0] ? 'contained' : 'outlined'} onClick={() => advance.mutate(to)} disabled={to === 'SURFACING' && !blank}>
                {to === 'DISPATCHED' ? 'Dispatch to shop' : to === 'QC' ? 'Fitting done, send to QC' : `Move to ${to.toLowerCase()}`}</Button> : null;
            })}
            {j.status === 'QC' && can('opticallab.qc') && (
              <>
                <Button variant="contained" color="success" onClick={() => qc.mutate(true)}>Pass</Button>
                <Button variant="outlined" color="error" onClick={() => qc.mutate(false)}>Fail, remake</Button>
              </>
            )}
          </Stack>
          <Divider />
          <Typography variant="subtitle2">History</Typography>
          {(j.events ?? []).map((e, i) => (
            <Typography key={i} variant="caption" sx={{ display: 'block' }}>{clock(e.at)} · {e.fromStatus ? `${e.fromStatus.toLowerCase()} → ` : ''}{e.toStatus?.toLowerCase()} · {e.actorName}{e.note ? ` · ${e.note}` : ''}</Typography>
          ))}
          {j.qcResult && <Typography variant="caption">Last quality check: {j.qcResult} {j.qcByName ? `by ${j.qcByName}` : ''} · {j.qcNote}</Typography>}
        </>
      )}
    </FormDialog>
  );
}
