import { Alert, Box, Button, Checkbox, Chip, FormControlLabel, Link, MenuItem, Stack, Tab, Table, TableBody, TableCell, TableHead, TableRow, Tabs, TextField, Typography } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';

import * as api from '../../api/generated/core-neha';
import { useAction } from '../../api/useAction';
import { useSession } from '../../auth/session';
import { DataTable } from '../../components/DataTable';
import { FormDialog } from '../../components/FormDialog';
import { PageHeader } from '../../components/PageHeader';
import { ProblemAlert } from '../../components/ProblemAlert';
import { clock, MyPositions, NeedPermission, StatCard, StatRow, useWorkingCentre } from '../clinic/common';

const STATUS_LABEL: Record<string, string> = {
  ORDERED: 'To collect', COLLECTED: 'Collected', IN_LAB: 'In the lab', RESULTED: 'To validate', VALIDATED: 'Released',
  SCHEDULED: 'Scheduled', ACQUIRED: 'To report', REPORTED: 'To sign', VERIFIED: 'Released', CANCELLED: 'Cancelled',
};
const FLAG: Record<string, { label: string; color: 'error' | 'warning' | 'default' }> = {
  HH: { label: 'Critical high', color: 'error' }, LL: { label: 'Critical low', color: 'error' }, H: { label: 'High', color: 'warning' },
  L: { label: 'Low', color: 'warning' }, A: { label: 'Abnormal', color: 'warning' },
};

function StatusLabel({ o }: { o: api.DiagnosticOrderView }) {
  return <Chip size="small" label={STATUS_LABEL[o.status ?? ''] ?? o.status} color={o.released ? 'success' : o.status === 'CANCELLED' ? 'default' : 'info'} variant={o.released ? 'filled' : 'outlined'} />;
}

function Priority({ p }: { p?: string }) {
  return p && p !== 'ROUTINE' ? <Chip size="small" color={p === 'STAT' ? 'error' : 'warning'} label={p.toLowerCase()} sx={{ ml: 0.5 }} /> : null;
}

function useWorklist(centreId: string | undefined, kind: 'LAB' | 'IMAGING', enabled: boolean) {
  return useQuery({ queryKey: ['diagnostics', kind, centreId], queryFn: () => api.diagnosticWorklist(centreId!, { kind }), enabled: enabled && !!centreId });
}

const columns = (open: (o: api.DiagnosticOrderView) => void) => [
  { key: 'acc', header: 'Accession', render: (r: api.DiagnosticOrderView) => <>{r.accessionNo}<Priority p={r.priority} /></>, value: (r: api.DiagnosticOrderView) => r.accessionNo },
  { key: 'patient', header: 'Patient', render: (r: api.DiagnosticOrderView) => <>{r.patientName} <Typography component="span" variant="caption" color="text.secondary">{r.patientUhid}</Typography></>, value: (r: api.DiagnosticOrderView) => `${r.patientName} ${r.patientUhid}` },
  { key: 'test', header: 'Test', render: (r: api.DiagnosticOrderView) => <>{r.testName}{r.hasCritical && <Chip size="small" color="error" label="critical" sx={{ ml: 0.5 }} />}</>, value: (r: api.DiagnosticOrderView) => r.testName },
  { key: 'when', header: 'Ordered', render: (r: api.DiagnosticOrderView) => <>{clock(r.orderedAt)} <Typography component="span" variant="caption" color="text.secondary">{r.orderedByName}</Typography></>, value: (r: api.DiagnosticOrderView) => r.orderedAt },
  { key: 'status', header: 'Status', render: (r: api.DiagnosticOrderView) => <StatusLabel o={r} /> },
  { key: 'open', header: '', align: 'right' as const, render: (r: api.DiagnosticOrderView) => <Button size="small" onClick={() => open(r)}>Open</Button> },
];

