import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { SessionProvider, useSession } from './session';

function Probe() {
  const s = useSession();
  return (
    <ul>
      <li>a-read:{String(s.hasAtCentre('org.centre.read', 'A'))}</li>
      <li>b-read:{String(s.hasAtCentre('org.centre.read', 'B'))}</li>
      <li>audit-anywhere:{String(s.hasAnywhere('audit.read'))}</li>
      <li>centre:{s.centreId}</li>
    </ul>
  );
}

describe('session permissions', () => {
  it('is scoped per centre and never widened by another centre', () => {
    render(
      <SessionProvider me={{ tenantPermissions: [], centres: [{ centreId: 'A', name: 'Centre A', permissions: ['org.centre.read'] }] }}>
        <Probe />
      </SessionProvider>,
    );
    expect(screen.getByText('a-read:true')).toBeInTheDocument();
    expect(screen.getByText('b-read:false')).toBeInTheDocument();
    expect(screen.getByText('audit-anywhere:false')).toBeInTheDocument();
    expect(screen.getByText('centre:A')).toBeInTheDocument();
  });

  it('applies tenant-wide grants to every centre', () => {
    render(
      <SessionProvider me={{ tenantPermissions: ['org.centre.read', 'audit.read'], centres: [] }}>
        <Probe />
      </SessionProvider>,
    );
    expect(screen.getByText('b-read:true')).toBeInTheDocument();
    expect(screen.getByText('audit-anywhere:true')).toBeInTheDocument();
  });
});
