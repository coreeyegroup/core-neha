import { afterEach, describe, expect, it, vi } from 'vitest';

import { ApiError, apiFetch } from './http';

describe('apiFetch', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT';
  });

  it('sends the CSRF token on changes but not on reads', async () => {
    document.cookie = 'XSRF-TOKEN=abc123';
    const spy = vi.spyOn(globalThis, 'fetch').mockImplementation(async () => new Response('{}', { status: 200 }));
    await apiFetch('/v1/centres');
    await apiFetch('/v1/centres', { method: 'POST', body: '{}' });
    const [getCall, postCall] = spy.mock.calls;
    expect(new Headers(getCall?.[1]?.headers).get('X-XSRF-TOKEN')).toBeNull();
    expect(new Headers(postCall?.[1]?.headers).get('X-XSRF-TOKEN')).toBe('abc123');
    expect(String(postCall?.[0])).toBe('/core-neha/api/v1/centres');
    expect(postCall?.[1]?.credentials).toBe('same-origin');
  });

  it('turns a problem response into an ApiError with code and request id', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ status: 422, code: 'iam.self-approval', detail: 'You cannot decide a request you made', requestId: 'r-1' }), { status: 422 }),
    );
    const error = await apiFetch('/v1/assignments/x/approve', { method: 'POST' }).catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).code).toBe('iam.self-approval');
    expect((error as ApiError).status).toBe(422);
    expect((error as ApiError).requestId).toBe('r-1');
  });

  it('describes a refused session without a body', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('', { status: 401 }));
    const error = (await apiFetch('/v1/me').catch((e: unknown) => e)) as ApiError;
    expect(error.code).toBe('unauthenticated');
    expect(error.message).toMatch(/sign in/i);
  });
});
