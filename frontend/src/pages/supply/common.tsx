import { Autocomplete, Box, Chip, MenuItem, TextField, Typography } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { useEffect } from 'react';

import * as api from '../../api/generated/core-neha';

export const CATEGORY_LABEL: Record<string, string> = {
  DRUG: 'Medicine',
  CONSUMABLE: 'Consumable',
  SURGICAL: 'Surgical',
  FRAME: 'Frame',
  SPECTACLE_LENS: 'Spectacle lens',
  LENS_BLANK: 'Lens blank',
  CONTACT_LENS: 'Contact lens',
  OPTICAL_ACCESSORY: 'Optical accessory',
  LAB_REAGENT: 'Lab reagent',
};

export const STORE_TYPE_LABEL: Record<string, string> = {
  PHARMACY: 'Pharmacy',
  OPTICAL: 'Optical shop',
  OPTICAL_LAB: 'Optical lab',
  OT: 'Operation theatre',
  CENTRAL: 'Main store',
};

export function useStores(centreId: string | undefined, enabled = true) {
  return useQuery({ queryKey: ['stores', centreId], queryFn: () => api.stores(centreId!), enabled: !!centreId && enabled });
}

/** Picks a store at the centre; with {@code type}, only stores of that type, and selects the first one automatically. */
export function StoreSelect({ centreId, type, value, onChange, label = 'Store' }: {
  centreId: string | undefined; type?: string; value: string; onChange: (id: string) => void; label?: string;
}) {
  const stores = useStores(centreId);
  const list = (stores.data ?? []).filter((s) => s.status === 'ACTIVE' && (!type || s.storeType === type));
  const first = list[0]?.id;
  useEffect(() => {
    if (!value && first) {
      onChange(first);
    }
  }, [value, first, onChange]);
  return (
    <TextField select label={label} value={list.some((s) => s.id === value) ? value : ''} onChange={(e) => onChange(e.target.value)} sx={{ minWidth: 220 }}>
      {list.map((s) => (
        <MenuItem key={s.id} value={s.id}>{s.name} · {STORE_TYPE_LABEL[s.storeType ?? ''] ?? s.storeType}</MenuItem>
      ))}
    </TextField>
  );
}

export function useItems(category?: string, q?: string) {
  return useQuery({ queryKey: ['items', category ?? '', q ?? ''], queryFn: () => api.items({ category, q }), staleTime: 30_000 });
}

export function ItemPicker({ categories, value, onChange, label = 'Item', autoFocus }: {
  categories?: string[]; value?: api.ItemView; onChange: (i: api.ItemView | undefined) => void; label?: string; autoFocus?: boolean;
}) {
  const items = useItems();
  const options = (items.data ?? []).filter((i) => !categories || categories.includes(i.category ?? ''));
  return (
    <Autocomplete<api.ItemView>
      options={options}
      value={value ?? null}
      onChange={(_, v) => onChange(v ?? undefined)}
      getOptionLabel={(i) => `${i.name} (${i.code})`}
      isOptionEqualToValue={(a, b) => a.id === b.id}
      groupBy={(i) => CATEGORY_LABEL[i.category ?? ''] ?? i.category ?? ''}
      renderOption={(props, i) => (
        <li {...props} key={i.id}>
          <Box>
            <Typography variant="body2" sx={{ fontWeight: 600 }}>{i.name}</Typography>
            <Typography variant="caption" color="text.secondary">
              {i.code}{i.genericName ? ` · ${i.genericName}` : ''}{i.schedule ? ` · Schedule ${i.schedule}` : ''}{i.mrp != null ? ` · MRP ₹${i.mrp}` : ''}
            </Typography>
          </Box>
        </li>
      )}
      renderInput={(params) => <TextField {...params} label={label} autoFocus={autoFocus} required />}
    />
  );
}

export function ScheduleChip({ schedule }: { schedule?: string }) {
  if (!schedule) {
    return null;
  }
  const rx = ['H', 'H1', 'X'].includes(schedule);
  return <Chip size="small" variant="outlined" color={rx ? 'warning' : 'default'} label={rx ? `Rx · Sch ${schedule}` : schedule} />;
}

export function expiryTone(date?: string): 'error' | 'warning' | undefined {
  if (!date) {
    return undefined;
  }
  const days = (new Date(date).getTime() - Date.now()) / 86_400_000;
  return days <= 0 ? 'error' : days <= 90 ? 'warning' : undefined;
}

export function power(v?: number | null): string {
  if (v == null) {
    return '–';
  }
  return `${v > 0 ? '+' : ''}${v.toFixed(2)}`;
}

/** Text input for a dioptre value: accepts +1.25, -0.5, 2. */
export function parseDioptre(s: string): number | undefined {
  const t = s.trim();
  if (!t) {
    return undefined;
  }
  const n = Number(t);
  return Number.isFinite(n) ? n : undefined;
}