/** Laboratory: samples to collect, samples in the lab, results to enter, results to validate, released reports. */
export function Laboratory() {
  const session = useSession();
  const { centreId, centreName } = useWorkingCentre();
  const at = (p: string) => session.hasAtCentre(p, centreId);
  const ok = ['lab.read', 'lab.collect', 'lab.process', 'lab.validate'].some(at);
  const list = useWorklist(centreId, 'LAB', ok);
  const [tab, setTab] = useState('ORDERED');
  const [open, setOpen] = useState<string | undefined>();
  const all = list.data ?? [];
  const tabs: [string, string, (o: api.DiagnosticOrderView) => boolean][] = [
    ['ORDERED', 'To collect', (o) => o.status === 'ORDERED'],
    ['COLLECTED', 'To receive', (o) => o.status === 'COLLECTED'],
    ['IN_LAB', 'To enter results', (o) => o.status === 'IN_LAB'],
    ['RESULTED', 'To validate', (o) => o.status === 'RESULTED'],
    ['VALIDATED', 'Released (7 days)', (o) => o.status === 'VALIDATED'],
  ];
  const current = tabs.find((t) => t[0] === tab) ?? tabs[0]!;
  return (
    <>
      <PageHeader title="Laboratory" subtitle={<>{centreName} · samples, results, validation and reports</>} />
      <MyPositions />
      <Box sx={{ mt: 2 }} />
      <NeedPermission ok={ok} what="laboratory access">
        <StatRow>
          {tabs.slice(0, 4).map(([k, label, f]) => <StatCard key={k} label={label} value={all.filter(f).length} tone={k === 'RESULTED' ? 'warning' : undefined} />)}
          <StatCard label="Critical results" value={all.filter((o) => o.hasCritical && o.status !== 'VALIDATED').length} tone="warning" hint="tell the doctor at once" />
        </StatRow>
        <Tabs value={tab} onChange={(_, v: string) => setTab(v)} sx={{ mb: 2, borderBottom: 1, borderColor: 'divider' }} variant="scrollable">
          {tabs.map(([k, label, f]) => <Tab key={k} value={k} label={`${label} (${all.filter(f).length})`} />)}
        </Tabs>
        <ProblemAlert error={list.error} />
        <DataTable rows={all.filter(current[2])} rowKey={(r) => r.id ?? ''} loading={list.isLoading} empty="Nothing here." onRowClick={(r) => setOpen(r.id)} columns={columns((r) => setOpen(r.id))} />
      </NeedPermission>
      {open && <OrderDialog orderId={open} onClose={() => setOpen(undefined)} />}
    </>
  );
}

/** Imaging: to schedule, scheduled, to report, to sign, released; images open in the viewer. */
export function Imaging() {
  const session = useSession();
  const { centreId, centreName } = useWorkingCentre();
  const at = (p: string) => session.hasAtCentre(p, centreId);
  const ok = ['radiology.read', 'radiology.perform', 'radiology.report', 'radiology.verify'].some(at);
  const list = useWorklist(centreId, 'IMAGING', ok);
  const [tab, setTab] = useState('ORDERED');
  const [open, setOpen] = useState<string | undefined>();
  const all = list.data ?? [];
  const tabs: [string, string][] = [['ORDERED', 'To schedule'], ['SCHEDULED', 'Scheduled'], ['ACQUIRED', 'To report'], ['REPORTED', 'To sign'], ['VERIFIED', 'Released (7 days)']];
  return (
    <>
      <PageHeader title="Imaging" subtitle={<>{centreName} · X-ray, CT, MRI, ultrasound, OCT and fundus imaging: schedule, images, reports</>} />
      <MyPositions />
      <Box sx={{ mt: 2 }} />
      <NeedPermission ok={ok} what="imaging access">
        <StatRow>{tabs.slice(0, 4).map(([k, label]) => <StatCard key={k} label={label} value={all.filter((o) => o.status === k).length} />)}</StatRow>
        <Tabs value={tab} onChange={(_, v: string) => setTab(v)} sx={{ mb: 2, borderBottom: 1, borderColor: 'divider' }} variant="scrollable">
          {tabs.map(([k, label]) => <Tab key={k} value={k} label={`${label} (${all.filter((o) => o.status === k).length})`} />)}
        </Tabs>
        <ProblemAlert error={list.error} />
        <DataTable rows={all.filter((o) => o.status === tab)} rowKey={(r) => r.id ?? ''} loading={list.isLoading} empty="Nothing here." onRowClick={(r) => setOpen(r.id)}
          columns={[...columns((r) => setOpen(r.id)).slice(0, 4),
            { key: 'mod', header: 'Modality', render: (r) => r.modality },
            { key: 'img', header: 'Images', render: (r) => r.viewerUrl ? <Link href={r.viewerUrl} target="_blank" rel="noopener" onClick={(e) => e.stopPropagation()}>Open viewer</Link> : r.scheduledAt ? clock(r.scheduledAt) : '' },
            ...columns((r) => setOpen(r.id)).slice(4)]} />
      </NeedPermission>
      {open && <OrderDialog orderId={open} onClose={() => setOpen(undefined)} />}
    </>
  );
}

