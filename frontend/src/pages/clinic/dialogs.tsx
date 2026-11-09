import {
  Alert,
  Autocomplete,
  Box,
  Checkbox,
  FormControlLabel,
  IconButton,
  MenuItem,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import DeleteOutline from '@mui/icons-material/DeleteOutlined';
import { useQuery } from '@tanstack/react-query';
import { useMemo, useState, type ChangeEvent } from 'react';

import * as api from '../../api/generated/core-neha';
import { ApiError } from '../../api/http';
import { useAction } from '../../api/useAction';
import { FormDialog } from '../../components/FormDialog';
import { ageSex, money, todayIst } from './common';

// ---------------------------------------------------------------- patient search

export function PatientPicker({ value, onChange, autoFocus, label = 'Patient', required = true }: { value?: api.PatientView; onChange: (p: api.PatientView | undefined) => void; autoFocus?: boolean; label?: string; required?: boolean }) {
  const [input, setInput] = useState('');
  const q = input.trim();
  const results = useQuery({
    queryKey: ['patients', 'search', q],
    queryFn: () => api.searchPatients({ q, limit: 20 }),
    enabled: q.length >= 2,
    staleTime: 10_000,
  });
  return (
    <Autocomplete<api.PatientView>
      options={results.data ?? []}
      value={value ?? null}
      onChange={(_, v) => onChange(v ?? undefined)}
      inputValue={input}
      onInputChange={(_, v) => setInput(v)}
      filterOptions={(x) => x}
      loading={results.isFetching}
      getOptionLabel={(p) => `${p.fullName} · ${p.uhid}`}
      isOptionEqualToValue={(a, b) => a.id === b.id}
      noOptionsText={q.length < 2 ? 'Type a name, mobile number or UHID' : 'No patient found'}
      renderOption={(props, p) => (
        <li {...props} key={p.id}>
          <Box>
            <Typography variant="body2" sx={{ fontWeight: 600 }}>{p.fullName}</Typography>
            <Typography variant="caption" color="text.secondary">
              {p.uhid} · {ageSex(p.ageYears, p.sex)} · {p.mobile ?? 'no mobile'} · {p.city ?? ''}
            </Typography>
          </Box>
        </li>
      )}
      renderInput={(params) => <TextField {...params} label={label} placeholder="Name, mobile or UHID" autoFocus={autoFocus} required={required} />}
    />
  );
}

// ---------------------------------------------------------------- register

const emptyPatient = { givenName: '', familyName: '', sex: '', ageYears: '', birthDate: '', mobile: '', email: '', addressLine: '', city: 'Bengaluru', state: 'Karnataka', pincode: '', abhaNumber: '', guardianName: '', emergencyContactName: '', emergencyContactPhone: '' };

export function RegisterPatientDialog({ centreId, onClose, onDone }: { centreId: string; onClose: () => void; onDone?: (p: api.PatientView) => void }) {
  const [f, setF] = useState(emptyPatient);
  const [confirm, setConfirm] = useState(false);
  const set = (k: keyof typeof emptyPatient) => (e: ChangeEvent<HTMLInputElement>) => setF({ ...f, [k]: e.target.value });
  const action = useAction(
    () => api.registerPatient({
      centreId,
      givenName: f.givenName,
      familyName: f.familyName,
      sex: f.sex,
      ageYears: f.birthDate ? undefined : f.ageYears ? Number(f.ageYears) : undefined,
      birthDate: f.birthDate || undefined,
      mobile: f.mobile || undefined,
      email: f.email || undefined,
      addressLine: f.addressLine || undefined,
      city: f.city || undefined,
      state: f.state || undefined,
      pincode: f.pincode || undefined,
      abhaNumber: f.abhaNumber || undefined,
      guardianName: f.guardianName || undefined,
      emergencyContactName: f.emergencyContactName || undefined,
      emergencyContactPhone: f.emergencyContactPhone || undefined,
      confirmNotDuplicate: confirm,
    }),
    { invalidate: [['patients']], success: (p) => `Registered ${p.fullName}, UHID ${p.uhid}`, onDone: (p) => { onDone?.(p); onClose(); } },
  );
  const duplicate = action.error instanceof ApiError && action.error.code === 'patient.possible-duplicate';
  return (
    <FormDialog open title="Register a new patient" submitLabel={duplicate && confirm ? 'Register as a new patient' : 'Register'} onClose={onClose}
      onSubmit={() => action.mutate(undefined)} busy={action.isPending} error={action.error} maxWidth="md"
      intro={<Typography variant="body2" color="text.secondary">One UHID for the patient at every centre of the group. Search first to avoid duplicates.</Typography>}>
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr 1fr' }, gap: 2 }}>
        <TextField label="Given name" value={f.givenName} onChange={set('givenName')} required autoFocus />
        <TextField label="Family name" value={f.familyName} onChange={set('familyName')} />
        <TextField select label="Sex" value={f.sex} onChange={set('sex')} required>
          <MenuItem value="FEMALE">Female</MenuItem>
          <MenuItem value="MALE">Male</MenuItem>
          <MenuItem value="OTHER">Other</MenuItem>
          <MenuItem value="UNKNOWN">Not stated</MenuItem>
        </TextField>
        <TextField label="Date of birth" type="date" value={f.birthDate} onChange={set('birthDate')} slotProps={{ inputLabel: { shrink: true } }} />
        <TextField label="or age (years)" type="number" value={f.ageYears} onChange={set('ageYears')} disabled={!!f.birthDate} />
        <TextField label="Mobile" value={f.mobile} onChange={set('mobile')} placeholder="98450 12345" />
        <TextField label="Address" value={f.addressLine} onChange={set('addressLine')} sx={{ gridColumn: { sm: 'span 2' } }} />
        <TextField label="PIN code" value={f.pincode} onChange={set('pincode')} />
        <TextField label="City" value={f.city} onChange={set('city')} />
        <TextField label="State" value={f.state} onChange={set('state')} />
        <TextField label="Email" value={f.email} onChange={set('email')} />
        <TextField label="ABHA number (optional)" value={f.abhaNumber} onChange={set('abhaNumber')} placeholder="12-3456-7890-1234" />
        <TextField label="Guardian (for a child)" value={f.guardianName} onChange={set('guardianName')} />
        <TextField label="Emergency contact" value={f.emergencyContactName} onChange={set('emergencyContactName')} />
        <TextField label="Emergency contact phone" value={f.emergencyContactPhone} onChange={set('emergencyContactPhone')} />
      </Box>
      {duplicate && (
        <FormControlLabel control={<Checkbox checked={confirm} onChange={(e) => setConfirm(e.target.checked)} />}
          label="I have checked: this is a different person, register a new UHID" />
      )}
    </FormDialog>
  );
}

// ---------------------------------------------------------------- doctors

export function useClinicians(centreId: string | undefined, department?: string) {
  return useQuery({
    queryKey: ['clinicians', centreId, department],
    queryFn: () => api.clinicians(centreId!, { department }),
    enabled: !!centreId,
    staleTime: 60_000,
  });
}

function DoctorSelect({ centreId, value, onChange, label = 'Doctor', department = 'OPHTHALMOLOGY', required }: { centreId: string; value: string; onChange: (v: string) => void; label?: string; department?: string; required?: boolean }) {
  const doctors = useClinicians(centreId, department);
  return (
    <TextField select label={label} value={value} onChange={(e) => onChange(e.target.value)} required={required}>
      {!required && <MenuItem value="">Any available</MenuItem>}
      {(doctors.data ?? []).map((d) => (
        <MenuItem key={d.personId} value={d.personId}>{d.name} · {d.positionTitle}</MenuItem>
      ))}
    </TextField>
  );
}

export { DoctorSelect };

// ---------------------------------------------------------------- appointments and walk-in

export function BookAppointmentDialog({ centreId, onClose, patient: initial }: { centreId: string; onClose: () => void; patient?: api.PatientView }) {
  const [patient, setPatient] = useState<api.PatientView | undefined>(initial);
  const [date, setDate] = useState(todayIst());
  const [time, setTime] = useState('10:00');
  const [visitType, setVisitType] = useState('NEW');
  const [source, setSource] = useState('PHONE');
  const [doctor, setDoctor] = useState('');
  const [reason, setReason] = useState('');
  const action = useAction(
    () => api.bookAppointment(centreId, {
      patientId: patient?.id ?? '', appointmentDate: date, slotTime: `${time}:00`, visitType, source,
      consultantPersonId: doctor || undefined, reason: reason || undefined,
    }),
    { invalidate: [['appointments'], ['clinic-board']], success: (a) => `Appointment booked for ${a.patientName} at ${time}`, onDone: onClose },
  );
  return (
    <FormDialog open title="Book an appointment" submitLabel="Book" onClose={onClose} onSubmit={() => action.mutate(undefined)} busy={action.isPending} error={action.error}>
      <PatientPicker value={patient} onChange={setPatient} autoFocus={!initial} />
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
        <TextField label="Date" type="date" value={date} onChange={(e) => setDate(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} fullWidth />
        <TextField label="Time" type="time" value={time} onChange={(e) => setTime(e.target.value)} slotProps={{ inputLabel: { shrink: true }, htmlInput: { step: 900 } }} fullWidth />
      </Stack>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
        <TextField select label="Visit type" value={visitType} onChange={(e) => setVisitType(e.target.value)} fullWidth>
          <MenuItem value="NEW">New consultation</MenuItem>
          <MenuItem value="REVIEW">Review</MenuItem>
          <MenuItem value="POST_OP">Post-operative review</MenuItem>
        </TextField>
        <TextField select label="Booked by" value={source} onChange={(e) => setSource(e.target.value)} fullWidth>
          <MenuItem value="PHONE">Phone</MenuItem>
          <MenuItem value="WALK_IN">At the desk</MenuItem>
          <MenuItem value="ONLINE">Online</MenuItem>
          <MenuItem value="REFERRAL">Referral</MenuItem>
        </TextField>
      </Stack>
      <DoctorSelect centreId={centreId} value={doctor} onChange={setDoctor} />
      <TextField label="Reason (optional)" value={reason} onChange={(e) => setReason(e.target.value)} />
    </FormDialog>
  );
}

export function WalkInDialog({ centreId, onClose, patient: initial }: { centreId: string; onClose: () => void; patient?: api.PatientView }) {
  const [patient, setPatient] = useState<api.PatientView | undefined>(initial);
  const [visitType, setVisitType] = useState('NEW');
  const [doctor, setDoctor] = useState('');
  const [complaint, setComplaint] = useState('');
  const action = useAction(
    () => api.startWalkInVisit(centreId, { patientId: patient?.id ?? '', visitType, consultantPersonId: doctor || undefined, chiefComplaint: complaint || undefined }),
    { invalidate: [['visits'], ['clinic-board']], success: (v) => `Token ${v.tokenNo}: ${v.patientName} sent to optometry`, onDone: onClose },
  );
  return (
    <FormDialog open title="Check in a walk-in patient" submitLabel="Check in" onClose={onClose} onSubmit={() => action.mutate(undefined)} busy={action.isPending} error={action.error}
      intro={<Typography variant="body2" color="text.secondary">The patient gets today’s token and joins the optometry queue.</Typography>}>
      <PatientPicker value={patient} onChange={setPatient} autoFocus={!initial} />
      <TextField select label="Visit type" value={visitType} onChange={(e) => setVisitType(e.target.value)}>
        <MenuItem value="NEW">New consultation</MenuItem>
        <MenuItem value="REVIEW">Review</MenuItem>
        <MenuItem value="POST_OP">Post-operative review</MenuItem>
      </TextField>
      <DoctorSelect centreId={centreId} value={doctor} onChange={setDoctor} />
      <TextField label="Chief complaint" value={complaint} onChange={(e) => setComplaint(e.target.value)} multiline minRows={2} />
    </FormDialog>
  );
}

export function CheckInDialog({ appointment, centreId, onClose }: { appointment: api.AppointmentView; centreId: string; onClose: () => void }) {
  const [doctor, setDoctor] = useState(appointment.consultantPersonId ?? '');
  const [complaint, setComplaint] = useState(appointment.reason ?? '');
  const action = useAction(
    () => api.checkInAppointment(appointment.id!, { consultantPersonId: doctor || undefined, chiefComplaint: complaint || undefined, expectedVersion: appointment.version ?? 0 }),
    { invalidate: [['appointments'], ['visits'], ['clinic-board']], success: (v) => `Token ${v.tokenNo}: ${v.patientName} checked in`, onDone: onClose },
  );
  return (
    <FormDialog open title={`Check in ${appointment.patientName}`} submitLabel="Check in" onClose={onClose} onSubmit={() => action.mutate(undefined)} busy={action.isPending} error={action.error}>
      <DoctorSelect centreId={centreId} value={doctor} onChange={setDoctor} />
      <TextField label="Chief complaint" value={complaint} onChange={(e) => setComplaint(e.target.value)} multiline minRows={2} autoFocus />
    </FormDialog>
  );
}

// ---------------------------------------------------------------- reasons

export function ReasonDialog({ title, label = 'Reason', submitLabel, onClose, run, success, extra }: {
  title: string; label?: string; submitLabel: string; onClose: () => void; run: (reason: string, flag: boolean) => Promise<unknown>; success: string;
  extra?: string;
}) {
  const [reason, setReason] = useState('');
  const [flag, setFlag] = useState(false);
  const action = useAction(() => run(reason, flag), { invalidate: [['appointments'], ['visits'], ['clinic-board'], ['bill'], ['bills'], ['surgeries'], ['surgery'], ['visit'], ['spectacle-orders']], success, onDone: onClose });
  return (
    <FormDialog open title={title} submitLabel={submitLabel} onClose={onClose} onSubmit={() => action.mutate(undefined)} busy={action.isPending} error={action.error}>
      <TextField label={label} value={reason} onChange={(e) => setReason(e.target.value)} required autoFocus multiline minRows={2} />
      {extra && <FormControlLabel control={<Checkbox checked={flag} onChange={(e) => setFlag(e.target.checked)} />} label={extra} />}
    </FormDialog>
  );
}

// ---------------------------------------------------------------- billing

export function useTariff(centreId: string | undefined) {
  return useQuery({ queryKey: ['tariff', centreId], queryFn: () => api.tariff(centreId!), enabled: !!centreId, staleTime: 60_000 });
}

/** Suggested charges for a visit: consultation by visit type, plus refraction for a new patient. */
function suggested(items: api.TariffItemView[], visitType?: string): string[] {
  const code = visitType === 'REVIEW' ? 'CONSULT_REVIEW' : visitType === 'POST_OP' ? 'CONSULT_POST_OP' : 'CONSULT_NEW';
  const wanted = visitType === 'NEW' || !visitType ? [code, 'REFRACTION'] : [code];
  return items.filter((i) => wanted.includes(i.code ?? '')).map((i) => i.id!);
}

export function ChargeLines({ centreId, lines, setLines }: { centreId: string; lines: Array<{ id: string; qty: number }>; setLines: (l: Array<{ id: string; qty: number }>) => void }) {
  const tariff = useTariff(centreId);
  const items = useMemo(() => tariff.data ?? [], [tariff.data]);
  const byId = useMemo(() => new Map(items.map((i) => [i.id!, i])), [items]);
  const [pick, setPick] = useState<api.TariffItemView | null>(null);
  const total = lines.reduce((s, l) => {
    const i = byId.get(l.id);
    return s + (i ? (i.price ?? 0) * l.qty * (1 + (i.gstRate ?? 0) / 100) : 0);
  }, 0);
  return (
    <>
      <Autocomplete<api.TariffItemView>
        options={items}
        value={pick}
        groupBy={(i) => (i.category ?? '').replaceAll('_', ' ').toLowerCase()}
        getOptionLabel={(i) => `${i.name} · ${money(i.price)}`}
        onChange={(_, v) => {
          if (v && !lines.some((l) => l.id === v.id)) {
            setLines([...lines, { id: v.id!, qty: 1 }]);
          }
          setPick(null);
        }}
        renderInput={(params) => <TextField {...params} label="Add a charge from the price list" />}
      />
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>Charge</TableCell>
            <TableCell align="right" sx={{ width: 90 }}>Qty</TableCell>
            <TableCell align="right">Amount</TableCell>
            <TableCell sx={{ width: 40 }} />
          </TableRow>
        </TableHead>
        <TableBody>
          {lines.length === 0 && (
            <TableRow><TableCell colSpan={4}><Typography variant="body2" color="text.secondary">No charges yet.</Typography></TableCell></TableRow>
          )}
          {lines.map((l) => {
            const i = byId.get(l.id);
            return (
              <TableRow key={l.id}>
                <TableCell>{i?.name}{(i?.gstRate ?? 0) > 0 ? ` (GST ${i?.gstRate}%)` : ''}</TableCell>
                <TableCell align="right">
                  <TextField type="number" value={l.qty} onChange={(e) => setLines(lines.map((x) => (x.id === l.id ? { ...x, qty: Math.max(1, Number(e.target.value) || 1) } : x)))}
                    slotProps={{ htmlInput: { min: 1, max: 999, 'aria-label': `Quantity of ${i?.name}` } }} sx={{ width: 72 }} />
                </TableCell>
                <TableCell align="right">{money((i?.price ?? 0) * l.qty)}</TableCell>
                <TableCell>
                  <IconButton aria-label={`Remove ${i?.name}`} onClick={() => setLines(lines.filter((x) => x.id !== l.id))}><DeleteOutline fontSize="small" /></IconButton>
                </TableCell>
              </TableRow>
            );
          })}
          <TableRow>
            <TableCell colSpan={2} sx={{ fontWeight: 600 }}>Total (with GST)</TableCell>
            <TableCell align="right" sx={{ fontWeight: 600 }}>{money(total)}</TableCell>
            <TableCell />
          </TableRow>
        </TableBody>
      </Table>
    </>
  );
}

export function CreateBillDialog({ centreId, patientId, patientName, visitId, visitType, surgeryId, packageItemId, onClose, onDone }: {
  centreId: string; patientId: string; patientName?: string; visitId?: string; visitType?: string; surgeryId?: string; packageItemId?: string;
  onClose: () => void; onDone?: (b: api.BillDetail) => void;
}) {
  const tariff = useTariff(centreId);
  const [lines, setLines] = useState<Array<{ id: string; qty: number }> | undefined>();
  const initial = useMemo(() => {
    if (!tariff.data) {
      return undefined;
    }
    if (packageItemId) {
      return [{ id: packageItemId, qty: 1 }];
    }
    return suggested(tariff.data, visitType).map((id) => ({ id, qty: 1 }));
  }, [tariff.data, visitType, packageItemId]);
  const current = lines ?? initial ?? [];
  const action = useAction(
    () => api.createBill(centreId, { patientId, visitRef: visitId, surgeryRef: surgeryId, lines: current.map((l) => ({ tariffItemId: l.id, quantity: l.qty })) }),
    { invalidate: [['bills'], ['visit'], ['visits'], ['surgery']], success: (b) => `Bill ${b.bill?.billNo} raised for ${money(b.bill?.netAmount)}`, onDone: (b) => { onDone?.(b); onClose(); } },
  );
  return (
    <FormDialog open title={`New bill · ${patientName ?? ''}`} submitLabel="Raise bill" onClose={onClose} onSubmit={() => action.mutate(undefined)} busy={action.isPending} error={action.error} maxWidth="md"
      intro={<Typography variant="body2" color="text.secondary">{surgeryId ? 'Surgery package bill.' : 'Suggested from the visit type; change as needed.'} Prices come from this centre’s price list.</Typography>}>
      <ChargeLines centreId={centreId} lines={current} setLines={setLines} />
    </FormDialog>
  );
}

export function CollectPaymentDialog({ bill, onClose }: { bill: api.BillView; onClose: () => void }) {
  const [mode, setMode] = useState('CASH');
  const [amount, setAmount] = useState(String(bill.balance ?? 0));
  const [reference, setReference] = useState('');
  const action = useAction(
    () => api.collectPayment(bill.id!, { mode, amount: Number(amount), reference: reference || undefined, expectedVersion: bill.version ?? 0 }),
    { invalidate: [['bill'], ['bills'], ['visit'], ['visits'], ['collections']], success: (b) => (b.bill?.status === 'PAID' ? `Bill ${b.bill?.billNo} fully paid` : `Received ${money(Number(amount))}; balance ${money(b.bill?.balance)}`), onDone: onClose },
  );
  return (
    <FormDialog open title={`Collect payment · ${bill.billNo}`} submitLabel={`Receive ${money(Number(amount) || 0)}`} onClose={onClose} onSubmit={() => action.mutate(undefined)} busy={action.isPending} error={action.error}
      intro={<Alert severity="info">Balance due: <b>{money(bill.balance)}</b> of {money(bill.netAmount)}</Alert>}>
      <TextField select label="Mode" value={mode} onChange={(e) => setMode(e.target.value)}>
        <MenuItem value="CASH">Cash</MenuItem>
        <MenuItem value="UPI">UPI</MenuItem>
        <MenuItem value="CARD">Card</MenuItem>
        <MenuItem value="INSURANCE">Insurance / TPA</MenuItem>
        <MenuItem value="CORPORATE">Corporate</MenuItem>
      </TextField>
      <TextField label="Amount (₹)" type="number" value={amount} onChange={(e) => setAmount(e.target.value)} required autoFocus />
      {mode !== 'CASH' && <TextField label={mode === 'UPI' ? 'UPI transaction id' : mode === 'CARD' ? 'Card approval code' : 'Authorisation / reference'} value={reference} onChange={(e) => setReference(e.target.value)} required />}
    </FormDialog>
  );
}
