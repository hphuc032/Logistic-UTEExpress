# ORD-04 Phase 2: RECEIVE_RETURN

Branch: `feature/return-01-phase2-receipt`. HP-approved contract implemented on 2026-10-18.
Changes remain uncommitted for TD review.

## Production behavior

`POST /vendor/orders/{id}/return/receive` accepts JSON and form submissions with
`expectedVersion`, `expectedRequestVersion`, `receiptReference`. Names match Phase 1.
Submitting the operation is the Vendor attestation accepted by HP. The UI explicitly
states the commitment and the accepted evidence types. This is not carrier verification.

The reference is limited to 255 Java characters before stripping, consistent with the
new `VARCHAR(255)` column. It must identify a valid handover record, receipt note, return
tracking number or traceable electronic document for this request. One unique
ReturnRequest per Order, row locking and both JPA versions prevent duplicate receipt.
Reference text is not globally unique: a document may cover several Orders. Actor and
timestamp come from persisted authentication and the server Clock; client actor, scope,
status, timestamp, refund and restock fields are rejected.

The existing return transaction rejects an outer transaction and locks/refreshes Order
then ReturnRequest before locking/rechecking active Account, VENDOR role and APPROVED
owned Shop. A receipt changes Order RETURN_REQUESTED to RETURNED and leaves ReturnRequest
APPROVED, increments both versions exactly once and writes one history row. The existing
after-commit event uses `Vendor attested return receipt`; rollback emits no event.
Generic RECEIVE_RETURN is explicitly blocked in the shared lifecycle implementation.

No Payment, Shipping, Ops cancellation, inventory, restocking, refund or voucher
production logic was changed or invoked by receipt. `restockable` remains NULL.
Owned projections expose receipt reference/time and keep actor IDs internal.

## Database

New migration: `V20261018090000__td_ord04_return_receipt.sql`.
Latest migration before this change: `V20261013090000__td_return01_management.sql`.
The existing migration remains unchanged.

Adds `received_by` with FK to `uteexpress.users`, and `receipt_reference VARCHAR(255)`.
Replaces `ck_return_requests_no_receipt` and `ck_return_requests_times`; preserves
Phase 1 decision/status/version/ownership/uniqueness checks. Receipt fields must all be
NULL or all populated on an APPROVED request. Receipt time must follow decision time,
and received rows require `updated_at = received_at`. `restockable` stays nullable with
a NULL-only check. A PostgreSQL trigger prevents overwriting, clearing or reassigning
successful receipt evidence.

## Test coverage

`ReturnManagementIT` retains Phase 1 coverage and adds receipt success, JSON/form/UI,
authorization, persisted eligibility, CSRF/forged fields, validation, stale versions,
wrong states, duplicate receipt, database CHECK/FK/immutability and rollback coverage.
Other-module rows are compared before and after receipt.

Concurrency uses separate worker transactions, latches, bounded lock/statement waits,
backend PIDs, `pg_stat_activity` and `pg_blocking_pids`. It proves a real Order waiter,
one winner on commit, a valid waiting retry on rollback, ReturnRequest version refresh
after contention, and both schedules for Account lock, Shop restriction and VENDOR
revocation. No Thread.sleep is used.

The existing Shop authority performs an ownership pre-read before the final Shop lock.
If moderation commits during the wait, Hibernate can reject its changed @Version at
lock acquisition with CONFLICT before refresh, rather than ACCESS_DENIED after refresh.
This existing Phase 1 outcome remains fail-closed; tests require unchanged receipt,
Order, history and no event for either denial outcome.

`ReturnReceiptMigrationIT` exercises fresh migration and upgrade from Phase 1 populated
with PENDING/APPROVED/REJECTED rows, preservation of all historical fields and Phase 1
checksum, Flyway validation, second-migrate no-op, nullable restockable, CHECK/FK checks
and successful immutable receipt.

## Changed files

