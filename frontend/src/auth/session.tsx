import { useQuery, type UseQueryResult } from '@tanstack/react-query';
import { createContext, useContext, useMemo, useState, type ReactNode } from 'react';

import { me as fetchMe, type Me } from '../api/generated/core-neha';
import { ApiError } from '../api/http';

export interface Session {
  me: Me;
  /** Permission held at tenant scope or at any centre. */
  hasAnywhere: (permission: string) => boolean;
  /** Permission held for the given centre (tenant-wide grants included). */
  hasAtCentre: (permission: string, centreId: string | undefined) => boolean;
  hasTenantWide: (permission: string) => boolean;
  centreId: string | undefined;
  setCentreId: (id: string) => void;
}

const SessionContext = createContext<Session | undefined>(undefined);
const CENTRE_KEY = 'core-neha.centre';

function storedCentre(): string | undefined {
  try {
    return window.localStorage.getItem(CENTRE_KEY) ?? undefined;
  } catch {
    return undefined;
  }
}

export function useMeQuery(): UseQueryResult<Me, ApiError> {
  return useQuery<Me, ApiError>({ queryKey: ['me'], queryFn: () => fetchMe(), retry: false, staleTime: 30_000 });
}

export function SessionProvider({ me, children }: { me: Me; children: ReactNode }) {
  const [chosen, setChosen] = useState<string | undefined>(storedCentre);
  const session = useMemo<Session>(() => {
    const tenant = new Set(me.tenantPermissions ?? []);
    const centres = me.centres ?? [];
    const centreId = centres.some((c) => c.centreId === chosen) ? chosen : centres[0]?.centreId;
    return {
      me,
      centreId,
      setCentreId: (id: string) => {
        setChosen(id);
        try {
          window.localStorage.setItem(CENTRE_KEY, id);
        } catch {
          /* storage unavailable: keep in memory only */
        }
      },
      hasTenantWide: (p) => tenant.has(p),
      hasAnywhere: (p) => tenant.has(p) || centres.some((c) => (c.permissions ?? []).includes(p)),
      hasAtCentre: (p, id) => tenant.has(p) || centres.some((c) => c.centreId === id && (c.permissions ?? []).includes(p)),
    };
  }, [me, chosen]);
  return <SessionContext.Provider value={session}>{children}</SessionContext.Provider>;
}

export function useSession(): Session {
  const s = useContext(SessionContext);
  if (!s) {
    throw new Error('useSession outside SessionProvider');
  }
  return s;
}
