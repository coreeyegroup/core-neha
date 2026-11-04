// Serves the built staff application at /core-neha/ with a mock API (synthetic data,
// same shapes as the backend contract). Used for browser checks and Playwright tests.
//   node e2e/mock-server.mjs [port] [--signed-out]
import { createServer } from 'node:http';
import { readdirSync, readFileSync } from 'node:fs';
import { readFile } from 'node:fs/promises';
import { extname, join } from 'node:path';

import { clinicRoute, personaMe } from './clinic-mock.mjs';

const port = Number(process.argv[2] ?? 4173);
let signedIn = !process.argv.includes('--signed-out');
// --persona=<username>: sign in as one of the Vijay Eye Clinic staff (see clinic-mock.mjs).
const persona = process.argv.find((a) => a.startsWith('--persona='))?.slice('--persona='.length);
const dist = new URL('../dist/', import.meta.url).pathname;
const website = new URL('../website/', import.meta.url).pathname;
// Help chapters from the backend resources, filtered like HelpService (audience line in each file).
const helpRoot = new URL('../../backend/src/main/resources/help/', import.meta.url).pathname;
const helpChapters = ['manual', 'developer'].flatMap((book) => readdirSync(helpRoot + book).filter((f) => /^\d+-.+\.md$/.test(f)).map((f) => {
  const lines = readFileSync(helpRoot + book + '/' + f, 'utf8').split('\n');
  const title = lines.shift().replace(/^#\s+/, '');
  const summary = lines[0].startsWith('>') ? lines.shift().replace(/^>\s*/, '') : '';
  const aud = /^<!--\s*audience:\s*(.*?)\s*-->$/.exec(lines[0].trim());
  if (aud) lines.shift();
  return { book, slug: f.replace(/^\d+-/, '').replace(/\.md$/, ''), order: Number(f.split('-')[0]), title, summary, audience: aud ? aud[1].split(/\s+/) : ['administrator'], body: lines.join('\n').trim() };
}));
function helpFor(who) {
  const held = new Set([...(who.tenantPermissions ?? []), ...(who.centres ?? []).flatMap((c) => c.permissions ?? [])]);
  const admin = (who.tenantPermissions ?? []).includes('iam.role.approve');
  const chapters = helpChapters.filter((c) => c.audience.includes('everyone') || (c.audience.includes('administrator') ? admin : c.audience.some((p) => held.has(p))))
    .map(({ audience, ...c }) => c);
  return { administrator: admin, chapters };
}
const T = '0195f000-0000-7000-8000-000000000001';
const id = (n) => `0195f000-0000-7000-8000-${String(n).padStart(12, '0')}`;
const centres = [
  { id: id(101), code: 'BLR-IND', name: 'Indiranagar Eye Hospital', centreType: 'EYE_HOSPITAL', nodeName: 'Bengaluru', legalEntityName: 'Neha Hospitals Pvt Ltd (demo)', status: 'ACTIVE', version: 0, createdAt: '2026-11-04T00:00:00Z' },
  { id: id(102), code: 'BLR-JAY', name: 'Jayanagar Eye Clinic', centreType: 'EYE_CLINIC', nodeName: 'Bengaluru', legalEntityName: 'Neha Eye and Health Care Pvt Ltd (demo)', status: 'ACTIVE', version: 0, createdAt: '2026-11-04T00:00:00Z' },
  { id: id(103), code: 'BLR-OPL', name: 'Peenya Optical Laboratory', centreType: 'OPTICAL_LAB', nodeName: 'Bengaluru', legalEntityName: 'Neha Optical Labs Pvt Ltd (demo)', status: 'ACTIVE', version: 0, createdAt: '2026-11-04T00:00:00Z' },
];
const allPerms = ['org.structure.read', 'org.structure.manage', 'org.centre.create', 'org.centre.read', 'org.centre.configure', 'workforce.position.define', 'workforce.read', 'workforce.establish', 'workforce.person.manage', 'iam.role.read', 'iam.role.draft', 'iam.role.approve', 'iam.user.manage', 'iam.assignment.read', 'iam.assignment.request', 'iam.assignment.approve', 'iam.assignment.revoke', 'iam.responsibility.manage', 'audit.read'];
const me = {
  userId: id(1), username: 'asha.admin', displayName: 'Asha Admin', tenantId: T, tenantName: 'Neha Health Group (demonstration)', demoTenant: true,
  tenantPermissions: allPerms, centres: centres.map((c) => ({ centreId: c.id, code: c.code, name: c.name, permissions: allPerms.filter((p) => !['org.structure.manage', 'workforce.position.define', 'iam.role.read', 'iam.role.draft', 'iam.role.approve'].includes(p)) })),
  pendingApprovals: 1, responsibilities: [], postings: [], roles: [{ roleName: 'System administrator', kind: 'ACCESS', scopeName: 'Neha Health Group (demonstration)' }],
};
const depts = [
  ['RECEPTION', 'Reception and front office', 'ADMINISTRATIVE'], ['OPHTHALMOLOGY', 'Ophthalmology', 'CLINICAL'], ['OPTOMETRY', 'Optometry', 'CLINICAL'],
  ['OPTICAL_RETAIL', 'Optical retail and dispensing', 'OPERATIONS'], ['SURGERY_DAYCARE', 'Surgery and day-care', 'CLINICAL'], ['LABORATORY', 'Laboratory', 'CLINICAL_SUPPORT'],
  ['OPTICAL_MANUFACTURING', 'Optical manufacturing', 'OPERATIONS'],
].map(([code, name, category], i) => ({ id: id(200 + i), code, name, category, status: 'ACTIVE', version: 0 }));
const centreDepts = depts.slice(0, 6).map((d, i) => ({ id: id(300 + i), departmentId: d.id, departmentCode: d.code, departmentName: d.name, category: d.category, validFrom: '2026-11-04', version: 0 }));
const roles = [
  { id: id(401), code: 'SYSTEM_ADMINISTRATOR', name: 'System administrator', system: true, status: 'ACTIVE', publishedVersion: 1, version: 0 },
  { id: id(402), code: 'CENTRE_MANAGER_ACCESS', name: 'Centre manager access', system: false, status: 'ACTIVE', publishedVersion: 1, openVersion: 2, openVersionStatus: 'PENDING_APPROVAL', version: 0 },
  { id: id(403), code: 'FRONT_OFFICE', name: 'Front office', system: false, status: 'ACTIVE', publishedVersion: 1, version: 0 },
];
const pending = [{ id: id(501), kind: 'ACCESS', userId: id(2), username: 'kiran.frontdesk', roleId: id(403), roleName: 'Front office', scopeType: 'CENTRE', scopeId: id(102), scopeName: 'Jayanagar Eye Clinic', status: 'PENDING_APPROVAL', reason: 'New receptionist', requestedByName: 'meena.manager', requestedAt: '2026-11-04T05:00:00Z', version: 0, viaBootstrap: false }];
const user = { id: id(2), personId: id(601), personName: 'Kiran Reddy', username: 'kiran.frontdesk', status: 'ACTIVE', accessVersion: 1, loginLinked: true, version: 0 };
const routes = {
  'GET /v1/me': () => me,
  'GET /v1/centres': () => centres,
  'GET /v1/departments': () => depts,
  'GET /v1/hierarchy/levels': () => [{ id: id(11), code: 'STATE', name: 'State', rank: 1, status: 'ACTIVE', version: 0 }, { id: id(12), code: 'CITY', name: 'City', rank: 2, status: 'ACTIVE', version: 0 }],
  'GET /v1/hierarchy/nodes': () => [{ id: id(21), levelCode: 'STATE', code: 'KA', name: 'Karnataka', path: `/${id(21)}/`, status: 'ACTIVE' }, { id: id(22), levelCode: 'CITY', code: 'BLR', name: 'Bengaluru', path: `/${id(21)}/${id(22)}/`, status: 'ACTIVE' }],
  'GET /v1/legal-entities': () => [{ id: id(31), code: 'HOSP', name: 'Neha Hospitals Pvt Ltd (demo)', status: 'ACTIVE', version: 0 }],
  'GET /v1/positions': () => [{ id: id(41), code: 'OPTOMETRIST', title: 'Optometrist', category: 'ALLIED_HEALTH', status: 'ACTIVE', version: 0 }],
  'GET /v1/people': () => [{ id: id(601), givenName: 'Kiran', familyName: 'Reddy', email: 'kiran@demo.core-neha.invalid', demo: true, status: 'ACTIVE', version: 0 }],
  'GET /v1/users': () => [user],
  'GET /v1/roles': () => roles,
  'GET /v1/approvals': () => pending,
  'GET /v1/permissions': () => allPerms.map((code) => ({ code, module: code.split('.')[0], description: `Permission ${code}`, tenantOnly: ['org.structure.manage', 'workforce.position.define', 'iam.role.read', 'iam.role.draft', 'iam.role.approve'].includes(code) })),
  'GET /v1/audit-events': () => [
    { id: 3, occurredAt: '2026-11-04T05:02:00Z', actorLabel: 'kiran.frontdesk', action: 'org.centre.read', objectType: 'centre', objectId: id(101), scopeType: 'CENTRE', scopeId: id(101), outcome: 'DENIED', reason: 'missing permission org.centre.read at CENTRE' },
    { id: 2, occurredAt: '2026-11-04T05:01:00Z', actorLabel: 'bala.approver', action: 'iam.assignment.approve', objectType: 'role_assignment', objectId: id(500), scopeType: 'CENTRE', scopeId: id(102), outcome: 'SUCCESS' },
  ],
};
function dynamic(method, path) {
  let m;
  if (method === 'GET' && (m = path.match(/^\/v1\/centres\/([^/]+)$/))) return centres.find((c) => c.id === m[1]) ?? { __status: 404 };
  if (method === 'GET' && /^\/v1\/centres\/[^/]+\/departments$/.test(path)) return centreDepts;
  if (method === 'GET' && /^\/v1\/centres\/[^/]+\/establishments$/.test(path)) return [{ id: id(701), positionTitle: 'Optometrist', departmentName: 'Optometry', approvedHeadcount: 2, activePostings: 1, validFrom: '2026-11-04', version: 0 }];
  if (method === 'GET' && /^\/v1\/users\/[^/]+\/effective-access$/.test(path)) return {
    user, notes: ['Responsibilities are listed for information only; they never grant access.'],
    assignments: [{ ...pending[0], id: id(502), status: 'ACTIVE', roleName: 'Front office', scopeName: 'Jayanagar Eye Clinic' }],
    permissions: [{ permission: 'org.centre.read', description: 'View centres and their configuration', tenantOnly: false, scopes: [{ scopeType: 'CENTRE', scopeId: id(102), scopeName: 'Jayanagar Eye Clinic', viaRole: 'FRONT_OFFICE', viaAssignment: id(502) }] }],
    responsibilities: [{ id: id(801), code: 'FIRE_SAFETY_WARDEN', name: 'Fire safety warden', scopeName: 'Jayanagar Eye Clinic' }],
  };
  if (method === 'GET' && (m = path.match(/^\/v1\/roles\/([^/]+)$/))) {
    const role = roles.find((r) => r.id === m[1]) ?? roles[1];
    return { role, versions: [
      { id: id(901), roleId: role.id, versionNo: 2, status: 'PENDING_APPROVAL', changeNote: 'Add audit read', createdBy: id(3), submittedBy: id(3), permissions: ['org.centre.read', 'workforce.read', 'audit.read'], version: 1 },
      { id: id(902), roleId: role.id, versionNo: 1, status: 'PUBLISHED', changeNote: 'Initial version', permissions: ['org.centre.read', 'workforce.read'], decisionNote: 'Reviewed', version: 2 },
    ] };
  }
  if (method === 'GET' && /^\/v1\/role-versions\/[^/]+\/impact$/.test(path)) return { added: ['audit.read'], removed: [], affectedUsers: 1, affected: [{ username: 'meena.manager' }] };
  if (method === 'POST' && /^\/v1\/assignments\/[^/]+\/approve$/.test(path)) return { ...pending[0], status: 'ACTIVE' };
  if (method === 'POST' && path === '/v1/assignments') return { __status: 422, code: 'iam.assignment.self', detail: 'You cannot request access or authority for yourself', requestId: 'mock-1' };
  return undefined;
}
const types = { '.js': 'text/javascript', '.css': 'text/css', '.html': 'text/html', '.woff2': 'font/woff2', '.woff': 'font/woff', '.svg': 'image/svg+xml' };
createServer(async (req, res) => {
  const url = new URL(req.url, 'http://x');
  if (url.pathname === '/core-neha/api/logout') { signedIn = false; res.writeHead(302, { Location: '/core-neha/staff/signed-out' }); return res.end(); }
  if (url.pathname === '/core-neha/api/oauth2/authorization/keycloak') { signedIn = true; res.writeHead(302, { Location: '/core-neha/staff/' }); return res.end(); }
  if (url.pathname.startsWith('/core-neha/api/')) {
    const path = url.pathname.replace('/core-neha/api', '');
    if (!signedIn) { res.writeHead(401, { 'Content-Type': 'application/problem+json' }); return res.end('{"status":401}'); }
    // A test can also choose the persona per browser with a mock_persona cookie.
    const who = /(?:^|; )mock_persona=([^;]+)/.exec(req.headers.cookie ?? '')?.[1] ?? persona;
    const meNow = who ? personaMe(who, T) : me;
    const body = req.method === 'GET' && path === '/v1/help' ? helpFor(meNow)
      : who && req.method === 'GET' && path === '/v1/me' ? meNow
      : clinicRoute(req.method, path, url.searchParams) ?? routes[`${req.method} ${path}`]?.() ?? dynamic(req.method, path);
    if (body === undefined) { res.writeHead(404); return res.end('{"status":404,"code":"not-found","detail":"Not found"}'); }
    const status = body.__status ?? 200;
    res.writeHead(status, { 'Content-Type': 'application/json', 'Set-Cookie': 'XSRF-TOKEN=mock-token; Path=/core-neha' });
    return res.end(JSON.stringify(body));
  }
  if (url.pathname.startsWith('/core-neha/staff')) {
    const file = url.pathname.replace(/^\/core-neha\/staff\/?/, '') || 'index.html';
    try {
      const data = await readFile(join(dist, file));
      res.writeHead(200, { 'Content-Type': types[extname(file)] ?? 'application/octet-stream' });
      return res.end(data);
    } catch {
      res.writeHead(200, { 'Content-Type': 'text/html' });
      return res.end(await readFile(join(dist, 'index.html')));
    }
  }
  // Public website (static), like the gateway: <name> serves <name>.html.
  let file = url.pathname.replace(/^\/core-neha\/?/, '') || 'index.html';
  for (const candidate of [file, `${file}.html`]) {
    try {
      const data = await readFile(join(website, candidate));
      res.writeHead(200, { 'Content-Type': types[extname(candidate)] ?? 'application/octet-stream' });
      return res.end(data);
    } catch { /* try the next */ }
  }
  res.writeHead(404, { 'Content-Type': 'text/html' });
  res.end(await readFile(join(website, '404.html')));
}).listen(port, () => console.log(`mock website on http://localhost:${port}/core-neha/ and staff app on /core-neha/staff/`));
