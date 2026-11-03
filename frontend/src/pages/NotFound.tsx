import { Typography } from '@mui/material';
import { Link as RouterLink } from 'react-router';

export function NotFound() {
  return (
    <>
      <Typography variant="h1" gutterBottom>Page not found</Typography>
      <RouterLink to="/">Back to overview</RouterLink>
    </>
  );
}
