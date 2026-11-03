import { Chip, type ChipProps } from '@mui/material';

const COLOURS: Record<string, ChipProps['color']> = {
  ACTIVE: 'success',
  PUBLISHED: 'success',
  SUCCESS: 'success',
  PENDING_APPROVAL: 'warning',
  DRAFT: 'info',
  DISABLED: 'default',
  RETIRED: 'default',
  SUPERSEDED: 'default',
  REVOKED: 'default',
  REJECTED: 'error',
  DENIED: 'error',
  FAILED: 'error',
};

const LABELS: Record<string, string> = {
  PENDING_APPROVAL: 'Pending approval',
};

/** One status dimension per chip, with text (never colour alone). */
export function StatusChip({ status }: { status?: string }) {
  if (!status) {
    return null;
  }
  const label = LABELS[status] ?? status.charAt(0) + status.slice(1).toLowerCase().replaceAll('_', ' ');
  return <Chip label={label} color={COLOURS[status] ?? 'default'} variant={COLOURS[status] ? 'filled' : 'outlined'} />;
}