/** One order: details, results or report, and the next step for the person's role. */
function OrderDialog({ orderId, onClose }: { orderId: string; onClose: () => void }) {
  const session = useSession();
  const q = useQuery({ queryKey: ['diagnostic-order', orderId], queryFn: () => api.diagnosticOrder(orderId) });
  const o = q.data;
  const can = (p: string) => !!o && session.hasAtCentre(p, o.centreId);
  const [values, setValues] = useState<Record<string, string>>({});
  const [note, setNote] = useState('');
  const [when, setWhen] = useState('');
  const [uid, setUid] = useState('');
  const [findings, setFindings] = useState<string | undefined>();
  const [impression, setImpression] = useState<string | undefined>();
  const v = o?.version ?? 0;
  const inv = { invalidate: [['diagnostics'], ['diagnostic-order'], ['patient-diagnostics']], onDone: onClose };
  const step = useAction((s: 'collect' | 'receive' | 'reject' | 'validate' | 'verify' | 'cancel') => {
    const body = { expectedVersion: v, note: note || undefined };
    return s === 'collect' ? api.collectSample(orderId, body) : s === 'receive' ? api.receiveSample(orderId, body) : s === 'reject' ? api.rejectSample(orderId, body)
      : s === 'validate' ? api.validateLabResults(orderId, body) : s === 'verify' ? api.verifyImaging(orderId, body) : api.cancelDiagnosticOrder(orderId, body);
  }, { ...inv, success: (r) => `${r.accessionNo}: ${(STATUS_LABEL[r.status ?? ''] ?? r.status ?? '').toLowerCase()}` });
  const results = useAction(() => api.enterLabResults(orderId, { expectedVersion: v, results: (o?.results ?? []).map((r) => {
    const raw = values[r.parameterId ?? ''] ?? (r.valueNumeric != null ? String(r.valueNumeric) : r.valueText ?? '');
    const n = Number(raw);
    return r.unit !== undefined && raw.trim() !== '' && Number.isFinite(n) && !/[a-z]/i.test(raw) ? { parameterId: r.parameterId!, valueNumeric: n } : { parameterId: r.parameterId!, valueText: raw };
  }) }), { ...inv, success: 'Results saved; another person validates them' });
  const schedule = useAction(() => api.scheduleImaging(orderId, { expectedVersion: v, scheduledAt: new Date(when).toISOString() }), { ...inv, success: 'Scheduled' });
  const acquire = useAction(() => api.acquireImaging(orderId, { expectedVersion: v, studyInstanceUid: uid || undefined }), { ...inv, success: 'Images linked to the order' });
  const report = useAction(() => api.reportImaging(orderId, { expectedVersion: v, findings: findings ?? o?.findings ?? '', impression: impression ?? o?.impression ?? '' }), { ...inv, success: 'Report saved' });
  const busy = step.isPending || results.isPending || schedule.isPending || acquire.isPending || report.isPending;
  const error = step.error ?? results.error ?? schedule.error ?? acquire.error ?? report.error;
  const lab = o?.kind === 'LAB';
  return (
    <FormDialog open title={o ? `${o.testName} · ${o.accessionNo}` : 'Order'} submitLabel="Close" onClose={onClose} onSubmit={onClose} busy={busy} error={error} maxWidth="md">
      <ProblemAlert error={q.error} />
      {o && (
        <>
          <Typography variant="body2">{o.patientName} · {o.patientUhid}{o.patientAge ? ` · ${o.patientAge} y` : ''} · ordered by {o.orderedByName} {clock(o.orderedAt)} <StatusLabel o={o} /><Priority p={o.priority} /></Typography>
          {o.clinicalNotes && <Typography variant="body2" color="text.secondary">Clinical notes: {o.clinicalNotes}</Typography>}
          {lab && <Typography variant="body2" color="text.secondary">Sample: {o.sampleType?.toLowerCase()} · request {o.requestNo} · bill {o.billNo} ({o.billStatus?.toLowerCase()})</Typography>}
          {o.rejectionNote && <Alert severity="warning">Sample rejected: {o.rejectionNote}. Collect again.</Alert>}
          {o.hasCritical && <Alert severity="error">Critical result: inform the treating doctor immediately and record it.</Alert>}
          {lab && (o.results ?? []).length > 0 && (
            <Table size="small">
              <TableHead><TableRow><TableCell>Test</TableCell><TableCell>Result</TableCell><TableCell>Unit</TableCell><TableCell>Reference</TableCell><TableCell /></TableRow></TableHead>
              <TableBody>
                {(o.results ?? []).map((r) => (
                  <TableRow key={r.parameterId}>
                    <TableCell>{r.name}{r.loincCode && <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>LOINC {r.loincCode}</Typography>}</TableCell>
                    <TableCell>
                      {(o.status === 'IN_LAB' || o.status === 'RESULTED') && can('lab.process')
                        ? <TextField size="small" value={values[r.parameterId ?? ''] ?? (r.valueNumeric != null ? String(r.valueNumeric) : r.valueText ?? '')} onChange={(e) => setValues({ ...values, [r.parameterId ?? '']: e.target.value })} sx={{ width: 140 }} />
                        : <strong>{r.valueNumeric ?? r.valueText ?? '–'}</strong>}
                    </TableCell>
                    <TableCell>{r.unit ?? ''}</TableCell>
                    <TableCell>{r.reference ?? ''}</TableCell>
                    <TableCell>{r.flag && FLAG[r.flag] && <Chip size="small" color={FLAG[r.flag]!.color} label={FLAG[r.flag]!.label} />}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
          {lab && o.validatedByName && <Typography variant="caption">Results by {o.resultByName} {clock(o.resultAt)} · validated by {o.validatedByName} {clock(o.validatedAt)}</Typography>}
          {!lab && (
            <>
              <Typography variant="body2" color="text.secondary">{o.modality} · {o.bodyPart ?? ''}{o.scheduledAt ? ` · scheduled ${clock(o.scheduledAt)}` : ''}{o.performedByName ? ` · done by ${o.performedByName} ${clock(o.performedAt)}` : ''}{o.imageCount ? ` · ${o.imageCount} images` : ''}</Typography>
              {o.viewerUrl && <Box><Button variant="outlined" href={o.viewerUrl} target="_blank" rel="noopener">Open images in the viewer</Button></Box>}
              {(o.status === 'ORDERED' || o.status === 'SCHEDULED') && can('radiology.perform') && (
                <Alert severity="info">On the machine, enter accession number <strong>{o.accessionNo}</strong> so the images find this order.</Alert>
              )}
              {o.findings && o.status !== 'ACQUIRED' && !(o.status === 'REPORTED' && can('radiology.report')) && (
                <Box><Typography variant="subtitle2">Findings</Typography><Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>{o.findings}</Typography>
                  <Typography variant="subtitle2" sx={{ mt: 1 }}>Impression</Typography><Typography variant="body2">{o.impression}</Typography>
                  <Typography variant="caption">Reported by {o.reportedByName}{o.verifiedByName ? ` · signed by ${o.verifiedByName} ${clock(o.verifiedAt)}` : ''}</Typography></Box>
              )}
              {(o.status === 'ACQUIRED' || o.status === 'REPORTED') && can('radiology.report') && (
                <>
                  <TextField label="Findings" value={findings ?? o.findings ?? ''} onChange={(e) => setFindings(e.target.value)} multiline minRows={4} />
                  <TextField label="Impression" value={impression ?? o.impression ?? ''} onChange={(e) => setImpression(e.target.value)} multiline minRows={2} />
                </>
              )}
            </>
          )}
          {(o.status === 'ORDERED' || o.status === 'SCHEDULED') && !lab && can('radiology.perform') && (
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
              <TextField label="Appointment" type="datetime-local" value={when} onChange={(e) => setWhen(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
              <TextField label="Study Instance UID (only without an image archive)" value={uid} onChange={(e) => setUid(e.target.value)} sx={{ flex: 1 }} />
            </Stack>
          )}
          {['COLLECTED', 'IN_LAB'].includes(o.status ?? '') && can('lab.process') && <TextField label="Reason, if rejecting the sample" value={note} onChange={(e) => setNote(e.target.value)} />}
          <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', gap: 1 }}>
            {o.status === 'ORDERED' && lab && can('lab.collect') && <Button variant="contained" onClick={() => step.mutate('collect')}>Sample collected</Button>}
            {o.status === 'COLLECTED' && can('lab.process') && <Button variant="contained" onClick={() => step.mutate('receive')}>Receive in the lab</Button>}
            {['COLLECTED', 'IN_LAB'].includes(o.status ?? '') && can('lab.process') && <Button color="warning" onClick={() => step.mutate('reject')} disabled={!note.trim()}>Reject sample</Button>}
            {['IN_LAB', 'RESULTED'].includes(o.status ?? '') && can('lab.process') && <Button variant={o.status === 'IN_LAB' ? 'contained' : 'outlined'} onClick={() => results.mutate(undefined)}>Save results</Button>}
            {o.status === 'RESULTED' && can('lab.validate') && <Button variant="contained" color="success" onClick={() => step.mutate('validate')}>Validate and release</Button>}
            {(o.status === 'ORDERED' || o.status === 'SCHEDULED') && !lab && can('radiology.perform') && (
              <>
                <Button onClick={() => schedule.mutate(undefined)} disabled={!when}>Schedule</Button>
                <Button variant="contained" onClick={() => acquire.mutate(undefined)}>Study done: link images</Button>
              </>
            )}
            {(o.status === 'ACQUIRED' || o.status === 'REPORTED') && can('radiology.report') && <Button variant={o.status === 'ACQUIRED' ? 'contained' : 'outlined'} onClick={() => report.mutate(undefined)}>Save report</Button>}
            {o.status === 'REPORTED' && can('radiology.verify') && <Button variant="contained" color="success" onClick={() => step.mutate('verify')}>Sign and release</Button>}
          </Stack>
        </>
      )}
    </FormDialog>
  );
}

/** For the doctor: order laboratory tests and imaging for a patient (opened from the visit chart). */
export function OrderDiagnosticsDialog({ centreId, patientId, patientName, visitId, onClose }: { centreId: string; patientId: string; patientName?: string; visitId?: string; onClose: () => void }) {
  const session = useSession();
  const cat = useQuery({ queryKey: ['diagnostic-catalogue'], queryFn: () => api.diagnosticCatalogue() });
  const [tests, setTests] = useState<string[]>([]);
  const [procs, setProcs] = useState<string[]>([]);
  const [priority, setPriority] = useState('ROUTINE');
  const [notes, setNotes] = useState('');
  const toggle = (list: string[], set: (l: string[]) => void, id: string) => set(list.includes(id) ? list.filter((x) => x !== id) : [...list, id]);
  const save = useAction(() => api.orderDiagnostics(centreId, { patientId, visitRef: visitId, priority, clinicalNotes: notes || undefined, labTestIds: tests, procedureIds: procs }),
    { invalidate: [['patient-diagnostics'], ['diagnostics']], success: (r) => `${r.length} order(s) placed; request ${r[0]?.requestNo}, bill ${r[0]?.billNo}`, onDone: onClose });
  const canLab = session.hasAtCentre('lab.order', centreId);
  const canImg = session.hasAtCentre('radiology.order', centreId);
  return (
    <FormDialog open title={`Order tests${patientName ? ` for ${patientName}` : ''}`} submitLabel="Place orders" onClose={onClose} onSubmit={() => save.mutate(undefined)} busy={save.isPending} error={save.error} maxWidth="md">
      <ProblemAlert error={cat.error} />
      {canLab && (
        <Box>
          <Typography variant="subtitle2">Laboratory</Typography>
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' } }}>
            {(cat.data?.labTests ?? []).map((t) => <FormControlLabel key={t.id} control={<Checkbox checked={tests.includes(t.id!)} onChange={() => toggle(tests, setTests, t.id!)} />} label={<>{t.name} <Typography component="span" variant="caption" color="text.secondary">₹{t.price}</Typography></>} />)}
          </Box>
        </Box>
      )}
      {canImg && (
        <Box>
          <Typography variant="subtitle2">Imaging</Typography>
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' } }}>
            {(cat.data?.procedures ?? []).map((p) => <FormControlLabel key={p.id} control={<Checkbox checked={procs.includes(p.id!)} onChange={() => toggle(procs, setProcs, p.id!)} />} label={<>{p.name} <Typography component="span" variant="caption" color="text.secondary">{p.modality} · ₹{p.price}</Typography></>} />)}
          </Box>
        </Box>
      )}
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
        <TextField select label="Priority" value={priority} onChange={(e) => setPriority(e.target.value)} sx={{ minWidth: 160 }}>
          <MenuItem value="ROUTINE">Routine</MenuItem><MenuItem value="URGENT">Urgent</MenuItem><MenuItem value="STAT">Immediately (STAT)</MenuItem>
        </TextField>
        <TextField label="Clinical notes for the lab or radiologist" value={notes} onChange={(e) => setNotes(e.target.value)} sx={{ flex: 1 }} />
      </Stack>
    </FormDialog>
  );
}

/** The patient's laboratory and imaging orders with released results, for the visit chart. */
export function PatientDiagnostics({ patientId }: { patientId: string }) {
  const q = useQuery({ queryKey: ['patient-diagnostics', patientId], queryFn: () => api.patientDiagnostics(patientId) });
  const [open, setOpen] = useState<string | undefined>();
  if (!q.data?.length) {
    return <Typography variant="body2" color="text.secondary">No tests ordered.</Typography>;
  }
  return (
    <Stack spacing={1}>
      {q.data.map((o) => (
        <Box key={o.id}>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
            <Link component="button" type="button" onClick={() => setOpen(o.id)} sx={{ fontWeight: 600 }}>{o.testName}</Link>
            <StatusLabel o={o} />
            {o.hasCritical && <Chip size="small" color="error" label="critical" />}
            {o.viewerUrl && <Link href={o.viewerUrl} target="_blank" rel="noopener">Images</Link>}
            <Typography variant="caption" color="text.secondary">{clock(o.orderedAt)}</Typography>
          </Stack>
          {o.released && (o.results ?? []).map((r) => (
            <Typography key={r.parameterId} variant="body2" color={r.flag && r.flag !== 'N' ? 'error.main' : 'text.secondary'}>
              {r.name}: {r.valueNumeric ?? r.valueText} {r.unit ?? ''} {r.flag && r.flag !== 'N' ? `(${FLAG[r.flag]?.label ?? r.flag})` : ''}
            </Typography>
          ))}
          {o.released && o.impression && <Typography variant="body2" color="text.secondary">{o.impression}</Typography>}
        </Box>
      ))}
      {open && <OrderDialog orderId={open} onClose={() => setOpen(undefined)} />}
    </Stack>
  );
}
