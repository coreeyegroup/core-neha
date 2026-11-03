import { Alert, AlertTitle } from '@mui/material';

import { ApiError } from '../api/http';

/** Shows a server refusal with its stable code and request id, so support can trace it. */
export function ProblemAlert({ error }: { error: unknown }) {
  if (!error) {
    return null;
  }
  const e = error instanceof ApiError ? error : undefined;
  const title =
    e?.status === 403 ? 'Not permitted' : e?.status === 422 ? 'Not allowed by a rule' : e?.status === 409 ? 'Conflict' : 'Could not complete';
  return (
    <Alert severity={e?.status === 403 || e?.status === 422 ? 'warning' : 'error'} sx={{ my: 1 }}>
      <AlertTitle>{title}</AlertTitle>
      {e?.message ?? String(error)}
      {e && (
        <div style={{ opacity: 0.75, marginTop: 4, fontSize: 12 }}>
          {e.code}
          {e.requestId ? ` · request ${e.requestId}` : ''}
        </div>
      )}
    </Alert>
  );
}
