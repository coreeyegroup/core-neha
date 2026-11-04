import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router';

import * as api from '../api/generated/core-neha';
import { DataTable } from '../components/DataTable';
import { PageHeader } from '../components/PageHeader';
import { StatusChip } from '../components/StatusChip';

export function Users() {
  const navigate = useNavigate();
  const list = useQuery({ queryKey: ['users'], queryFn: () => api.users() });
  return (
    <>
      <PageHeader title="Users and access" subtitle="User accounts you may see. Open one to review assignments, request access or explain a decision." />
      <DataTable
        rows={list.data}
        loading={list.isLoading}
        rowKey={(r) => r.id ?? ''}
        filterPlaceholder="Filter by username or name"
        onRowClick={(r) => navigate(`/users/${r.id}`)}
        columns={[
          { key: 'username', header: 'Username', render: (r) => r.username, value: (r) => r.username },
          { key: 'name', header: 'Person', render: (r) => r.personName, value: (r) => r.personName },
          { key: 'login', header: 'Login', render: (r) => (r.loginLinked ? 'Linked' : 'Not linked'), value: (r) => String(r.loginLinked) },
          { key: 'mfa', header: 'One-time code', render: (r) => (r.mfaRequired === false ? 'Off' : 'Required'), value: (r) => String(r.mfaRequired) },
          { key: 'status', header: 'Status', render: (r) => <StatusChip status={r.status} />, value: (r) => r.status },
        ]}
      />
    </>
  );
}
