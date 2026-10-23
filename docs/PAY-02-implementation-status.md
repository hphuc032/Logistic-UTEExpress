# PAY-02 implementation status

Completed manual full COD refund implementation and required scoped verification on
`feature/pay-02-refund`, HEAD `425dd6aecf83c2f0b3e3147e1eda68e0b53368b0`.

HP final-interface approval, QD design PASS `d56b3b6`, and migration reservation
`48fa7c3` are supplied by the user. These two approval commits are not in the local
object database; their contents were not independently reviewed or fetched.
The previous independent-slice reservation blocker is superseded by this task.
There is no SHIP-04 dependency. No remaining implementation/required-test blocker
was found in this local scope. No deployment or full repository-wide PostgreSQL
suite is claimed.

## Implementation and ownership

- `POST /admin/orders/{orderId}/refunds`, JSON body containing only
  `expectedOrderVersion`, `expectedReturnRequestVersion`, `proofReference`, and
  one required `Idempotency-Key` header. Reject unknown JSON fields, query actor
  overrides, duplicate headers, and invalid commands. Standard CSRF remains active.
- ADMIN-only controller and lifecycle method; persisted ACTIVE Account and current
  ADMIN role are checked using existing public HP/QD services. MANAGER has no access.
  No HP/QD-owned production Java module changed.
- Dedicated `OrderLifecycleService.completeRefund` rejects an outer transaction,
  starts READ_COMMITTED, takes a transaction advisory lock derived from the
  operation and exact key, then locks Order -> ReturnRequest -> Payment -> Refund
  evidence -> current ADMIN Account. Internal authorities are MANDATORY.
- The fresh transaction has no earlier Account entity load; OSIV is disabled.
  Account authorization remains locked through commit, with no subsequent Role row
  lock or acquisition of another Order. Governance role reads use its public service.
- Payment owns collection validation and JDBC refund persistence; Order owns the
  dedicated RETURNED -> REFUNDED writer, history and safe audit. Ledger insert,
  Order/version, history and audit commit or roll back together. The shared lifecycle
  writer publishes only after commit. An independent physical connection in the
  event listener verifies committed ledger, Order, history and audit visibility.
- Require RETURNED, APPROVED, both original versions, immutable persisted receipt
  and matching RECEIVE_RETURN history actor/time, one successful COD PAID Payment,
  non-null paid_at, null expired_at, exact positive whole-VND Order grand_total,
  and no previous refund. Reject ONLINE, partial, cancellation and zero refunds.
- Payment remains PAID; ReturnRequest/version/receipt/restockable remain unchanged.
  Preserve Order historical snapshots, Inventory, Shipping/Shipment and redeemed
  voucher state. A populated delivered-Shipment/redeemed-voucher test verifies this.
  No gateway or restocking implementation was added.

## Ledger migration

Exactly one new migration:
`src/main/resources/db/migration/V20261022090000__td_pay02_refund_ledger.sql`.
Existing applied migration contents are unchanged.

The version follows Receipt `20261018090000` and Shipping evidence
`20261018110000`. `uteexpress.refunds` stores only immutable SUCCEEDED results.
It has unique Order, Payment, ReturnRequest and `(operation, idempotency_key)`
constraints, restrictive immediate composite Payment/Order and ReturnRequest/Order
FKs, a persisted processing Account FK, positive finite whole-VND amount, original
versions, canonical bytes/digest, immutable proof, server timestamps and collection/
receipt snapshots. New parent `(id, order_id)` uniqueness enables the composite FKs.
Application server Clock supplies timestamps; none is accepted from the client.
UPDATE, DELETE and TRUNCATE triggers prevent modification, including cascade
truncation. No historical refunds are backfilled or source rows rewritten.

Fresh, receipt-baseline upgrade and Shipping-baseline upgrade pass on PostgreSQL
17.6. Tests preserve historical Order/Payment/ReturnRequest rows and prior Flyway
checksums, validate migration order and schema, verify no backfill, and repeat
migrate with zero pending work. Composite foreign keys reject foreign Payment or
ReturnRequest; Account FK, uniqueness, status and monetary checks reject invalid
rows. Append-only tests cover proof, canonical bytes, actor, amount, timestamp,
delete, truncate, cascade truncate and source deletion. Rolled-back inserts permit
the key to be reused.

## Idempotency

Namespace: `PAY02_MANUAL_COD_REFUND_V1`. Reused the existing canonicalizer and COD
guard rather than adding parallel implementations. Canonical schema 1 encodes the
schema integer, length-prefixed UTF-8 operation, Order ID, original Order version,
original ReturnRequest version, and length-prefixed normalized proof. SHA-256 and
full bytes are compared. Key is exact, nonblank and at most 255 Unicode code points;
proof uses `strip()` without changing case or Unicode representation. NUL and
malformed Unicode are rejected. The key is outside canonical payload equality.

The same key, payload and original ADMIN return the committed frozen result with
`replay=true`, before stale state/version guards. Current ADMIN authorization is
checked even for replay. Different ADMIN, payload, Order, original version or key
for an already refunded Order conflicts. Replay adds no refund/history/audit/event.
Rollback reserves no key. Generic COMPLETE_REFUND remains rejected.

