# Backend guide
> How to add a feature: permission, migration, model, service, controller, test. Patterns and pitfalls.
<!-- audience: administrator -->

## The shape of a feature

1. **Permission**: add to `kernel/Permission.java` and insert into `iam.permission` in a new migration. Use `module.object.verb` (`billing.refund.approve`).
2. **Migration**: tables in the module's schema with `tenant_id`, RLS, `version`, CHECKs (see *Data model*).
3. **Models**: records in `<Module>Models.java`: a `…View` for responses, a command record per action with Bean Validation (`@NotNull`, `@Size`, `@Pattern`). Commands that update carry `@NotNull Integer expectedVersion`.
4. **Service** (`@Service`, public class, public methods for other modules and the controller):

```java
@Transactional
public BillDetail requestDiscount(Actor actor, UUID billId, BillingModels.RequestDiscount cmd) {
    BillView bill = billRow(billId);
    Scope scope = Scope.centre(bill.centreId());
    access.require(actor, Permission.BILLING_CHARGE, scope, "billing.discount.request", "bill", billId.toString());
    requireOpen(bill, cmd.expectedVersion());                  // rules → RuleViolationException / ConflictException
    jdbc.sql("update billing.bill set … , version = version + 1 where id = :id").param("id", billId).update();
    audit.record(AuditEntry.success(actor, "billing.discount.request", "bill", billId, scope,
            Map.of("billNo", bill.billNo(), "amount", cmd.amount().toPlainString())));
    return detailRow(billId);
}
```

   Order is always: load → **require permission at the right scope** → check rules → change → **audit** → return the fresh view.
5. **Controller**: thin, in the module, `@RequestMapping("/v1")`, `@Valid @RequestBody`, `actor.require()`. The method name becomes the client function name, so make it unique and meaningful.
6. **Tests**: an integration test with `PostgresTestBase` and `@MockitoBean IdentityProvisioner`; test the rule *and* the refusal paths (wrong person, missing permission).
7. **Contract and client**: see *API reference*.
8. **Help**: update the operation manual chapter for the screen.

## Patterns

- **SQL**: `JdbcClient` with text blocks and named parameters. Map rows into records by column name (`DataClassRowMapper`; snake_case maps to camelCase). Alias columns to component names (`is_demo as demo`).
- **Nullable parameters in predicates** need a cast: `(cast(:status as text) is null or b.status = :status)`; otherwise PostgreSQL cannot infer the type.
- **Upserts with optimistic locking**: `insert … on conflict (key) do update set …, version = t.version + 1 where t.version = cast(:v as int)`; zero rows ⇒ conflict.
- **Counters** (UHID, tokens, bill numbers): `insert … on conflict do update set last_value = last_value + 1 returning last_value`, which is atomic.
- **Cross-module reads** go through the other module's `…Directory` interface. Display-only joins to names are acceptable in read SQL.
- **Dates**: `BusinessTime.today()` (IST) for business days; `now()` in SQL for timestamps (stored as timestamptz).
- **Errors**: throw `NotFoundException`, `ConflictException(code, msg)`, `RuleViolationException(code, msg)`. Codes are `module.object.rule` and are shown to users, so make the message say what to do.
- **Operator commands**: add a `case` in `OperationsRunner` and an `ops/<name>.sh`; refuse to run outside demo for demo data.

## Pitfalls we have hit

| Symptom | Cause | Fix |
|---|---|---|
| Bean cycle at startup | Service depending on a directory implemented by itself through another bean | Separate `Jdbc…Directory` component |
| Enum mapping errors | DataClassRowMapper and Java enums | Keep status as `String` in records, or an explicit RowMapper |
| `RestClient.Builder` missing | Boot 4 no longer auto-configures it in this setup | `RestClient.builder()` directly |
| HttpSecurity in command mode | Security config created without a web context | `@ConditionalOnWebApplication` |
| Contract schema merged | Same record name in two modules | Unique record names |
| Address already in use for one-off containers | Fixed container IP in Compose | No fixed IP for the backend |

## Running locally

Maven needs access to Maven Central. Where it is blocked, push a branch and let CI build and test. Tests need Docker (Testcontainers starts `postgres:18.6-alpine3.24`).

```bash
mvn -f backend/pom.xml verify          # all tests
mvn -f backend/pom.xml -Dtest=ClinicSeedTest test
```
