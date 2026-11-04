// Synthetic eye-clinic data for the mock API (same shapes as the backend contract).
// Used with: node e2e/mock-server.mjs <port> --persona=<username>
const id = (n) => `0195f000-0000-7000-8000-${String(n).padStart(12, '0')}`;
const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Kolkata' }).format(new Date());
const now = new Date().toISOString();
const ago = (min) => new Date(Date.now() - min * 60_000).toISOString();

export const VEC = { id: id(150), code: 'VEC', name: 'Vijaya Eye Clinic' };

const ROLE_PERMISSIONS = {
  reception: ['org.centre.read', 'patient.read', 'patient.register', 'opd.queue.read', 'opd.appointment.manage', 'opd.checkin', 'billing.read', 'billing.charge', 'billing.collect'],
  optometry: ['org.centre.read', 'patient.read', 'opd.queue.read', 'eye.clinical.read', 'eye.optometry.record'],
  doctor: ['org.centre.read', 'patient.read', 'opd.queue.read', 'eye.clinical.read', 'eye.optometry.record', 'eye.consultation.record'],
  counselling: ['org.centre.read', 'patient.read', 'opd.queue.read', 'opd.appointment.manage', 'eye.clinical.read', 'eye.counselling.record', 'eye.surgery.schedule', 'billing.read'],
  surgeon: ['org.centre.read', 'patient.read', 'opd.queue.read', 'eye.clinical.read', 'eye.surgery.prepare', 'eye.surgery.operate'],
  director: ['org.centre.read', 'workforce.read', 'iam.assignment.read', 'patient.read', 'opd.queue.read', 'eye.clinical.read', 'billing.read', 'billing.report.read', 'billing.discount.approve', 'billing.dayclose.approve', 'audit.read'],
  accounts: ['org.centre.read', 'billing.read', 'billing.report.read', 'billing.dayclose.approve', 'billing.discount.approve', 'billing.tariff.manage', 'audit.read'],
};
const PERSONAS = {
  'priya.reception': ['Priya Sharma', 'reception', 'Receptionist', 'Reception'],
  'sunita.optometry': ['Sunita Rao', 'optometry', 'Optometrist', 'Optometry'],
  'dr.vijay': ['Vijay Kumar', 'doctor', 'Consultant ophthalmologist', 'Consultation (eye specialists)'],
  'lata.counselling': ['Lata Menon', 'counselling', 'Surgery counsellor', 'Surgery counselling'],
  'dr.arjun': ['Arjun Rao', 'surgeon', 'Ophthalmic surgeon', 'Operation theatre (OT)'],
  'shobha.accounts': ['Shobha Kulkarni', 'accounts', 'Accounts manager', 'Accounts'],
  'radhika.director': ['Radhika Narayan', 'director', 'Centre director', undefined],
};

export function personaMe(username, tenant) {
  const [name, role, position, dept] = PERSONAS[username];
  return {
    userId: id(1000), username, displayName: name, tenantId: tenant, tenantName: 'Neha Health Group (demonstration)', demoTenant: true,
    tenantPermissions: [], centres: [{ centreId: VEC.id, code: VEC.code, name: VEC.name, permissions: ROLE_PERMISSIONS[role] }],
    pendingApprovals: 0, responsibilities: [], postings: [{ centreId: VEC.id, centreName: VEC.name, positionTitle: position, ...(dept ? { departmentName: dept } : {}) }],
    roles: [{ roleName: `Eye clinic ${role}`, kind: 'ACCESS', scopeName: VEC.name }],
  };
}