## Concurrency and rollback evidence

`ManualCodRefundIT` uses separate real PostgreSQL connections/transactions, backend
PIDs, latches, bounded Future/latch waits, and actual `pg_blocking_pids` plus worker
SQL. Polling observes the lock condition; sleeps do not select a race winner.

- Seven refund schedules: same-ADMIN replay, winner rollback, different ADMIN,
  different proof, different Order, different-key commit and different-key rollback.
  Actual waiters block on `pg_advisory_xact_lock` or Order SQL. Main-connection reads
  see no ledger/Order/history/audit effects before the held transaction commits.
- Eight ADMIN revocation schedules: refund first / revocation first, role revoke /
  Account locking, first attempt / committed replay. Revocation waits behind retained
  authority, or the waiting refund/replay observes committed revocation and is denied.
- Six three-connection FK schedules cover processed_by, history.actor_id and
  audit.actor_id in both arrival orders. Existing HP Account PESSIMISTIC_WRITE emits
  PostgreSQL NO KEY UPDATE, compatible with FK KEY SHARE. Governance FOR UPDATE
  waits on the first holder and then the remaining holder, verified with actual
  blocker PIDs after each release. No owner-module SQL authority workaround was added.
- Five fault stages: after ledger insert, Order flush, history flush, audit flush,
  and before commit. Every stage restores the entire prior state, emits no event,
  and allows the same key to succeed afterward. JDBC and JPA atomicity is exercised.
- After-commit observer verifies committed evidence on another physical connection;
  rollback and replay emit no additional event. READ_COMMITTED is asserted in SQL.

The final refund run records 27 actual blocking observations across 21 concurrency
invocations. Logs include the observed PIDs and SQL category for each wait.

## Verification commands and exact results

