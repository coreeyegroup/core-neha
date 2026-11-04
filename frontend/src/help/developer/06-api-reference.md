# API reference
> Every endpoint, how errors look, and how the contract and the generated client are kept in step.

## Conventions

- Base path: `/core-neha/api`, all business endpoints under `/v1`. JSON in and out; empty fields are omitted.
- Authentication by session cookie; mutating requests need the `X-XSRF-TOKEN` header.
- Collection endpoints that belong to a centre are `/v1/centres/{centreId}/…`; the centre is the access scope.
- Updates send `expectedVersion`; a stale version returns **409**.
- Errors are RFC 9457 problem documents:

```
HTTP/1.1 422
{ "type": "urn:core-neha:problem:billing.discount.self-approval",
  "title": "Unprocessable Entity", "status": 422,
  "detail": "You cannot decide a discount you requested",
  "code": "billing.discount.self-approval", "requestId": "…" }
```

| Status | Exception | Meaning |
|---|---|---|
| 401 | `UnauthenticatedException` | No session / account no longer valid (`session.ended`) |
| 403 | `AccessDeniedException` | Missing permission at the scope (`access.denied`), audited as DENIED |
| 404 | `NotFoundException` | Not found in your tenant |
| 409 | `ConflictException`, DB uniqueness | Concurrent change or duplicate |
| 422 | `RuleViolationException` | A business rule refused it; `code` names the rule |
| 400 | validation | Bean Validation failed on the request body |

## The contract workflow

1. Change controllers or model records in Java.
2. Push. CI runs `OpenApiContractTest`, which exports `backend/target/openapi.json`, and compares it with `contracts/openapi.json`. If it differs, CI prints the new contract between `OPENAPI-CONTRACT-BEGIN` and `OPENAPI-CONTRACT-END` in the log (one field per line).
3. Copy it into `contracts/openapi.json` (sorted keys, two-space indent), then in `frontend/` run `npm run generate:api`.
4. Commit both. CI fails the frontend job if `src/api/generated` does not match the contract.

> **Important** Record names become schema names. Two records with the same simple name in different modules (for example two `Decision` records) are merged into one schema by springdoc and the contract becomes wrong. Give every request and response record a unique name.

## Organisation

| Method | Path (under /core-neha/api/v1) | Client function |
|---|---|---|
| GET | `/centres` | `centres` |
| POST | `/centres` | `createCentre` |
| GET | `/centres/{centreId}` | `centre` |
| GET | `/centres/{centreId}/departments` | `centreDepartments` |
| POST | `/centres/{centreId}/departments` | `enableDepartment` |
| POST | `/centres/{centreId}/departments/{centreDepartmentId}/retire` | `retire` |
| GET | `/centres/{centreId}/departments/{centreDepartmentId}/retire-impact` | `retireImpact` |
| GET | `/departments` | `departments` |
| POST | `/departments` | `createDepartment` |
| GET | `/hierarchy/levels` | `levels` |
| POST | `/hierarchy/levels` | `createLevel` |
| GET | `/hierarchy/nodes` | `nodes` |
| POST | `/hierarchy/nodes` | `createNode` |
| GET | `/legal-entities` | `legalEntities` |
| POST | `/legal-entities` | `createLegalEntity` |

## Workforce

| Method | Path (under /core-neha/api/v1) | Client function |
|---|---|---|
| GET | `/centres/{centreId}/establishments` | `establishments` |
| POST | `/centres/{centreId}/establishments` | `establish` |
| GET | `/people` | `people` |
| POST | `/people` | `createPerson` |
| GET | `/people/{personId}` | `person` |
| POST | `/people/{personId}/postings` | `addPosting` |
| GET | `/positions` | `positions` |
| POST | `/positions` | `definePosition` |
| POST | `/postings/{postingId}/end` | `endPosting` |

## Identity and access

