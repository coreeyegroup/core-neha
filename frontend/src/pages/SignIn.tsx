import { Alert, Box, Button, Paper, Stack, Typography } from '@mui/material';

import { signIn } from '../auth/signin';

export function SignIn({ reason }: { reason?: string }) {
  return (
    <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', p: 2, bgcolor: 'background.default' }}>
      <Paper sx={{ p: 4, maxWidth: 440, width: '100%' }}>
        <Stack spacing={2}>
          <Typography variant="h1">core-neha</Typography>
          <Typography variant="body1" color="text.secondary">
            Staff sign-in. You will be asked for your password and the code from your authenticator app.
          </Typography>
          {reason && <Alert severity="info">{reason}</Alert>}
          <Button variant="contained" size="large" onClick={signIn} autoFocus>
            Sign in
          </Button>
          <Typography variant="caption" color="text.secondary">
            Demonstration environment: synthetic data only.
          </Typography>
        </Stack>
      </Paper>
    </Box>
  );
}

export function SignedOut() {
  const params = new URLSearchParams(window.location.search);
  return <SignIn reason={params.get('reason') === 'login-failed' ? 'Sign-in did not complete. Please try again.' : 'You have signed out.'} />;
}
