import { Alert, Snackbar } from '@mui/material';
import { createContext, useCallback, useContext, useState, type ReactNode } from 'react';

type Notify = (message: string, severity?: 'success' | 'info' | 'warning' | 'error') => void;
const NotifierContext = createContext<Notify>(() => undefined);

export function NotifierProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<{ message: string; severity: 'success' | 'info' | 'warning' | 'error' } | undefined>();
  const notify = useCallback<Notify>((message, severity = 'success') => setState({ message, severity }), []);
  return (
    <NotifierContext.Provider value={notify}>
      {children}
      <Snackbar open={!!state} autoHideDuration={5000} onClose={() => setState(undefined)} anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}>
        <Alert severity={state?.severity ?? 'success'} variant="filled" onClose={() => setState(undefined)}>
          {state?.message}
        </Alert>
      </Snackbar>
    </NotifierContext.Provider>
  );
}

export function useNotify(): Notify {
  return useContext(NotifierContext);
}
