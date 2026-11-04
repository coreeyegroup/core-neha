import { describe, expect, it } from 'vitest';

import { visibleItems } from './navigation';

describe('navigation', () => {
  it('shows only items whose permission is held somewhere', () => {
    const held = new Set(['org.centre.read']);
    const labels = visibleItems((p) => held.has(p)).map((i) => i.label);
    expect(labels).toEqual(['Overview', 'Centres', 'Help centre']);
  });

  it('shows approvals for either approval permission', () => {
    const labels = visibleItems((p) => p === 'iam.role.approve').map((i) => i.label);
    expect(labels).toContain('Approvals');
  });
});
