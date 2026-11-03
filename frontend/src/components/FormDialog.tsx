import { Button, Dialog, DialogActions, DialogContent, DialogTitle, Stack } from '@mui/material';
import type { FormEvent, ReactNode } from 'react';

import { ProblemAlert } from './ProblemAlert';

interface Props {
  open: boolean;
  title: string;
  submitLabel: string;
  onClose: () => void;
  onSubmit: () => void;
  busy?: boolean;
  error?: unknown;
  children: ReactNode;
  intro?: ReactNode;
  maxWidth?: 'xs' | 'sm' | 'md' | 'lg';
}

/** Dialog for one business action. Submit with Enter; Escape cancels. */
export function FormDialog({ open, title, submitLabel, onClose, onSubmit, busy, error, children, intro, maxWidth = 'sm' }: Props) {
  const submit = (e: FormEvent) => {
    e.preventDefault();
    onSubmit();
  };
  return (
    <Dialog open={open} onClose={busy ? undefined : onClose} maxWidth={maxWidth} aria-labelledby="form-dialog-title">
      <form onSubmit={submit} noValidate>
        <DialogTitle id="form-dialog-title">{title}</DialogTitle>
        <DialogContent dividers>
          {intro}
          <Stack spacing={2} sx={{ mt: intro ? 2 : 0 }}>
            {children}
          </Stack>
          <ProblemAlert error={error} />
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose} disabled={busy}>
            Cancel
          </Button>
          <Button type="submit" variant="contained" disabled={busy}>
            {busy ? 'Saving…' : submitLabel}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}
