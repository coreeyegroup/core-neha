import { Alert, Chip, Paper, Stack, Table, TableBody, TableCell, TableRow, Typography } from '@mui/material';
import { useQuery } from '@tanstack/react-query';

import * as api from '../api/generated/core-neha';
import { PageHeader } from '../components/PageHeader';
import { ProblemAlert } from '../components/ProblemAlert';

const MODULES: [string, string, string][] = [
  ['EYE_CARE', 'Eye care', 'Optometry, consultation, counselling, surgery'],
  ['BILLING', 'Billing and accounts', 'Bills, payments, discounts, day close, price list'],
  ['INVENTORY', 'Inventory', 'Stores, items, batches, stock, purchase receipts, issues'],
  ['PHARMACY', 'Pharmacy', 'Prescriptions, dispensing, sales, returns'],
  ['OPTICAL', 'Optical shop', 'Spectacle orders, frames and lenses, delivery'],
  ['OPTICAL_LAB', 'Optical manufacturing', 'Lens surfacing, edging, fitting and quality check'],
  ['LABORATORY', 'Laboratory', 'Test orders, samples, results, validation, reports'],
  ['RADIOLOGY', 'Radiology and imaging', 'Imaging orders, studies, image viewer, reports'],
];

/** The organisation's licence from the software provider: read only here; the provider changes it. */
export function Licence() {
  const q = useQuery({ queryKey: ['licence'], queryFn: () => api.licence() });
  const l = q.data;
  const on = new Set(l?.modules ?? []);
  return (
    <>
      <PageHeader title="Licence" subtitle={l ? <>{l.tenantName} · licensed modules, validity and centres</> : 'Licensed modules, validity and centres'} />
      <ProblemAlert error={q.error} />
      {l && (
        <Stack spacing={2}>
          {!l.current && <Alert severity="error">This licence is not current ({l.status === 'SUSPENDED' ? 'suspended' : 'outside its validity'}). Contact the software provider.</Alert>}
          <Paper variant="outlined">
            <Table size="small">
              <TableBody>
                <TableRow><TableCell sx={{ width: 220 }}>Licence number</TableCell><TableCell>{l.licenceNo}</TableCell></TableRow>
                <TableRow><TableCell>Licensee</TableCell><TableCell>{l.licenseeName}</TableCell></TableRow>
                <TableRow><TableCell>Plan</TableCell><TableCell>{l.plan}</TableCell></TableRow>
                <TableRow><TableCell>Valid</TableCell><TableCell>{l.validFrom} to {l.validTo ?? 'no end date'}</TableCell></TableRow>
                <TableRow><TableCell>Centres</TableCell><TableCell>{l.centres} in use{l.maxCentres ? ` of ${l.maxCentres} licensed` : ', no limit'}</TableCell></TableRow>
                <TableRow><TableCell>Status</TableCell><TableCell><Chip size="small" label={l.current ? 'Active' : l.status === 'SUSPENDED' ? 'Suspended' : 'Expired'} color={l.current ? 'success' : 'error'} /></TableCell></TableRow>
              </TableBody>
            </Table>
          </Paper>
          <Typography variant="h3">Modules</Typography>
          <Paper variant="outlined">
            <Table size="small">
              <TableBody>
                {MODULES.map(([code, name, hint]) => (
                  <TableRow key={code}>
                    <TableCell sx={{ width: 220 }}>{name}</TableCell>
                    <TableCell>{hint}</TableCell>
                    <TableCell align="right"><Chip size="small" variant={on.has(code) ? 'filled' : 'outlined'} color={on.has(code) ? 'success' : 'default'} label={on.has(code) ? 'Licensed' : 'Not licensed'} /></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Paper>
          <Typography variant="body2" color="text.secondary">
            Organisation, people, access, audit, patients and the outpatient queue are always included. A module that is not licensed is
            switched off for everyone in the organisation, whatever their roles. Your data is kept separate from every other organisation using this software.
          </Typography>
        </Stack>
      )}
    </>
  );
}
