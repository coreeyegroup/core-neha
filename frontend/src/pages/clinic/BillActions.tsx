import { Button, Chip, Stack } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { useState, type MouseEvent } from 'react';
import { useNavigate } from 'react-router';

import * as api from '../../api/generated/core-neha';
import { useAction } from '../../api/useAction';
import { useSession } from '../../auth/session';
import { money } from './common';
import { CollectPaymentDialog, CreateBillDialog, ReasonDialog } from './dialogs';

function CollectForBill({ billId, onClose }: { billId: string; onClose: () => void }) {
  const bill = useQuery({ queryKey: ['bill', billId], queryFn: () => api.bill(billId) });
  return bill.data?.bill ? <CollectPaymentDialog bill={bill.data.bill} onClose={onClose} /> : null;
}

/** What front office can do next for a visit's bills: raise, collect, complete checkout. */
export function BillActions({ visit, chart: given }: { visit: api.VisitView; chart?: api.VisitChart }) {
  const session = useSession();
  const navigate = useNavigate();
  const centreId = visit.centreId;
  const loaded = useQuery({ queryKey: ['visit', visit.id], queryFn: () => api.visitChart(visit.id!), enabled: !given });
  const chart = given ?? loaded.data;
  const [dialog, setDialog] = useState<'bill' | 'free' | undefined>();
  const [collect, setCollect] = useState<string | undefined>();
  const bills = (chart?.bills ?? []).filter((b) => b.status !== 'CANCELLED');
  const due = bills.filter((b) => (b.balance ?? 0) > 0 && b.discountStatus !== 'PENDING');
  const pending = bills.filter((b) => b.discountStatus === 'PENDING');
  const settled = bills.length > 0 && due.length === 0 && pending.length === 0;
  const checkout = useAction(
    () => api.checkoutVisit(visit.id!, { expectedVersion: chart?.visit?.version ?? visit.version ?? 0 }),
    { invalidate: [['visits'], ['visit'], ['clinic-board']], success: `${visit.patientName} checked out` },
  );
  const stop = (e: MouseEvent) => e.stopPropagation();
  if (!chart) {
    return null;
  }
  return (
    <Stack direction="row" spacing={0.5} sx={{ justifyContent: 'flex-end', flexWrap: 'wrap', gap: 0.5 }} onClick={stop}>
      {bills.length === 0 && session.hasAtCentre('billing.charge', centreId) && (
        <Button size="small" variant="contained" onClick={() => setDialog('bill')}>Raise bill</Button>
      )}
      {pending.map((b) => <Chip key={b.id} size="small" color="warning" label={`${b.billNo}: discount awaiting approval`} onClick={() => navigate(`/bills/${b.id}`)} />)}
      {session.hasAtCentre('billing.collect', centreId) && due.map((b) => (
        <Button key={b.id} size="small" variant="contained" color="warning" onClick={() => setCollect(b.id)}>Collect {money(b.balance)}</Button>
      ))}
      {bills.length > 0 && <Button size="small" onClick={() => navigate(`/bills/${bills[0]?.id}`)}>View bill</Button>}
      {visit.stage === 'CHECKOUT' && session.hasAtCentre('opd.checkin', centreId) && settled && (
        <Button size="small" variant="contained" color="success" onClick={() => checkout.mutate(undefined)} disabled={checkout.isPending}>Complete checkout</Button>
      )}
      {visit.stage === 'CHECKOUT' && session.hasAtCentre('opd.checkin', centreId) && bills.length === 0 && (
        <Button size="small" onClick={() => setDialog('free')}>No charge…</Button>
      )}
      {dialog === 'bill' && (
        <CreateBillDialog centreId={centreId!} patientId={visit.patientId!} patientName={visit.patientName} visitId={visit.id} visitType={visit.visitType}
          onClose={() => setDialog(undefined)} />
      )}
      {dialog === 'free' && (
        <ReasonDialog title={`Check out without a bill · ${visit.patientName}`} label="Why is this visit free of charge?" submitLabel="Complete checkout"
          onClose={() => setDialog(undefined)} success={`${visit.patientName} checked out`}
          run={(reason) => api.checkoutVisit(visit.id!, { noChargeReason: reason, expectedVersion: chart.visit?.version ?? 0 })} />
      )}
      {collect && <CollectForBill billId={collect} onClose={() => setCollect(undefined)} />}
    </Stack>
  );
}
