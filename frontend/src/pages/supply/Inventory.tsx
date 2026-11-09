import { Box, Button, Chip, IconButton, MenuItem, Stack, Tab, Tabs, TextField, Typography } from '@mui/material';
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
import { clock, dateLabel, money, MyPositions, NeedPermission, StatCard, StatRow, todayIst, useWorkingCentre } from '../clinic/common';
import { CATEGORY_LABEL, expiryTone, ItemPicker, STORE_TYPE_LABEL, StoreSelect, useStores } from './common';

/** Stores, stock by batch and expiry, goods receipts, transfers, adjustments and the stock ledger, at the working centre. */
export function Inventory() {
  const session = useSession();
  const { centreId, centreName } = useWorkingCentre();
  const at = (p: string) => session.hasAtCentre(p, centreId);
  const ok = at('inventory.read');
  const [storeId, setStoreId] = useState('');
  const [tab, setTab] = useState('stock');
  const [dialog, setDialog] = useState<'receive' | 'transfer' | 'adjust' | 'store' | 'item' | undefined>();
  const [editItem, setEditItem] = useState<api.ItemView | undefined>();
  const stores = useStores(centreId, ok);
  const store = (stores.data ?? []).find((s) => s.id === storeId);
  const stock = useQuery({ queryKey: ['stock', storeId], queryFn: () => api.stock(storeId), enabled: ok && !!storeId });
  const batches = useQuery({ queryKey: ['batches', storeId], queryFn: () => api.batches(storeId, { inStock: true }), enabled: ok && !!storeId && tab === 'batches' });
  const moves = useQuery({ queryKey: ['movements', storeId], queryFn: () => api.movements(storeId), enabled: ok && !!storeId && tab === 'ledger' });
  const expiring = useQuery({ queryKey: ['expiring', centreId], queryFn: () => api.expiring(centreId!, { days: 90 }), enabled: ok && !!centreId });
  const receipts = useQuery({ queryKey: ['receipts', centreId], queryFn: () => api.receipts(centreId!), enabled: ok && !!centreId && tab === 'receipts' });
  const transfers = useQuery({ queryKey: ['transfers', centreId], queryFn: () => api.transfers(centreId!), enabled: ok && !!centreId && tab === 'transfers' });
  const adjustments = useQuery({ queryKey: ['adjustments', centreId], queryFn: () => api.adjustments(centreId!), enabled: ok && !!centreId });
  const itemMaster = session.hasTenantWide('inventory.item.manage');
  const allItems = useQuery({ queryKey: ['items', 'master'], queryFn: () => api.items({ includeRetired: true }), enabled: itemMaster && tab === 'items' });
  const lines = stock.data ?? [];
  const pending = (adjustments.data ?? []).filter((a) => a.status === 'PENDING');
  const decide = useAction((v: { a: api.AdjustmentView; approve: boolean }) =>
    api.decideAdjustment(v.a.id!, { approve: v.approve, note: v.approve ? 'Checked and approved' : 'Rejected', expectedVersion: v.a.version ?? 0 }),
  { invalidate: [['adjustments'], ['stock'], ['batches'], ['movements']], success: 'Adjustment decided' });

  return (
    <>
      <PageHeader title="Inventory" subtitle={<>{centreName} · stores, stock by batch and expiry, receipts, transfers and adjustments</>}
        actions={
          <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', gap: 1 }}>
            {at('inventory.receive') && <Button variant="contained" onClick={() => setDialog('receive')} disabled={!storeId}>Receive goods</Button>}
            {at('inventory.transfer') && <Button onClick={() => setDialog('transfer')} disabled={!storeId}>Send to another store</Button>}
            {at('inventory.adjust.request') && <Button onClick={() => setDialog('adjust')} disabled={!storeId}>Request adjustment</Button>}
            {at('inventory.store.manage') && <Button onClick={() => setDialog('store')}>New store</Button>}
          </Stack>
        } />
      <MyPositions />
      <Box sx={{ mt: 2 }} />
      <NeedPermission ok={ok || itemMaster} what="inventory access">
        {ok && (
          <>
            <Stack direction="row" spacing={2} sx={{ mb: 2, alignItems: 'center', flexWrap: 'wrap', gap: 1 }}>
              <StoreSelect centreId={centreId} value={storeId} onChange={setStoreId} />
              {store && <Chip label={STORE_TYPE_LABEL[store.storeType ?? ''] ?? store.storeType} />}
            </Stack>
            <StatRow>
              <StatCard label="Items in stock" value={lines.filter((l) => (l.quantity ?? 0) > 0).length} hint={`${lines.length} items stocked here`} />
              <StatCard label="At or below reorder level" value={lines.filter((l) => l.belowReorder).length} tone="warning" />
              <StatCard label="Stock value (cost)" value={money(lines.reduce((n, l) => n + (l.stockValue ?? 0), 0))} />
              <StatCard label="Expiring in 90 days" value={(expiring.data ?? []).length} tone={(expiring.data ?? []).length ? 'warning' : undefined} hint="all stores at this centre" />
              <StatCard label="Adjustments waiting" value={pending.length} tone={pending.length ? 'warning' : undefined} />
            </StatRow>
          </>
        )}
        <Tabs value={tab} onChange={(_, v: string) => setTab(v)} sx={{ mb: 2, borderBottom: 1, borderColor: 'divider' }} variant="scrollable">
          {ok && <Tab value="stock" label="Stock" />}
          {ok && <Tab value="batches" label="Batches" />}
          {ok && <Tab value="expiry" label="Expiry" />}
          {ok && <Tab value="receipts" label="Goods receipts" />}
          {ok && <Tab value="transfers" label="Transfers" />}
          {ok && <Tab value="adjustments" label={`Adjustments (${pending.length})`} />}
          {ok && <Tab value="ledger" label="Stock ledger" />}
          {itemMaster && <Tab value="items" label="Item master" />}
        </Tabs>
        {tab === 'stock' && ok && (
          <DataTable rows={lines} rowKey={(r) => r.itemId ?? ''} loading={stock.isLoading} empty="Nothing stocked in this store yet."
            columns={[
              { key: 'name', header: 'Item', render: (r) => <>{r.itemName} <Typography component="span" variant="caption" color="text.secondary">{r.itemCode}</Typography></>, value: (r) => `${r.itemName} ${r.itemCode}` },
              { key: 'cat', header: 'Type', render: (r) => CATEGORY_LABEL[r.category ?? ''] ?? r.category, value: (r) => r.category },
              { key: 'qty', header: 'On hand', align: 'right', render: (r) => <>{r.quantity} {r.unit}</>, value: (r) => r.quantity },
              { key: 'reorder', header: 'Reorder at', align: 'right', render: (r) => r.belowReorder ? <Chip size="small" color="warning" label={`≤ ${r.reorderLevel}`} /> : r.reorderLevel, value: (r) => r.reorderLevel },
              { key: 'batches', header: 'Batches', align: 'right', render: (r) => r.batches, value: (r) => r.batches },
              { key: 'exp', header: 'Nearest expiry', render: (r) => r.nearestExpiry ? <Chip size="small" variant="outlined" color={expiryTone(r.nearestExpiry) ?? 'default'} label={dateLabel(r.nearestExpiry)} /> : '–', value: (r) => r.nearestExpiry },
              { key: 'value', header: 'Value', align: 'right', render: (r) => money(r.stockValue), value: (r) => r.stockValue },
            ]} />
        )}
        {tab === 'batches' && ok && <BatchTable rows={batches.data} loading={batches.isLoading} />}
        {tab === 'expiry' && ok && <BatchTable rows={expiring.data} loading={expiring.isLoading} empty="Nothing expires in the next 90 days." />}
        {tab === 'receipts' && ok && (
          <DataTable rows={receipts.data} rowKey={(r) => r.id ?? ''} loading={receipts.isLoading} empty="No goods received yet."
            columns={[
              { key: 'no', header: 'Receipt', render: (r) => r.receiptNo, value: (r) => r.receiptNo },
              { key: 'store', header: 'Store', render: (r) => r.storeName, value: (r) => r.storeName },
              { key: 'sup', header: 'Supplier and invoice', render: (r) => <>{r.supplierName} <Typography component="span" variant="caption" color="text.secondary">{r.supplierInvoiceNo} · {dateLabel(r.invoiceDate)}</Typography></>, value: (r) => `${r.supplierName} ${r.supplierInvoiceNo}` },
              { key: 'lines', header: 'Lines', align: 'right', render: (r) => r.lines },
              { key: 'cost', header: 'Cost', align: 'right', render: (r) => money(r.totalCost), value: (r) => r.totalCost },
              { key: 'by', header: 'Received by', render: (r) => <>{r.receivedByName} <Typography component="span" variant="caption" color="text.secondary">{clock(r.receivedAt)}</Typography></> },
            ]} />
        )}
        {tab === 'transfers' && ok && (
          <DataTable rows={transfers.data} rowKey={(r) => r.id ?? ''} loading={transfers.isLoading} empty="No transfers."
            columns={[
              { key: 'no', header: 'Transfer', render: (r) => r.transferNo, value: (r) => r.transferNo },
              { key: 'from', header: 'From', render: (r) => r.fromStoreName },
              { key: 'to', header: 'To', render: (r) => r.toStoreName },
              { key: 'note', header: 'Note', render: (r) => r.note ?? '' },
              { key: 'by', header: 'Sent by', render: (r) => <>{r.sentByName} <Typography component="span" variant="caption" color="text.secondary">{clock(r.sentAt)}</Typography></> },
            ]} />
        )}
        {tab === 'adjustments' && ok && (
          <DataTable rows={adjustments.data} rowKey={(r) => r.id ?? ''} loading={adjustments.isLoading} empty="No adjustments."
            columns={[
              { key: 'item', header: 'Item and batch', render: (r) => <>{r.itemName} <Typography component="span" variant="caption" color="text.secondary">{r.batchNo} · {r.storeName}</Typography></>, value: (r) => `${r.itemName} ${r.batchNo}` },
              { key: 'qty', header: 'Change', align: 'right', render: (r) => (r.quantity ?? 0) > 0 ? `+${r.quantity}` : r.quantity },
              { key: 'why', header: 'Reason', render: (r) => <>{r.reason?.toLowerCase().replace('_', ' ')}: {r.note}</> },
              { key: 'req', header: 'Requested by', render: (r) => r.requestedByName },
              { key: 'status', header: 'Status', render: (r) => <StatusChip status={r.status} /> },
              { key: 'act', header: '', align: 'right', render: (r) => r.status === 'PENDING' && at('inventory.adjust.approve') ? (
                <Stack direction="row" spacing={0.5} sx={{ justifyContent: 'flex-end' }}>
                  <Button size="small" variant="contained" onClick={() => decide.mutate({ a: r, approve: true })}>Approve</Button>
                  <Button size="small" onClick={() => decide.mutate({ a: r, approve: false })}>Reject</Button>
                </Stack>
              ) : r.decidedByName ? <Typography variant="caption">{r.decidedByName}</Typography> : null },
            ]} />
        )}
        <ProblemAlert error={decide.error} />
        {tab === 'ledger' && ok && (
          <DataTable rows={moves.data} rowKey={(r) => r.id ?? ''} loading={moves.isLoading} empty="No movements in the last 30 days."
            columns={[
              { key: 'at', header: 'When', render: (r) => clock(r.movedAt), value: (r) => r.movedAt },
              { key: 'item', header: 'Item and batch', render: (r) => <>{r.itemName} <Typography component="span" variant="caption" color="text.secondary">{r.batchNo}</Typography></>, value: (r) => `${r.itemName} ${r.batchNo}` },
              { key: 'type', header: 'Movement', render: (r) => r.movementType?.toLowerCase().replace('_', ' '), value: (r) => r.movementType },
              { key: 'qty', header: 'Quantity', align: 'right', render: (r) => (r.quantity ?? 0) > 0 ? `+${r.quantity}` : r.quantity },
              { key: 'bal', header: 'Balance', align: 'right', render: (r) => r.balanceAfter },
              { key: 'ref', header: 'Document', render: (r) => r.refNo ?? r.reason ?? '', value: (r) => r.refNo },
              { key: 'by', header: 'By', render: (r) => r.movedByName },
            ]} />
        )}
        {tab === 'items' && itemMaster && (
          <>
            <Button variant="contained" sx={{ mb: 1 }} onClick={() => { setEditItem(undefined); setDialog('item'); }}>New item</Button>
            <DataTable rows={allItems.data} rowKey={(r) => r.id ?? ''} loading={allItems.isLoading} onRowClick={(r) => { setEditItem(r); setDialog('item'); }}
              columns={[
                { key: 'code', header: 'Code', render: (r) => r.code, value: (r) => r.code },
                { key: 'name', header: 'Name', render: (r) => r.name, value: (r) => `${r.name} ${r.genericName ?? ''}` },
                { key: 'cat', header: 'Type', render: (r) => CATEGORY_LABEL[r.category ?? ''] ?? r.category, value: (r) => r.category },
                { key: 'sch', header: 'Schedule', render: (r) => r.schedule ?? '' },
                { key: 'mrp', header: 'MRP', align: 'right', render: (r) => money(r.mrp), value: (r) => r.mrp },
                { key: 'gst', header: 'GST %', align: 'right', render: (r) => r.gstRate },
                { key: 'status', header: 'Status', render: (r) => <StatusChip status={r.status} /> },
              ]} />
          </>
        )}
      </NeedPermission>
      {dialog === 'receive' && storeId && <ReceiveDialog storeId={storeId} onClose={() => setDialog(undefined)} />}
      {dialog === 'transfer' && storeId && centreId && <TransferDialog centreId={centreId} fromStoreId={storeId} onClose={() => setDialog(undefined)} />}
      {dialog === 'adjust' && storeId && <AdjustDialog storeId={storeId} onClose={() => setDialog(undefined)} />}
      {dialog === 'store' && centreId && <StoreDialog centreId={centreId} onClose={() => setDialog(undefined)} />}
      {dialog === 'item' && <ItemDialog item={editItem} onClose={() => setDialog(undefined)} />}
    </>
  );
}