- `src/main/java/com/uteexpress/order/entity/ReturnRequest.java`
- `src/main/java/com/uteexpress/order/dto/ReceiveReturnCommand.java` (new)
- `src/main/java/com/uteexpress/order/dto/ReceiveReturnInput.java` (new)
- `src/main/java/com/uteexpress/order/dto/ReturnManagementView.java`
- `src/main/java/com/uteexpress/order/dto/ReturnRequestView.java`
- `src/main/java/com/uteexpress/order/service/OrderLifecycleService.java`
- `src/main/java/com/uteexpress/order/service/OrderLifecycleServiceImpl.java`
- `src/main/java/com/uteexpress/order/service/OrderPlacementService.java`
- `src/main/java/com/uteexpress/order/service/ReturnOrderAuthority.java`
- `src/main/java/com/uteexpress/order/service/ReturnQueryService.java`
- `src/main/java/com/uteexpress/order/controller/VendorReturnController.java`
- `src/main/resources/templates/order/return.html`
- `src/main/resources/db/migration/V20261018090000__td_ord04_return_receipt.sql` (new)
- `src/test/java/com/uteexpress/order/ReturnManagementIT.java`
- `src/test/java/com/uteexpress/order/ReturnReceiptMigrationIT.java` (new)
- `docs/order-contracts.md`
- `docs/ORD-04-phase2-receipt-report.md` (new)

## Verification

Java: Eclipse Temurin 21.0.12.1; PostgreSQL 17.6 through Docker/Testcontainers.

Focused command (exit 0):

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
.\mvnw.cmd -Ppostgres-it '-Dit.test=ReturnManagementIT,ReturnReceiptMigrationIT' verify
```

423 Surefire tests and 151 Failsafe tests passed, zero failures/errors/skips.
`ReturnManagementIT`: 149; `ReturnReceiptMigrationIT`: 2.
Log: `.work-receipt-focused.log` (local ignored artifact).

Earlier focused failures were confined to new test setup: a JDBC timestamp cast and
missing reauthentication after MockMvc cleared SecurityContext. The subsequent Shop
race assertion was aligned with the existing Phase 1 ACCESS_DENIED/CONFLICT contract
described above. The Phase 1 UI assertion was updated because HP now authorizes receipt;
its generic-transition and refund denial assertions remain. No production failure was
hidden by weakening business-effect assertions.

The initial Maven invocation detected JAVA_HOME pointing at JDK 26 and failed the Java
21 enforcer; all successful verification uses the explicit Java 21 JAVA_HOME above.

Full PostgreSQL command (exit 0, BUILD SUCCESS, 6m40s):

```powershell
.\mvnw.cmd -Ppostgres-it clean verify
```

Uses the same explicit Java 21 JAVA_HOME above. 423 Surefire + 928 Failsafe = 1,351
tests passed; zero failures, errors, skips or Failsafe flakes. Totals were independently
summed from every generated TEST-*.xml and match `failsafe-summary.xml`.
Includes Phase 1/Phase 2 returns, COD Payment, Shipping fulfillment, Notification,
Order lifecycle and ArchitectureTest. Full log: `.work-receipt-full.log`.
`git diff --check`: exit 0. The original Phase 1 migration and Shipping/Payment/catalog
production paths have no diff (`git diff --exit-code` returned 0).

Remaining verification limitation: after all tests completed, the fork JVM exceeded
the 30-second System.exit shutdown allowance and was forcibly terminated. Maven still
reported BUILD SUCCESS/exit 0 and Failsafe summary reports timeout=false, all 928 tests
completed, no test failures. The dump shows SpringApplicationShutdownHook waiting in
HikariPool.shutdown/ThreadPoolExecutor.awaitTermination; concurrent pool threads retry
connections to already stopped Testcontainers PostgreSQL instances. Evidence:
`target/failsafe-reports/2026-10-18T12-10-15_412-jvmRun1.dump` and `.dumpstream`.
This is a test teardown limitation, not evidence of a receipt worker deadlock. No
unrelated pool/test-context configuration was changed to suppress it.

Other existing verification warnings include Mockito dynamic agent loading,
AuthenticationProvider/UserDetailsService configuration, and unchecked operations in
ProductPromotionIT. Expected CHECK/FK/unique violations are exercised by negative tests.

Final Git state: branch `feature/return-01-phase2-receipt`, HEAD
`85352d9cf492f6700c8adf67e24a2fc44dd2e11b`; 12 modified tracked files and 5 new untracked
files, all listed above. Nothing staged.

Scope deviations: none. No commit, push, PR or merge performed.

The business trust boundary remains Vendor attestation. Independent evidence retrieval,
carrier verification, refund and restocking require later approved contracts.