Java: Eclipse Adoptium 21.0.12.1. PostgreSQL: Testcontainers `postgres:17.6`.
Docker Desktop required restarting the task-started sandboxed Desktop process outside
the sandbox; PostgreSQL runs required access to the Windows Docker engine pipe.

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
.\mvnw.cmd '-Ppostgres-it' '-Dit.test=ManualCodRefundIT,RefundLedgerMigrationIT,CodPaymentIT,ReturnManagementIT,ShipperFulfillmentIT,ShipperOrderLifecycleIT,ShipmentAttemptMigrationIT,ReturnReceiptMigrationIT' clean verify
.\mvnw.cmd '-Ppostgres-it' '-Dit.test=ManualCodRefundIT,RefundLedgerMigrationIT' test-compile failsafe:integration-test failsafe:verify
git diff --check
```

| Run | Tests | Failures / errors / skipped | Exit |
| --- | ---: | --- | ---: |
| Clean verify, all Surefire units (includes ArchitectureTest 7) | 597 | 0 / 0 / 0 | 0 |
| Same clean verify, eight selected PostgreSQL classes | 371 | 0 / 0 / 0 | 0 |
| Final refund rerun with added preservation/isolation assertions | 65 | 0 / 0 / 0 | 0 |
| git diff --check | n/a | PASS | 0 |
| New-file whitespace and applied-migration content checks | n/a | PASS | 0 |

Production code is unchanged between the clean run and final rerun; only a new
preservation test and isolation assertion were added. The final union is 597 unit
and 372 scoped PostgreSQL invocations. Do not add overlapping reruns to that total.

| PostgreSQL class | Final tests |
| --- | ---: |
| ShipperFulfillmentIT | 13 |
| ManualCodRefundIT | 62 |
| ReturnManagementIT | 149 |
| ReturnReceiptMigrationIT | 3 |
| ShipperOrderLifecycleIT | 92 |
| CodPaymentIT | 47 |
| RefundLedgerMigrationIT | 3 |
| ShipmentAttemptMigrationIT | 3 |

Full clean-build log: `$env:TEMP\pay02-phase2-clean-verify.log`.
Final refund log: `$env:TEMP\pay02-phase2-final-refund.log`.
Raw reports: `target/surefire-reports` and `target/failsafe-reports`.

Earlier development checks, superseded by the passing runs above:

| Command / attempt | Tests | Failures / errors / skipped | Exit | Resolution |
| --- | ---: | --- | ---: | --- |
| `.\mvnw.cmd '-DskipTests' test-compile` | n/a | n/a | 0 | Compiled |
| `.\mvnw.cmd '-Dtest=ArchitectureTest' test` | 7 | 0 / 0 / 0 | 0 | Architecture passed |
| `.\mvnw.cmd test` before HTTP fixture updates | 597 | 0 / 126 / 0 | 1 | Added two authority mocks in 19 test contexts |
| PAY-02 integration command inside sandbox | 4 | 0 / 4 / 0 | 1 | Docker pipe access; reran outside sandbox |
| First real PAY-02 integration run | 55 | 14 / 17 / 0 | 1 | Corrected target-spy stubbing and byte-array comparisons |
| Second real PAY-02 integration run | 56 | 3 / 0 / 0 | 1 | Observed FK/NO KEY UPDATE compatibility; replaced incorrect blocking assumption |
| Third real PAY-02 integration run | 59 | 3 / 0 / 0 | 1 | Observe/release the actual first blocker in both schedules |

The PAY-02 integration command in those attempts is the final command above with
`ManualCodRefundIT,RefundLedgerMigrationIT`; the growing counts reflect tests added
during implementation. Historical independent-slice documentation reported 155
focused units, 597 full units and 307 existing PostgreSQL regressions passing before
this phase. Those older results are not added to current totals or treated as refund
transaction verification.

Remaining diagnostics are existing Mockito dynamic-agent/CDS warnings, the Spring
AuthenticationProvider/UserDetailsService warning, existing ProductPromotionIT
unchecked compilation diagnostics, and expected SQL warnings from regression fault
injection. There are no failing or skipped tests in the reported passing scope.

## Exact working-tree delivery manifest

This includes the reused independent slice already present at task entry. Nineteen
HTTP fixture files add exactly the two new authority mocks; their prior assertions
are unchanged. ReturnReceiptMigrationIT includes the newly reserved migration in its
expected upgrade sequence. The unrelated pre-existing untracked `tatus --short` is
preserved and excluded from this implementation manifest.

43 source/documentation files differ from HEAD:

- `docs/PAY-02-implementation-status.md`
- `src/main/java/com/uteexpress/order/controller/AdminRefundController.java`
- `src/main/java/com/uteexpress/order/service/OrderLifecycleService.java`
- `src/main/java/com/uteexpress/order/service/OrderLifecycleServiceImpl.java`
- `src/main/java/com/uteexpress/order/service/OrderPlacementService.java`
- `src/main/java/com/uteexpress/order/service/RefundOrderAuthority.java`
- `src/main/java/com/uteexpress/payment/dto/CollectedCodRefund.java`
- `src/main/java/com/uteexpress/payment/dto/ManualRefundCommand.java`
- `src/main/java/com/uteexpress/payment/dto/ManualRefundRequest.java`
- `src/main/java/com/uteexpress/payment/dto/RefundResult.java`
- `src/main/java/com/uteexpress/payment/dto/RefundStatus.java`
- `src/main/java/com/uteexpress/payment/entity/RefundLedger.java`
- `src/main/java/com/uteexpress/payment/repository/RefundLedgerRepository.java`
- `src/main/java/com/uteexpress/payment/service/CodRefundEligibility.java`
- `src/main/java/com/uteexpress/payment/service/ManualCodRefundService.java`
- `src/main/java/com/uteexpress/payment/service/ManualRefundPayload.java`
- `src/main/resources/db/migration/V20261022090000__td_pay02_refund_ledger.sql`
- `src/test/java/com/uteexpress/FoundationHttpTest.java`
- `src/test/java/com/uteexpress/account/controller/AddressControllerTest.java`
- `src/test/java/com/uteexpress/account/controller/ProfileControllerTest.java`
- `src/test/java/com/uteexpress/catalog/controller/PublicCatalogControllerTest.java`
- `src/test/java/com/uteexpress/catalog/controller/VendorProductControllerTest.java`
- `src/test/java/com/uteexpress/governance/AccountGovernanceWebTest.java`
- `src/test/java/com/uteexpress/governance/CategoryWebTest.java`
- `src/test/java/com/uteexpress/governance/ModerationWebTest.java`
- `src/test/java/com/uteexpress/governance/OpsDashboardTest.java`
- `src/test/java/com/uteexpress/governance/ShopApprovalWebTest.java`
- `src/test/java/com/uteexpress/identity/controller/EmailVerificationControllerTest.java`
- `src/test/java/com/uteexpress/identity/controller/PasswordResetControllerTest.java`
- `src/test/java/com/uteexpress/identity/controller/RegistrationControllerTest.java`
- `src/test/java/com/uteexpress/order/ManualCodRefundIT.java`
- `src/test/java/com/uteexpress/order/ReturnReceiptMigrationIT.java`
- `src/test/java/com/uteexpress/order/service/RefundTransitionGuardTest.java`
- `src/test/java/com/uteexpress/payment/ManualRefundContractTest.java`
- `src/test/java/com/uteexpress/payment/RefundLedgerMigrationIT.java`
- `src/test/java/com/uteexpress/payment/service/CodRefundEligibilityTest.java`
- `src/test/java/com/uteexpress/payment/service/ManualRefundPayloadTest.java`
- `src/test/java/com/uteexpress/security/AuthenticationWebTest.java`
- `src/test/java/com/uteexpress/security/CsrfCookieWebTest.java`
- `src/test/java/com/uteexpress/security/SecurityFoundationTest.java`
- `src/test/java/com/uteexpress/shipping/ShippingConfigWebTest.java`
- `src/test/java/com/uteexpress/shop/controller/ShopRegistrationControllerTest.java`
- `src/test/java/com/uteexpress/ui/UiLayoutTest.java`

No commit, branch switch, merge, push or PR was performed.
