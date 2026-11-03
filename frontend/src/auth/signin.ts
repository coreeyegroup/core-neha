import { API_BASE, readCookie } from '../api/http';

/** Starts the OIDC login handled by the backend (the browser never sees tokens). */
export function signIn(): void {
  window.location.assign(`${API_BASE}/oauth2/authorization/keycloak`);
}

/** Ends the application and identity-provider sessions with a form POST (full navigation). */
export function signOut(): void {
  const form = document.createElement('form');
  form.method = 'POST';
  form.action = `${API_BASE}/logout`;
  const token = readCookie('XSRF-TOKEN');
  if (token) {
    const input = document.createElement('input');
    input.type = 'hidden';
    input.name = '_csrf';
    input.value = token;
    form.appendChild(input);
  }
  document.body.appendChild(form);
  form.submit();
}