const people = [
  ['Lakshmi', 'Narayan', 'FEMALE', 67], ['Gopal', 'Krishna', 'MALE', 72], ['Fathima', 'Begum', 'FEMALE', 58], ['Raghav', 'Shetty', 'MALE', 34],
  ['Savitri', 'Devi', 'FEMALE', 61], ['Arjun', 'Hegde', 'MALE', 19],
];
const patients = people.map(([g, f, sex, age], i) => ({
  id: id(2000 + i), uhid: `NH${String(i + 1).padStart(8, '0')}`, givenName: g, familyName: f, fullName: `${g} ${f}`, sex, ageYears: age,
  birthDateEstimated: true, mobile: `98450 1000${i + 1}`, addressLine: 'Vijayanagar, Bengaluru', city: 'Bengaluru', state: 'Karnataka', pincode: '560040',
  registeredCentreId: VEC.id, registeredCentreName: VEC.name, demo: true, status: 'ACTIVE', createdAt: now, version: 0,
}));
const P = (i) => ({ patientId: patients[i].id, patientUhid: patients[i].uhid, patientName: patients[i].fullName, patientSex: patients[i].sex, patientAge: patients[i].ageYears, patientMobile: patients[i].mobile });
const doctors = [
  { personId: id(3001), name: 'Vijay Kumar', positionTitle: 'Consultant ophthalmologist', departmentCode: 'OPHTHALMOLOGY', departmentName: 'Consultation (eye specialists)' },
  { personId: id(3002), name: 'Meera Nair', positionTitle: 'Associate ophthalmologist', departmentCode: 'OPHTHALMOLOGY', departmentName: 'Consultation (eye specialists)' },
  { personId: id(3003), name: 'Arjun Rao', positionTitle: 'Ophthalmic surgeon', departmentCode: 'SURGERY_DAYCARE', departmentName: 'Operation theatre (OT)' },
];
const stages = ['COMPLETED', 'CHECKOUT', 'COUNSELLING', 'CONSULTATION', 'OPTOMETRY', 'OPTOMETRY'];
const visits = stages.map((stage, i) => ({
  id: id(4000 + i), centreId: VEC.id, centreName: VEC.name, ...P(i), visitDate: today, tokenNo: i + 1, visitType: 'NEW',
  chiefComplaint: ['Difficulty seeing the board', 'Glaucoma check', 'Dim vision in the right eye', 'Headache while reading', 'Itching', 'Floaters'][i],
  consultantPersonId: doctors[i % 2].personId, consultantName: doctors[i % 2].name, stage, checkedInAt: ago(120 - i * 15),
  optometryDoneAt: i < 4 ? ago(90 - i * 10) : undefined, consultationDoneAt: i < 3 ? ago(60 - i * 10) : undefined, completedAt: i === 0 ? ago(20) : undefined,
  waitingMinutes: stage === 'COMPLETED' ? undefined : 10 + i * 7, surgeryAdvised: i === 2, version: 2,
}));
const optometry = { visitId: visits[2].id, ucvaRe: '6/60', ucvaLe: '6/12', sphRe: 1, cylRe: -0.75, axisRe: 90, bcvaRe: '6/36', sphLe: 0.75, cylLe: -0.5, axisLe: 85, bcvaLe: '6/9', iopRe: 16, iopLe: 15, iopMethod: 'NCT', recordedByName: 'Sunita Rao', recordedAt: ago(80), version: 1 };
const consultation = { visitId: visits[2].id, history: 'Dim vision six months', anteriorSegmentRe: 'Lens opacity', diagnosis: 'Immature senile cataract, right eye', diagnosisCode: 'H25.1', diagnosisEye: 'RE', glassesPrescribed: false, advice: 'Cataract surgery advised', surgeryAdvised: true, surgeryProcedure: 'Phacoemulsification with foldable IOL', surgeryEye: 'RE', surgeryUrgency: 'ROUTINE', consultantName: 'Vijay Kumar', recordedByName: 'Vijay Kumar', recordedAt: ago(50), version: 1 };
const surgeryBase = { centreId: VEC.id, eye: 'RE', urgency: 'ROUTINE', advisedByName: 'Vijay Kumar', diagnosis: 'Immature senile cataract, right eye', consentSigned: false, siteMarked: false, fitnessCleared: false, biometryDone: false, pupilDilated: false, createdAt: now, version: 1 };
const surgeries = [
  { ...surgeryBase, id: id(5001), ...P(2), visitId: visits[2].id, procedureName: 'Phacoemulsification with foldable IOL', status: 'ADVISED' },
  { ...surgeryBase, id: id(5002), ...P(1), visitId: visits[1].id, procedureName: 'Phacoemulsification with foldable IOL', status: 'READY', surgeryDate: today, slotTime: '14:00:00', otRoom: 'OT 1', surgeonPersonId: id(3003), surgeonName: 'Arjun Rao', packageItemId: id(6010), packageName: 'Cataract surgery: phaco with monofocal IOL', estimatedCost: 28000, paymentPlan: 'INSURANCE', insurerName: 'Star Health (demo)', consentSigned: true, siteMarked: true, fitnessCleared: true, biometryDone: true, iolPower: '+21.5 D', preparedByName: 'Mary Joseph', preparedAt: ago(30) },
  { ...surgeryBase, id: id(5003), ...P(3), visitId: visits[3].id, procedureName: 'Phacoemulsification with toric IOL', eye: 'LE', status: 'SCHEDULED', surgeryDate: today, slotTime: '15:00:00', otRoom: 'OT 1', surgeonPersonId: id(3003), surgeonName: 'Arjun Rao', packageName: 'Cataract surgery: phaco with premium toric IOL', estimatedCost: 55000 },
];
const tariff = [
  ['CONSULT_NEW', 'Consultation, new patient', 'CONSULTATION', 600], ['CONSULT_REVIEW', 'Consultation, review', 'CONSULTATION', 300], ['REFRACTION', 'Refraction and vision assessment', 'INVESTIGATION', 250],
  ['OCT_MACULA', 'OCT macula', 'INVESTIGATION', 1500], ['PKG_PHACO_MONOFOCAL', 'Cataract surgery: phaco with monofocal IOL', 'SURGERY_PACKAGE', 28000],
].map(([code, name, category, price], i) => ({ id: id(6000 + i * 10), centreId: VEC.id, code, name, category, price, gstRate: 0, status: 'ACTIVE', version: 0 }));
const bill = { id: id(7001), centreId: VEC.id, centreName: VEC.name, ...P(1), billNo: 'VEC/2026-27/000002', billDate: today, visitRef: visits[1].id, status: 'OPEN', grossAmount: 2300, taxAmount: 0, discountAmount: 0, netAmount: 2300, paidAmount: 0, balance: 2300, discountStatus: 'NONE', createdByName: 'Priya Sharma', createdAt: ago(10), version: 0 };
const pendingBill = { ...bill, id: id(7002), ...P(0), billNo: 'VEC/2026-27/000003', visitRef: visits[0].id, discountRequested: 300, discountReason: 'Senior citizen concession', discountStatus: 'PENDING', discountRequestedByName: 'Priya Sharma' };
const board = { centreId: VEC.id, date: today, appointmentsBooked: 4, appointmentsArrived: 6, appointmentsCancelled: 1, visits: 6, waitingOptometry: 2, waitingConsultation: 1, waitingCounselling: 1, waitingCheckout: 1, completed: 1, cancelled: 0, averageWaitMinutes: 24, longestWaitMinutes: 45, newPatients: 6, surgeriesAdvised: 1, surgeriesToSchedule: 0, surgeriesToday: 2, surgeriesReadyToday: 1, surgeriesCompletedToday: 0, surgeriesUpcoming: 0 };

