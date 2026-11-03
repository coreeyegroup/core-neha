export interface NavItem {
  to: string;
  label: string;
  /** Shown only if the permission is held at some scope; the server still checks every action. */
  permission?: string;
  anyOf?: string[];
  group: 'Workspace' | 'Organisation' | 'People' | 'Access';
}

export const NAV_ITEMS: NavItem[] = [
  { to: '/', label: 'Overview', group: 'Workspace' },
  { to: '/centres', label: 'Centres', permission: 'org.centre.read', group: 'Organisation' },
  { to: '/organisation', label: 'Hierarchy and legal entities', permission: 'org.structure.read', group: 'Organisation' },
  { to: '/departments', label: 'Department library', permission: 'org.structure.read', group: 'Organisation' },
  { to: '/positions', label: 'Position library', permission: 'workforce.read', group: 'People' },
  { to: '/people', label: 'People and postings', permission: 'workforce.read', group: 'People' },
  { to: '/users', label: 'Users and access', permission: 'iam.assignment.read', group: 'Access' },
  { to: '/roles', label: 'Roles', permission: 'iam.role.read', group: 'Access' },
  { to: '/approvals', label: 'Approvals', anyOf: ['iam.assignment.approve', 'iam.role.approve'], group: 'Access' },
  { to: '/audit', label: 'Audit trail', permission: 'audit.read', group: 'Access' },
];

export function visibleItems(hasAnywhere: (p: string) => boolean): NavItem[] {
  return NAV_ITEMS.filter((i) => (i.permission ? hasAnywhere(i.permission) : i.anyOf ? i.anyOf.some(hasAnywhere) : true));
}
