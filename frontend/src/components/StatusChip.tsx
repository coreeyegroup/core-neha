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
  BOOKED: 'info',
  CHECKED_IN: 'success',
  NO_SHOW: 'default',
  CANCELLED: 'default',
  OPEN: 'warning',
  PAID: 'success',
  ADVISED: 'info',
  ACCEPTED: 'primary',
  DECLINED: 'default',
  SCHEDULED: 'info',
  READY: 'warning',
  COMPLETED: 'success',
  SUBMITTED: 'warning',
  APPROVED: 'success',
};

const LABELS: Record<string, string> = {
  PENDING_APPROVAL: 'Pending approval',
  CHECKED_IN: 'Arrived',
  NO_SHOW: 'No-show',
  READY: 'Ready for theatre',
  ACCEPTED: 'Accepted, to schedule',
};

/** One status dimension per chip, with text (never colour alone). */
export function StatusChip({ status }: { status?: string }) {
  if (!status) {
    return null;
  }
  const label = LABELS[status] ?? status.charAt(0) + status.slice(1).toLowerCase().replaceAll('_', ' ');
  return <Chip label={label} color={COLOURS[status] ?? 'default'} variant={COLOURS[status] ? 'filled' : 'outlined'} />;
}