export function clinicRoute(method, path, query) {
  let m;
  if (method === 'GET' && /^\/v1\/centres\/[^/]+\/clinic-board$/.test(path)) return board;
  if (method === 'GET' && /^\/v1\/centres\/[^/]+\/clinicians$/.test(path)) return doctors.filter((d) => !query.get('department') || d.departmentCode === query.get('department'));
  if (method === 'GET' && /^\/v1\/centres\/[^/]+\/appointments$/.test(path)) return [
    { id: id(4500), centreId: VEC.id, ...P(4), appointmentDate: today, slotTime: '15:00:00', visitType: 'NEW', source: 'PHONE', consultantName: 'Vijay Kumar', consultantPersonId: id(3001), status: 'BOOKED', createdByName: 'Priya Sharma', version: 0 },
    { id: id(4501), centreId: VEC.id, ...P(0), appointmentDate: today, slotTime: '09:00:00', visitType: 'NEW', source: 'ONLINE', consultantName: 'Vijay Kumar', status: 'CHECKED_IN', visitId: visits[0].id, createdByName: 'Priya Sharma', version: 1 },
  ];
  if (method === 'POST' && /^\/v1\/appointments\/[^/]+\/check-in$/.test(path)) return { ...visits[4], tokenNo: 7, patientName: patients[4].fullName };
  if (method === 'GET' && /^\/v1\/centres\/[^/]+\/visits$/.test(path)) return visits.filter((v) => !query.get('stage') || v.stage === query.get('stage'));
  if ((m = path.match(/^\/v1\/visits\/([^/]+)$/)) && method === 'GET') {
    const v = visits.find((x) => x.id === m[1]) ?? visits[2];
    return { visit: v, clinicalVisible: true, optometry: v.id === visits[2].id ? optometry : undefined, consultation: v.id === visits[2].id ? consultation : undefined,
      surgery: v.id === visits[2].id ? surgeries[0] : undefined, bills: v.id === visits[1].id ? [bill] : [], previousVisits: [] };
  }
  if (method === 'PUT' && (m = path.match(/^\/v1\/visits\/([^/]+)\/optometry$/))) {
    const v = visits.find((x) => x.id === m[1]) ?? visits[4];
    return { visit: { ...v, stage: 'CONSULTATION' }, clinicalVisible: true, optometry: { ...optometry, visitId: v.id }, bills: [], previousVisits: [] };
  }
  if (method === 'GET' && /^\/v1\/centres\/[^/]+\/surgeries$/.test(path)) {
    const view = query.get('view');
    return surgeries.filter((s) => (view === 'COUNSELLING' ? s.status === 'ADVISED' : view === 'OT_LIST' ? s.surgeryDate === today : view === 'UPCOMING' ? ['SCHEDULED', 'READY'].includes(s.status) : view === 'SCHEDULING' ? s.status === 'ACCEPTED' : true));
  }
  if (method === 'GET' && (m = path.match(/^\/v1\/surgeries\/([^/]+)$/))) return surgeries.find((s) => s.id === m[1]) ?? surgeries[0];
  if (method === 'GET' && /^\/v1\/surgeries\/[^/]+\/bills$/.test(path)) return [];
  if (method === 'GET' && /^\/v1\/centres\/[^/]+\/tariff$/.test(path)) return tariff;
  if (method === 'GET' && /^\/v1\/centres\/[^/]+\/collections$/.test(path)) return {
    centreId: VEC.id, from: today, to: today, billed: 41250, discounts: 300, collected: 38950, outstanding: 25300, bills: 9, pendingDiscounts: 1,
    byMode: [{ label: 'INSURANCE', amount: 28000, count: 1 }, { label: 'CASH', amount: 7350, count: 4 }, { label: 'UPI', amount: 1700, count: 2 }, { label: 'CARD', amount: 1900, count: 1 }],
    byCategory: [{ label: 'SURGERY_PACKAGE', amount: 33000, count: 2 }, { label: 'CONSULTATION', amount: 4200, count: 7 }, { label: 'INVESTIGATION', amount: 4050, count: 8 }],
    byDay: [{ date: today, billed: 40950, collected: 38950, bills: 9 }], byCollector: [{ label: 'Priya Sharma', amount: 38950, count: 8 }],
    outstandingBills: [bill, pendingBill],
  };
  if (method === 'GET' && /^\/v1\/centres\/[^/]+\/bills$/.test(path)) return [bill, pendingBill];
  if (method === 'GET' && /^\/v1\/centres\/[^/]+\/discounts\/pending$/.test(path)) return [pendingBill];
  if (method === 'POST' && /^\/v1\/bills\/[^/]+\/discount\/decision$/.test(path)) return { bill: { ...pendingBill, discountStatus: 'APPROVED', discountAmount: 300 }, lines: [], payments: [] };
  if (method === 'GET' && (m = path.match(/^\/v1\/bills\/([^/]+)$/))) {
    const b = m[1] === pendingBill.id ? pendingBill : bill;
    return { bill: b, lines: [{ id: id(7101), tariffItemId: tariff[0].id, description: 'Consultation, new patient', category: 'CONSULTATION', quantity: 1, unitPrice: 600, gstRate: 0, amount: 600, taxAmount: 0 }], payments: [] };
  }
  if (method === 'GET' && /^\/v1\/centres\/[^/]+\/day-closes\/preview$/.test(path)) return { businessDate: today, systemCash: 7350, systemCard: 1900, systemUpi: 1700, systemOther: 28000, payments: 8 };
  if (method === 'GET' && /^\/v1\/centres\/[^/]+\/day-closes$/.test(path)) return [];
  if (method === 'GET' && path === '/v1/patients') return patients.filter((p) => !query.get('q') || `${p.fullName} ${p.uhid} ${p.mobile}`.toLowerCase().includes(query.get('q').toLowerCase()));
  if (method === 'GET' && (m = path.match(/^\/v1\/patients\/([^/]+)$/))) return patients.find((p) => p.id === m[1]) ?? patients[0];
  if (method === 'GET' && /^\/v1\/patients\/[^/]+\/eye-history$/.test(path)) return { patientId: patients[2].id, clinicalVisible: true, visits: [{ visit: visits[2], diagnosis: consultation.diagnosis, diagnosisCode: 'H25.1', bcvaRe: '6/36', bcvaLe: '6/9', iopRe: 16, iopLe: 15 }], surgeries: [surgeries[0]], upcomingAppointments: [] };
  if (method === 'GET' && /^\/v1\/patients\/[^/]+\/bills$/.test(path)) return [bill];
  return undefined;
}