| Method | Path (under /core-neha/api/v1) | Client function |
|---|---|---|
| GET | `/approvals` | `pending` |
| POST | `/assignments` | `request` |
| POST | `/assignments/{assignmentId}/approve` | `approve` |
| POST | `/assignments/{assignmentId}/reject` | `reject` |
| POST | `/assignments/{assignmentId}/revoke` | `revoke` |
| GET | `/me` | `me` |
| GET | `/permissions` | `permissions` |
| POST | `/responsibilities` | `assignResponsibility` |
| GET | `/responsibility-types` | `responsibilityTypes` |
| POST | `/responsibility-types` | `defineResponsibility` |
| PUT | `/role-versions/{versionId}` | `updateDraft` |
| POST | `/role-versions/{versionId}/approve` | `approveVersion` |
| GET | `/role-versions/{versionId}/impact` | `impact` |
| POST | `/role-versions/{versionId}/reject` | `rejectVersion` |
| POST | `/role-versions/{versionId}/submit` | `submit` |
| GET | `/roles` | `roles` |
| POST | `/roles` | `createRole` |
| POST | `/roles/clone` | `cloneRole` |
| GET | `/roles/{roleId}` | `role` |
| POST | `/roles/{roleId}/versions` | `draftVersion` |
| GET | `/users` | `users` |
| POST | `/users` | `createUser` |
| GET | `/users/{userId}/assignments` | `userAssignments` |
| POST | `/users/{userId}/disable` | `disable` |
| GET | `/users/{userId}/effective-access` | `effectiveAccess` |
| GET | `/users/{userId}/effective-access/explain` | `explain` |
| POST | `/users/{userId}/enable` | `enable` |

## Audit

| Method | Path (under /core-neha/api/v1) | Client function |
|---|---|---|
| GET | `/audit-events` | `search` |

## Patients

| Method | Path (under /core-neha/api/v1) | Client function |
|---|---|---|
| GET | `/patients` | `searchPatients` |
| POST | `/patients` | `registerPatient` |
| GET | `/patients/{patientId}` | `patient` |
| PUT | `/patients/{patientId}` | `updatePatient` |

## Eye care

| Method | Path (under /core-neha/api/v1) | Client function |
|---|---|---|
| POST | `/appointments/{appointmentId}/cancel` | `cancelAppointment` |
| POST | `/appointments/{appointmentId}/check-in` | `checkInAppointment` |
| GET | `/centres/{centreId}/appointments` | `appointments` |
| POST | `/centres/{centreId}/appointments` | `bookAppointment` |
| GET | `/centres/{centreId}/clinic-board` | `clinicBoard` |
| GET | `/centres/{centreId}/clinicians` | `clinicians` |
| GET | `/centres/{centreId}/surgeries` | `surgeries` |
| GET | `/centres/{centreId}/visits` | `visits` |
| POST | `/centres/{centreId}/visits` | `startWalkInVisit` |
| GET | `/patients/{patientId}/eye-history` | `patientEyeHistory` |
| GET | `/surgeries/{surgeryId}` | `surgery` |
| GET | `/surgeries/{surgeryId}/bills` | `surgeryBills` |
| POST | `/surgeries/{surgeryId}/cancel` | `cancelSurgery` |
| POST | `/surgeries/{surgeryId}/counselling` | `counselSurgery` |
| PUT | `/surgeries/{surgeryId}/operation` | `saveSurgeryOperation` |
| PUT | `/surgeries/{surgeryId}/pre-op` | `saveSurgeryPreOp` |
| POST | `/surgeries/{surgeryId}/schedule` | `scheduleSurgery` |
| GET | `/visits/{visitId}` | `visitChart` |
| POST | `/visits/{visitId}/cancel` | `cancelVisit` |
| POST | `/visits/{visitId}/checkout` | `checkoutVisit` |
| PUT | `/visits/{visitId}/consultation` | `saveConsultation` |
| PUT | `/visits/{visitId}/optometry` | `saveOptometry` |

## Billing and accounts

| Method | Path (under /core-neha/api/v1) | Client function |
|---|---|---|
| GET | `/bills/{billId}` | `bill` |
| POST | `/bills/{billId}/cancel` | `cancelBill` |
| POST | `/bills/{billId}/discount` | `requestDiscount` |
| POST | `/bills/{billId}/discount/decision` | `decideDiscount` |
| POST | `/bills/{billId}/lines` | `addBillLines` |
| POST | `/bills/{billId}/payments` | `collectPayment` |
| POST | `/bills/{billId}/remove-line` | `removeBillLine` |
| GET | `/centres/{centreId}/bills` | `bills` |
| POST | `/centres/{centreId}/bills` | `createBill` |
| GET | `/centres/{centreId}/collections` | `collections` |
| GET | `/centres/{centreId}/day-closes` | `dayCloses` |
| POST | `/centres/{centreId}/day-closes` | `submitDayClose` |
| GET | `/centres/{centreId}/day-closes/preview` | `dayClosePreview` |
| GET | `/centres/{centreId}/discounts/pending` | `pendingDiscounts` |
| GET | `/centres/{centreId}/tariff` | `tariff` |
| POST | `/centres/{centreId}/tariff` | `createTariffItem` |
| PUT | `/centres/{centreId}/tariff/{itemId}` | `updateTariffItem` |
| POST | `/day-closes/{dayCloseId}/decision` | `decideDayClose` |
| GET | `/patients/{patientId}/bills` | `patientBills` |