function BatchTable({ rows, loading, empty = 'No stock.' }: { rows?: api.BatchView[]; loading?: boolean; empty?: string }) {
  return (
    <DataTable rows={rows} rowKey={(r) => r.id ?? ''} loading={loading} empty={empty}
      columns={[
        { key: 'item', header: 'Item', render: (r) => <>{r.itemName} <Typography component="span" variant="caption" color="text.secondary">{r.itemCode}</Typography></>, value: (r) => `${r.itemName} ${r.itemCode}` },
        { key: 'batch', header: 'Batch', render: (r) => r.batchNo, value: (r) => r.batchNo },
        { key: 'exp', header: 'Expiry', render: (r) => r.expiryDate ? <Chip size="small" variant="outlined" color={expiryTone(r.expiryDate) ?? 'default'} label={dateLabel(r.expiryDate)} /> : '–', value: (r) => r.expiryDate },
        { key: 'qty', header: 'On hand', align: 'right', render: (r) => r.qtyOnHand, value: (r) => r.qtyOnHand },
        { key: 'mrp', header: 'MRP', align: 'right', render: (r) => money(r.mrp), value: (r) => r.mrp },
        { key: 'cost', header: 'Cost', align: 'right', render: (r) => money(r.unitCost), value: (r) => r.unitCost },
      ]} />
  );
}

