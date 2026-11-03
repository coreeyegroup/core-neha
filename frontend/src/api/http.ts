/**
 * The only HTTP entry point of the staff application. Same-origin requests with
 * the session cookie; mutating requests carry the CSRF token from the
 * XSRF-TOKEN cookie. Errors become ApiError with the server's problem details.
 */
export const API_BASE = `${__BASE_PATH__}/api`;

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly requestId?: string;

  constructor(status: number, code: string, message: string, requestId?: string) {
    super(message);
    this.status = status;
    this.code = code;
    this.requestId = requestId;
  }
}

export function readCookie(name: string): string | undefined {
  const match = document.cookie.split('; ').find((c) => c.startsWith(`${name}=`));
  return match ? decodeURIComponent(match.substring(name.length + 1)) : undefined;
}

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS']);

export async function apiFetch<T>(url: string, options: RequestInit = {}): Promise<T> {
  const method = (options.method ?? 'GET').toUpperCase();
  const headers = new Headers(options.headers);
  headers.set('Accept', 'application/json');
  if (!SAFE_METHODS.has(method)) {
    const token = readCookie('XSRF-TOKEN');
    if (token) {
      headers.set('X-XSRF-TOKEN', token);
    }
  }
  const target = url.startsWith('http') ? url : `${API_BASE}${url.replace(/^\/api/, '')}`;
  const response = await fetch(target, { ...options, method, headers, credentials: 'same-origin' });
  if (response.status === 204) {
    return undefined as T;
  }
  const text = await response.text();
  const body: unknown = text ? safeJson(text) : undefined;
  if (!response.ok) {
    const problem = (body ?? {}) as { code?: string; detail?: string; title?: string; requestId?: string };
    throw new ApiError(
      response.status,
      problem.code ?? (response.status === 401 ? 'unauthenticated' : `http.${response.status}`),
      problem.detail ?? problem.title ?? describeStatus(response.status),
      problem.requestId,
    );
  }
  return body as T;
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return undefined;
  }
}

function describeStatus(status: number): string {
  switch (status) {
    case 401:
      return 'Your session has ended. Please sign in again.';
    case 403:
      return 'You do not have permission for this action here.';
    case 404:
      return 'Not found.';
    case 409:
      return 'Someone else changed this. Reload and try again.';
    default:
      return 'The request could not be completed.';
  }
}

export default apiFetch;
