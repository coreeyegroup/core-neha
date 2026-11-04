import { Alert, Box, Button, Grid, Paper, Table, TableBody, TableCell, TableHead, TableRow, TextField, Typography } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link as RouterLink, useParams } from 'react-router';

import * as api from '../../api/generated/core-neha';
import { useAction } from '../../api/useAction';
import { useSession } from '../../auth/session';
import { FormDialog } from '../../components/FormDialog';
import { PageHeader } from '../../components/PageHeader';
import { ProblemAlert } from '../../components/ProblemAlert';
import { KeyValue, Section } from '../../components/Section';
import { StatusChip } from '../../components/StatusChip';
import { clock, dateLabel, money } from './common';
import { ChargeLines, CollectPaymentDialog, ReasonDialog } from './dialogs';

export function BillPage() {
  const { billId = '' } = useParams();
  const session = useSession();
  const detail = useQuery({ queryKey: ['bill', billId], queryFn: () => api.bill(billId) });
  const [dialog, setDialog] = useState<'collect' | 'add' | 'discount' | 'decide' | 'cancel' | undefined>();
  const removeLine = useAction((lineId: string) => api.removeBillLine(billId, { lineId, expectedVersion: detail.data?.bill?.version ?? 0 }),
    { invalidate: [['bill'], ['bills'], ['visit']], success: 'Charge removed' });
  if (detail.error) {
    return <ProblemAlert error={detail.error} />;
  }
  const b = detail.data?.bill;
  if (!b) {
    return <Typography color="text.secondary">Loading…</Typography>;
  }
  const c = b.centreId;
  const open = b.status === 'OPEN';
  const pending = b.discountStatus === 'PENDING';
  const noPayments = (detail.data?.payments ?? []).length === 0;
  return (
    <>
      <PageHeader title={`Bill ${b.billNo}`} subtitle={<>{b.patientName} · {b.patientUhid} · {dateLabel(b.billDate)} · {b.centreName}</>}
        actions={
          <>
            <Button onClick={() => window.print()}>Print</Button>
            {b.visitRef && <Button component={RouterLink} to={`/visits/${b.visitRef}`}>Visit</Button>}
            {b.surgeryRef && <Button component={RouterLink} to={`/surgeries/${b.surgeryRef}`}>Surgery</Button>}
            {open && !pending && session.hasAtCentre('billing.charge', c) && <Button onClick={() => setDialog('add')}>Add charges</Button>}
            {open && (b.discountStatus === 'NONE' || b.discountStatus === 'REJECTED') && session.hasAtCentre('billing.charge', c) && (
              <Button onClick={() => setDialog('discount')}>Request discount…</Button>
            )}
            {open && pending && session.hasAtCentre('billing.discount.approve', c) && (
              <Button variant="contained" color="warning" onClick={() => setDialog('decide')}>Decide discount…</Button>
            )}
            {open && noPayments && session.hasAtCentre('billing.discount.approve', c) && <Button color="error" onClick={() => setDialog('cancel')}>Cancel bill…</Button>}
            {open && !pending && (b.balance ?? 0) > 0 && session.hasAtCentre('billing.collect', c) && (
              <Button variant="contained" onClick={() => setDialog('collect')}>Collect {money(b.balance)}</Button>
            )}
          </>
        } />
      {pending && <Alert severity="warning" sx={{ mb: 2 }}>Discount of {money(b.discountRequested)} requested by {b.discountRequestedByName}: “{b.discountReason}”. Payment waits for an approver.</Alert>}
      {b.status === 'CANCELLED' && <Alert severity="info" sx={{ mb: 2 }}>Cancelled: {b.cancelReason}</Alert>}
      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 8 }}>
          <Section title="Charges">
            <Paper>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Charge</TableCell>
                    <TableCell align="right">Qty</TableCell>
                    <TableCell align="right">Rate</TableCell>
                    <TableCell align="right">GST</TableCell>
                    <TableCell align="right">Amount</TableCell>
                    <TableCell />
                  </TableRow>
                </TableHead>
                <TableBody>
                  {(detail.data?.lines ?? []).map((l) => (
                    <TableRow key={l.id}>
                      <TableCell>{l.description}<Typography variant="caption" color="text.secondary" component="div">{(l.category ?? '').replaceAll('_', ' ').toLowerCase()}</Typography></TableCell>
                      <TableCell align="right">{l.quantity}</TableCell>
                      <TableCell align="right">{money(l.unitPrice)}</TableCell>
                      <TableCell align="right">{(l.gstRate ?? 0) > 0 ? money(l.taxAmount) : '—'}</TableCell>
                      <TableCell align="right">{money((l.amount ?? 0) + (l.taxAmount ?? 0))}</TableCell>
                      <TableCell align="right">
                        {open && noPayments && b.discountStatus !== 'PENDING' && b.discountStatus !== 'APPROVED' && session.hasAtCentre('billing.charge', c) && (
                          <Button size="small" onClick={() => removeLine.mutate(l.id!)}>Remove</Button>
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                  <TableRow><TableCell colSpan={4}>Gross</TableCell><TableCell align="right">{money((b.grossAmount ?? 0) + (b.taxAmount ?? 0))}</TableCell><TableCell /></TableRow>
                  {(b.discountAmount ?? 0) > 0 && <TableRow><TableCell colSpan={4}>Discount ({b.discountReason})</TableCell><TableCell align="right">− {money(b.discountAmount)}</TableCell><TableCell /></TableRow>}
                  <TableRow><TableCell colSpan={4} sx={{ fontWeight: 600 }}>Net payable</TableCell><TableCell align="right" sx={{ fontWeight: 600 }}>{money(b.netAmount)}</TableCell><TableCell /></TableRow>
                  <TableRow><TableCell colSpan={4}>Paid</TableCell><TableCell align="right">{money(b.paidAmount)}</TableCell><TableCell /></TableRow>
                  <TableRow><TableCell colSpan={4} sx={{ fontWeight: 600 }}>Balance</TableCell><TableCell align="right" sx={{ fontWeight: 600 }}>{money(b.balance)}</TableCell><TableCell /></TableRow>
                </TableBody>
              </Table>
              <ProblemAlert error={removeLine.error} />
            </Paper>
          </Section>
          <Section title="Payments">
            <Paper>
              <Table size="small">
                <TableHead><TableRow><TableCell>When</TableCell><TableCell>Mode</TableCell><TableCell>Reference</TableCell><TableCell>Received by</TableCell><TableCell align="right">Amount</TableCell></TableRow></TableHead>
                <TableBody>
                  {noPayments && <TableRow><TableCell colSpan={5}><Typography variant="body2" color="text.secondary">No payments yet.</Typography></TableCell></TableRow>}
                  {(detail.data?.payments ?? []).map((p) => (
                    <TableRow key={p.id}>
                      <TableCell>{dateLabel(p.businessDate)} {clock(p.receivedAt)}</TableCell>
                      <TableCell>{p.mode}</TableCell>
                      <TableCell>{p.reference ?? '—'}</TableCell>
                      <TableCell>{p.receivedByName}</TableCell>
                      <TableCell align="right">{money(p.amount)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Paper>
          </Section>
        </Grid>
        <Grid size={{ xs: 12, md: 4 }}>
          <KeyValue items={[
            ['Status', <StatusChip key="s" status={b.status} />],
            ['Patient', <RouterLink key="p" to={`/patients/${b.patientId}`}>{b.patientName}</RouterLink>],
            ['UHID', b.patientUhid],
            ['Raised by', `${b.createdByName} at ${clock(b.createdAt)}`],
            ['Discount', b.discountStatus === 'NONE' ? 'None' : `${b.discountStatus?.toLowerCase()} · ${money(b.discountRequested)}${b.discountDecidedByName ? ` · decided by ${b.discountDecidedByName}` : ''}`],
          ]} />
        </Grid>
      </Grid>
      {dialog === 'collect' && <CollectPaymentDialog bill={b} onClose={() => setDialog(undefined)} />}
      {dialog === 'add' && <AddChargesDialog bill={b} onClose={() => setDialog(undefined)} />}
      {dialog === 'discount' && <DiscountDialog bill={b} onClose={() => setDialog(undefined)} />}
      {dialog === 'decide' && <DecideDiscountDialog bill={b} onClose={() => setDialog(undefined)} />}
      {dialog === 'cancel' && (
        <ReasonDialog title={`Cancel bill ${b.billNo}`} submitLabel="Cancel bill" onClose={() => setDialog(undefined)} success="Bill cancelled"
          run={(reason) => api.cancelBill(b.id!, { reason, expectedVersion: b.version ?? 0 })} />
      )}
    </>
  );
}

function AddChargesDialog({ bill, onClose }: { bill: api.BillView; onClose: () => void }) {
  const [lines, setLines] = useState<Array<{ id: string; qty: number }>>([]);
  const action = useAction(() => api.addBillLines(bill.id!, { lines: lines.map((l) => ({ tariffItemId: l.id, quantity: l.qty })), expectedVersion: bill.version ?? 0 }),
    { invalidate: [['bill'], ['bills'], ['visit']], success: 'Charges added', onDone: onClose });
  return (
    <FormDialog open title={`Add charges · ${bill.billNo}`} submitLabel="Add" onClose={onClose} onSubmit={() => action.mutate(undefined)} busy={action.isPending} error={action.error} maxWidth="md">
      <ChargeLines centreId={bill.centreId!} lines={lines} setLines={setLines} />
    </FormDialog>
  );
}

function DiscountDialog({ bill, onClose }: { bill: api.BillView; onClose: () => void }) {
  const [amount, setAmount] = useState('');
  const [reason, setReason] = useState('');
  const action = useAction(() => api.requestDiscount(bill.id!, { amount: Number(amount), reason, expectedVersion: bill.version ?? 0 }),
    { invalidate: [['bill'], ['bills'], ['visit'], ['discounts']], success: 'Discount requested; an approver must decide it', onDone: onClose });
  return (
    <FormDialog open title={`Request a discount · ${bill.billNo}`} submitLabel="Request" onClose={onClose} onSubmit={() => action.mutate(undefined)} busy={action.isPending} error={action.error}
      intro={<Typography variant="body2" color="text.secondary">Someone other than you (front office supervisor or accounts manager) approves it. Payment waits until then.</Typography>}>
      <TextField label="Discount (₹)" type="number" value={amount} onChange={(e) => setAmount(e.target.value)} required autoFocus />
      <TextField label="Reason" value={reason} onChange={(e) => setReason(e.target.value)} required placeholder="Senior citizen concession" />
    </FormDialog>
  );
}

export function DecideDiscountDialog({ bill, onClose }: { bill: api.BillView; onClose: () => void }) {
  const [note, setNote] = useState('');
  const decide = useAction((approve: boolean) => api.decideDiscount(bill.id!, { approve, note: note || undefined, expectedVersion: bill.version ?? 0 }),
    { invalidate: [['bill'], ['bills'], ['visit'], ['discounts'], ['collections']], success: (r) => `Discount ${r.bill?.discountStatus?.toLowerCase()}`, onDone: onClose });
  return (
    <FormDialog open title={`Discount on ${bill.billNo}`} submitLabel="Approve" onClose={onClose} onSubmit={() => decide.mutate(true)} busy={decide.isPending} error={decide.error}
      intro={
        <Box>
          <Typography variant="body2"><b>{money(bill.discountRequested)}</b> off {money((bill.grossAmount ?? 0) + (bill.taxAmount ?? 0))} for {bill.patientName}</Typography>
          <Typography variant="body2" color="text.secondary">Requested by {bill.discountRequestedByName}: “{bill.discountReason}”</Typography>
        </Box>
      }>
      <TextField label="Decision note" value={note} onChange={(e) => setNote(e.target.value)} />
      <Button color="error" onClick={() => decide.mutate(false)} disabled={decide.isPending}>Reject instead</Button>
    </FormDialog>
  );
}
