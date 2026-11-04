import { Box, CircularProgress } from '@mui/material';
import { createBrowserRouter, RouterProvider } from 'react-router';

import { SessionProvider, useMeQuery } from './auth/session';
import { ProblemAlert } from './components/ProblemAlert';
import { AppShell } from './layout/AppShell';
import { Approvals } from './pages/Approvals';
import { Audit } from './pages/Audit';
import { CentreWorkspace } from './pages/CentreWorkspace';
import { Centres } from './pages/Centres';
import { Departments } from './pages/Departments';
import { NotFound } from './pages/NotFound';
import { Organisation } from './pages/Organisation';
import { Overview } from './pages/Overview';
import { People } from './pages/People';
import { PersonDetail } from './pages/PersonDetail';
import { Positions } from './pages/Positions';
import { RoleDetail } from './pages/RoleDetail';
import { Roles } from './pages/Roles';
import { SignedOut, SignIn } from './pages/SignIn';
import { UserDetail } from './pages/UserDetail';
import { Users } from './pages/Users';
import { AccountsDesk } from './pages/clinic/AccountsDesk';
import { BillPage } from './pages/clinic/BillPage';
import { ConsultationDesk, OptometryDesk } from './pages/clinic/ClinicalDesks';
import { PatientRecord, Patients } from './pages/clinic/Patients';
import { ReceptionDesk } from './pages/clinic/ReceptionDesk';
import { CounsellingDesk, OtDesk, SurgeryPage } from './pages/clinic/Surgery';
import { VisitChartPage } from './pages/clinic/VisitChart';

const router = createBrowserRouter(
  [
    {
      path: '/',
      element: <AppShell />,
      children: [
        { index: true, element: <Overview /> },
        { path: 'reception', element: <ReceptionDesk /> },
        { path: 'optometry', element: <OptometryDesk /> },
        { path: 'consultation', element: <ConsultationDesk /> },
        { path: 'counselling', element: <CounsellingDesk /> },
        { path: 'ot', element: <OtDesk /> },
        { path: 'accounts', element: <AccountsDesk /> },
        { path: 'patients', element: <Patients /> },
        { path: 'patients/:patientId', element: <PatientRecord /> },
        { path: 'visits/:visitId', element: <VisitChartPage /> },
        { path: 'bills/:billId', element: <BillPage /> },
        { path: 'surgeries/:surgeryId', element: <SurgeryPage /> },
        { path: 'centres', element: <Centres /> },
        { path: 'centres/:centreId', element: <CentreWorkspace /> },
        { path: 'organisation', element: <Organisation /> },
        { path: 'departments', element: <Departments /> },
        { path: 'positions', element: <Positions /> },
        { path: 'people', element: <People /> },
        { path: 'people/:personId', element: <PersonDetail /> },
        { path: 'users', element: <Users /> },
        { path: 'users/:userId', element: <UserDetail /> },
        { path: 'roles', element: <Roles /> },
        { path: 'roles/:roleId', element: <RoleDetail /> },
        { path: 'approvals', element: <Approvals /> },
        { path: 'audit', element: <Audit /> },
        { path: '*', element: <NotFound /> },
      ],
    },
  ],
  { basename: __BASE_PATH__ },
);

export function App() {
  const isSignedOutPage = window.location.pathname.replace(/\/$/, '').endsWith('/signed-out');
  const me = useMeQuery();
  if (isSignedOutPage) {
    return <SignedOut />;
  }
  if (me.isLoading) {
    return (
      <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center' }} role="status" aria-label="Loading">
        <CircularProgress />
      </Box>
    );
  }
  if (me.error) {
    if (me.error.status === 401) {
      return <SignIn reason={me.error.code === 'session.ended' ? 'Your session ended or your account has no access. Sign in again.' : undefined} />;
    }
    return <Box sx={{ p: 3 }}><ProblemAlert error={me.error} /></Box>;
  }
  return (
    <SessionProvider me={me.data!}>
      <RouterProvider router={router} />
    </SessionProvider>
  );
}