type ReceiveLine = { key: number; item?: api.ItemView; batchNo: string; expiryDate: string; quantity: string; unitCost: string; mrp: string };

function ReceiveDialog({ storeId, onClose }: { storeId: string; onClose: () => void }) {
  const [supplier, setSupplier] = useState('');
  const [invoice, setInvoice] = useState('');
  const [invoiceDate, setInvoiceDate] = useState(todayIst());
  const [lines, setLines] = useState<ReceiveLine[]>([{ key: 1, batchNo: '', expiryDate: '', quantity: '', unitCost: '', mrp: '' }]);
  const set = (k: number, patch: Partial<ReceiveLine>) => setLines((ls) => ls.map((l) => (l.key === k ? { ...l, ...patch } : l)));
  const save = useAction(() => api.receive({
    storeId, supplierName: supplier, supplierInvoiceNo: invoice, invoiceDate,
    lines: lines.filter((l) => l.item).map((l) => ({ itemId: l.item!.id!, batchNo: l.batchNo, expiryDate: l.expiryDate || undefined, quantity: Number(l.quantity), unitCost: Number(l.unitCost), mrp: Number(l.mrp) })),
  }), { invalidate: [['stock'], ['batches'], ['receipts'], ['expiring'], ['movements']], success: (r) => `Goods receipt ${r.receiptNo} posted`, onDone: onClose });
  return (
    <FormDialog open title="Receive goods from a supplier" submitLabel="Post goods receipt" onClose={onClose} onSubmit={() => save.mutate(undefined)}
      busy={save.isPending} error={save.error} maxWidth="lg"
      intro={<Typography variant="body2" color="text.secondary">Enter what arrived, batch by batch, from the supplier&apos;s invoice. Medicines need the expiry date; expired goods are refused.</Typography>}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
        <TextField label="Supplier" value={supplier} onChange={(e) => setSupplier(e.target.value)} required sx={{ flex: 2 }} />
        <TextField label="Supplier invoice no." value={invoice} onChange={(e) => setInvoice(e.target.value)} required sx={{ flex: 1 }} />
        <TextField label="Invoice date" type="date" value={invoiceDate} onChange={(e) => setInvoiceDate(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
      </Stack>
      {lines.map((l) => (
        <Stack key={l.key} direction={{ xs: 'column', md: 'row' }} spacing={1} sx={{ alignItems: { md: 'center' } }}>
          <Box sx={{ flex: 3, minWidth: 240 }}><ItemPicker value={l.item} onChange={(i) => set(l.key, { item: i, mrp: i?.mrp != null ? String(i.mrp) : l.mrp })} /></Box>
          <TextField label="Batch" value={l.batchNo} onChange={(e) => set(l.key, { batchNo: e.target.value })} required sx={{ flex: 1 }} />
          <TextField label="Expiry" type="date" value={l.expiryDate} onChange={(e) => set(l.key, { expiryDate: e.target.value })} slotProps={{ inputLabel: { shrink: true } }} sx={{ flex: 1 }} />
          <TextField label="Quantity" value={l.quantity} onChange={(e) => set(l.key, { quantity: e.target.value })} required slotProps={{ htmlInput: { inputMode: 'numeric' } }} sx={{ width: 100 }} />
          <TextField label="Unit cost" value={l.unitCost} onChange={(e) => set(l.key, { unitCost: e.target.value })} required slotProps={{ htmlInput: { inputMode: 'decimal' } }} sx={{ width: 110 }} />
          <TextField label="MRP" value={l.mrp} onChange={(e) => set(l.key, { mrp: e.target.value })} required slotProps={{ htmlInput: { inputMode: 'decimal' } }} sx={{ width: 110 }} />
          <IconButton aria-label="Remove line" onClick={() => setLines((ls) => ls.filter((x) => x.key !== l.key))} disabled={lines.length === 1}><DeleteOutlined /></IconButton>
        </Stack>
      ))}
      <Box><Button onClick={() => setLines((ls) => [...ls, { key: Date.now(), batchNo: '', expiryDate: '', quantity: '', unitCost: '', mrp: '' }])}>Add line</Button></Box>
    </FormDialog>
  );
}

function BatchPicker({ storeId, value, onChange }: { storeId: string; value?: api.BatchView; onChange: (b: api.BatchView | undefined) => void }) {
  const batches = useQuery({ queryKey: ['batches', storeId], queryFn: () => api.batches(storeId, { inStock: true }) });
  return (
    <TextField select label="Item and batch" value={value?.id ?? ''} required
      onChange={(e) => onChange((batches.data ?? []).find((b) => b.id === e.target.value))}>
      {(batches.data ?? []).map((b) => (
        <MenuItem key={b.id} value={b.id}>{b.itemName} · batch {b.batchNo}{b.expiryDate ? ` · exp ${dateLabel(b.expiryDate)}` : ''} · {b.qtyOnHand} on hand</MenuItem>
      ))}
    </TextField>
  );
}

function TransferDialog({ centreId, fromStoreId, onClose }: { centreId: string; fromStoreId: string; onClose: () => void }) {
  const [to, setTo] = useState('');
  const [batch, setBatch] = useState<api.BatchView | undefined>();
  const [qty, setQty] = useState('');
  const [note, setNote] = useState('');
  const stores = useStores(centreId);
  const save = useAction(() => api.transfer({ fromStoreId, toStoreId: to, note: note || undefined, lines: [{ batchId: batch!.id!, quantity: Number(qty) }] }),
    { invalidate: [['stock'], ['batches'], ['transfers'], ['movements']], success: (r) => `Transfer ${r.transferNo} sent`, onDone: onClose });
  return (
    <FormDialog open title="Send stock to another store" submitLabel="Send" onClose={onClose} onSubmit={() => save.mutate(undefined)} busy={save.isPending} error={save.error}>
      <TextField select label="To store" value={to} onChange={(e) => setTo(e.target.value)} required>
        {(stores.data ?? []).filter((s) => s.id !== fromStoreId && s.status === 'ACTIVE').map((s) => <MenuItem key={s.id} value={s.id}>{s.name}</MenuItem>)}
      </TextField>
      <BatchPicker storeId={fromStoreId} value={batch} onChange={setBatch} />
      <TextField label="Quantity" value={qty} onChange={(e) => setQty(e.target.value)} required slotProps={{ htmlInput: { inputMode: 'numeric' } }} />
      <TextField label="Note" value={note} onChange={(e) => setNote(e.target.value)} />
    </FormDialog>
  );
}

function AdjustDialog({ storeId, onClose }: { storeId: string; onClose: () => void }) {
  const [batch, setBatch] = useState<api.BatchView | undefined>();
  const [qty, setQty] = useState('-1');
  const [reason, setReason] = useState('DAMAGED');
  const [note, setNote] = useState('');
  const save = useAction(() => api.requestAdjustment({ batchId: batch!.id!, quantity: Number(qty), reason, note }),
    { invalidate: [['adjustments']], success: 'Adjustment requested; another person must approve it', onDone: onClose });
  return (
    <FormDialog open title="Request a stock adjustment" submitLabel="Request" onClose={onClose} onSubmit={() => save.mutate(undefined)} busy={save.isPending} error={save.error}
      intro={<Typography variant="body2" color="text.secondary">Stock changes only when someone else (the stores manager) approves the request.</Typography>}>
      <BatchPicker storeId={storeId} value={batch} onChange={setBatch} />
      <TextField label="Change in quantity (negative to remove)" value={qty} onChange={(e) => setQty(e.target.value)} required />
      <TextField select label="Reason" value={reason} onChange={(e) => setReason(e.target.value)}>
        {['DAMAGED', 'EXPIRED', 'COUNT_CORRECTION', 'LOST', 'OTHER'].map((r) => <MenuItem key={r} value={r}>{r.toLowerCase().replace('_', ' ')}</MenuItem>)}
      </TextField>
      <TextField label="What happened" value={note} onChange={(e) => setNote(e.target.value)} required multiline minRows={2} />
    </FormDialog>
  );
}

function StoreDialog({ centreId, onClose }: { centreId: string; onClose: () => void }) {
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [type, setType] = useState('PHARMACY');
  const save = useAction(() => api.createStore(centreId, { code: code.toUpperCase(), name, storeType: type }),
    { invalidate: [['stores']], success: (s) => `${s.name} opened`, onDone: onClose });
  return (
    <FormDialog open title="Open a store at this centre" submitLabel="Open store" onClose={onClose} onSubmit={() => save.mutate(undefined)} busy={save.isPending} error={save.error}>
      <TextField label="Code" value={code} onChange={(e) => setCode(e.target.value)} required helperText="Capital letters and digits, for example PHARM2" />
      <TextField label="Name" value={name} onChange={(e) => setName(e.target.value)} required />
      <TextField select label="Type" value={type} onChange={(e) => setType(e.target.value)}>
        {Object.entries(STORE_TYPE_LABEL).map(([k, v]) => <MenuItem key={k} value={k}>{v}</MenuItem>)}
      </TextField>
    </FormDialog>
  );
}

function ItemDialog({ item, onClose }: { item?: api.ItemView; onClose: () => void }) {
  const [f, setF] = useState({
    code: item?.code ?? '', name: item?.name ?? '', category: item?.category ?? 'DRUG', unit: item?.unit ?? 'bottle', gstRate: String(item?.gstRate ?? 5),
    mrp: item?.mrp != null ? String(item.mrp) : '', genericName: item?.genericName ?? '', strength: item?.strength ?? '', dosageForm: item?.dosageForm ?? '',
    schedule: item?.schedule ?? 'H', manufacturer: item?.manufacturer ?? '', brand: item?.brand ?? '', lensType: item?.lensType ?? '',
    lensIndex: item?.lensIndex != null ? String(item.lensIndex) : '', coating: item?.coating ?? '', reorderLevel: String(item?.reorderLevel ?? 0),
  });
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) => setF({ ...f, [k]: e.target.value });
  const drug = f.category === 'DRUG';
  const lens = f.category === 'SPECTACLE_LENS' || f.category === 'LENS_BLANK';
  const body: api.SaveItem = {
    code: f.code.toUpperCase(), name: f.name, category: f.category, unit: f.unit, gstRate: Number(f.gstRate), mrp: f.mrp ? Number(f.mrp) : undefined,
    genericName: drug ? f.genericName || undefined : undefined, strength: drug ? f.strength || undefined : undefined, dosageForm: drug ? f.dosageForm || undefined : undefined,
    schedule: drug ? f.schedule : undefined, manufacturer: f.manufacturer || undefined, brand: f.brand || undefined,
    lensType: lens ? f.lensType || undefined : undefined, lensIndex: lens && f.lensIndex ? Number(f.lensIndex) : undefined, coating: lens ? f.coating || undefined : undefined,
    reorderLevel: Number(f.reorderLevel || 0), expectedVersion: item?.version,
  };
  const save = useAction(() => (item ? api.updateItem(item.id!, body) : api.createItem(body)),
    { invalidate: [['items']], success: (i) => `${i.name} saved`, onDone: onClose });
  return (
    <FormDialog open title={item ? `Edit ${item.name}` : 'New item'} submitLabel="Save" onClose={onClose} onSubmit={() => save.mutate(undefined)} busy={save.isPending} error={save.error} maxWidth="md">
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
        <TextField label="Code" value={f.code} onChange={set('code')} required disabled={!!item} />
        <TextField select label="Type" value={f.category} onChange={set('category')} disabled={!!item} sx={{ minWidth: 200 }}>
          {Object.entries(CATEGORY_LABEL).map(([k, v]) => <MenuItem key={k} value={k}>{v}</MenuItem>)}
        </TextField>
        <TextField label="Unit" value={f.unit} onChange={set('unit')} required />
      </Stack>
      <TextField label="Name" value={f.name} onChange={set('name')} required />
      {drug && (
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
          <TextField label="Generic name" value={f.genericName} onChange={set('genericName')} sx={{ flex: 2 }} />
          <TextField label="Form" value={f.dosageForm} onChange={set('dosageForm')} />
          <TextField select label="Schedule" value={f.schedule} onChange={set('schedule')} sx={{ minWidth: 120 }}>
            {['OTC', 'G', 'H', 'H1', 'X'].map((s) => <MenuItem key={s} value={s}>{s}</MenuItem>)}
          </TextField>
        </Stack>
      )}
      {lens && (
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
          <TextField select label="Lens type" value={f.lensType} onChange={set('lensType')} sx={{ minWidth: 180 }}>
            {['SINGLE_VISION', 'BIFOCAL', 'PROGRESSIVE'].map((s) => <MenuItem key={s} value={s}>{s.toLowerCase().replace('_', ' ')}</MenuItem>)}
          </TextField>
          <TextField label="Index" value={f.lensIndex} onChange={set('lensIndex')} />
          <TextField label="Coating" value={f.coating} onChange={set('coating')} />
        </Stack>
      )}
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
        <TextField label="MRP (₹, incl. GST)" value={f.mrp} onChange={set('mrp')} />
        <TextField select label="GST %" value={f.gstRate} onChange={set('gstRate')} sx={{ minWidth: 100 }}>
          {['0', '5', '12', '18', '28'].map((s) => <MenuItem key={s} value={s}>{s}</MenuItem>)}
        </TextField>
        <TextField label="Reorder level" value={f.reorderLevel} onChange={set('reorderLevel')} />
        <TextField label="Manufacturer / brand" value={f.manufacturer || f.brand} onChange={set('manufacturer')} />
      </Stack>
    </FormDialog>
  );
}
