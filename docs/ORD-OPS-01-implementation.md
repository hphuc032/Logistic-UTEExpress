# ORD-OPS-01: TD components, QD integration blocked

Baseline: clean `feature/ord-ops-01-implementation` at
`34f114c1b05e0b953d8f21f919824a1ab68f75a4`, matching live `origin/develop`
when checked on 2026-10-12. No commit, branch creation, push, merge or PR.

## Shipping blocker

There is no approved Ops Shipment cancellation hook in this baseline.
`ShipmentFulfillmentService.cancelAssignedForVendorOrder(Long)` requires VENDOR
and persisted shop ownership. Its authorization cannot serve system-wide Ops.
`lockAssignedForTransition` and `requireFulfillmentEvidence` require SHIPPER and
current assignment. Order does not call those methods for Ops and does not read
or update Shipping tables in production.

The production `cancelForOps` method locks Order and then returns `CONFLICT` at
the missing Shipment lock hook. This includes Orders that appear to have no
Shipment: Order cannot establish authoritative absence through an approved QD
boundary yet. It performs no inventory, voucher, Order, history, audit or Payment
write, and emits no event. Shipping integration is **BLOCKED**.

## Exact Order contract

```java
OrderStatusChangedEvent cancelForOps(OpsOrderCancellationCommand command);

record OpsOrderCancellationCommand(
    Long orderId,
    OrderStatus expectedOrderStatus,
    Long expectedOrderVersion,
    OpsShipmentExpectation expectedShipment,
    Long expectedShipmentVersion,
    OpsCancellationReason reasonCode) {}
```

`EXPECTED_ABSENT` requires a null Shipment version and conflicts if any Shipment
exists after locking. `EXPECTED_PRESENT` requires a nonnegative Shipment version
and conflicts if the Shipment is missing or its locked persisted version differs.
Malformed command combinations return `INVALID_REQUEST`.

The command contains no actor, role, shop, target status, generic action, stock
quantity, Payment instruction or free text. Identity comes from SEC-01. The
single minimal reason `UNABLE_TO_FULFILL` produces the server-defined description
`Ops cancellation: unable to fulfill`. **The enum name is provisional for HP
review.** The Order audit action is `ORDER_OPS_CANCELLED`, target type `ORDER`,
with the fixed enum name as reason and before/after status and version only.

## TD implementation and lock order

The new entry point refuses an already active transaction with `INVALID_REQUEST`
before acquiring any resource. ADMIN-07 must call it without an outer transaction.
It neither joins nor suspends a caller's Account-owning transaction. Existing
Vendor and Shipper composition contracts are unchanged by this slice.

Inside the lifecycle transaction the implemented sequence is:

1. Authenticate and check trusted ADMIN/MANAGER authorities without locking.
2. Lock and refresh Order with `findByIdForUpdate`/`PESSIMISTIC_WRITE`.
3. Lock/read Shipment through the pending QD hook. Production stops here today.
4. Lock the SEC-01 persisted Account through
   `AccountIdentityService.requireActiveAccountForUpdate`; require ACTIVE.
5. Read persisted roles through `RoleGovernanceService.rolesFor`; require ADMIN
   or MANAGER. Retain the Account lock through commit. Governance role changes
   lock their target Account before changing role membership, so revocation
   cannot commit between this check and lifecycle commit. Ops never takes a
   global Role lock after Account.
6. Check original Order status/version, explicit Shipment expectation/version,
   and state/evidence. Only NEW without Shipment, CONFIRMED without Shipment,
   or CONFIRMED with clean ASSIGNED qualify. Reject all terminal/later states,
   inventory release/cancellation/delivery evidence, and NEW with ready evidence.
   Existing Shipping assignment requires CONFIRMED and ready_at; missing ready
   evidence on an ASSIGNED Order is inconsistent. Require zero attempts,
   max_attempts=2, no pickup/delivery timestamp and valid persisted resource IDs.
7. Resolve the clean assignment through the pending QD mutation hook.
8. Restore server-snapshot quantities using `InventoryService.restore`, whose
   implementation locks Products in ascending ID order. Set the once-only marker.
9. Release Voucher -> VoucherUsage through `VoucherService.releaseForCancellation`.
   Ops scope is system-wide; no vendor-owned Shop eligibility is needed for these
   effects. No Shop lock is taken by the Ops path.
10. Use `OrderTransitionPolicy` with server-selected CANCEL_NEW/CANCEL_CONFIRMED,
    then the existing sole lifecycle writer for CANCELLED, timestamps and history.
11. Append the Order audit in the same transaction. A failure propagates and rolls
    back all provisional effects. The existing lifecycle event synchronization
    publishes only after commit.

Payment is never called, locked or modified. Historical item, address, money,
shipping selection, commission and voucher snapshots are not rebuilt or changed.

The TD guards/effects are internal `OpsOrderAuthority` components. They are not a
second public cancellation API. Neither missing Shipping hook has a production
adapter or fallback, and both default to conflict. Integrating just the read hook
will still fail closed at the resolution hook.

## Required QD hook proposal

These signatures are a **proposal for QD review**, not an existing or approved
Shipping API. They avoid a Shipping -> Order dependency or module cycle:

```java
// Shipping-owned service; MANDATORY; caller already holds Order first.
Optional<ShipmentFulfillmentFacts> lockForOpsCancellation(Long orderId);

// Shipping-owned service; MANDATORY; called after persisted Ops eligibility.
void cancelLockedForOpsOrder(Long orderId, Long expectedShipmentVersion);
```

The first method must lock/read the Shipment, return authoritative absence or
complete persisted physical evidence, and acquire no Account/Role/Product lock.
All assignment/reassignment writers must retain Order as the first lock so that
absence stays serialized until commit. It must never apply vendor ownership or
shipper-assignment authorization to Ops. If existing facts do not represent all
conflicting physical evidence, QD must extend its DTO and guard before integration.

The second method must recheck the already locked clean ASSIGNED row and exact
original version; reject pickup/delivery/attempt/conflicting evidence; cancel and
increment Shipment version exactly once; and append Shipping-owned audit in the
same transaction. It must not mutate Order, inventory, voucher or Payment. Resolve
identity through SEC-01 and retain the approved ADMIN/MANAGER persisted eligibility;
do not acquire a global Role lock after the already-held Ops Account. Neither hook
may start an independent transaction or call back into Order lifecycle. TD will
adapt these Shipping service/DTO contracts inside the lifecycle once QD provides
them. Absence resolution needs no Shipment mutation, after the first hook has
established absence under the Order lock.

## Verification scope

`OpsOrderCancellationIT` uses real PostgreSQL and production TD components, with
an explicitly named `FixtureLifecycle` test double for the unavailable Shipping
lock/cancel operations. That fixture is never a Spring production bean and its
Shipment writes do not certify QD cancellation authorization, physical evidence
coverage or Shipping audit. Production fail-closed behavior is tested separately.

Coverage includes ADMIN/MANAGER, USER/VENDOR/SHIPPER denial, unauthenticated and
inactive accounts, persisted role revocation despite stale authenticated claims,
outer-transaction rejection, malformed expectations, eligible states, stale Order
status/version, absent/present/version conflicts, physical/inconsistent evidence,
terminal states, duplicate cancellation, inventory/voucher once-only effects,
Payment/snapshot preservation, history/Order audit atomicity and rollback after
stock, voucher and flushed-audit failures. Event assertions check commit/rollback.

Ten deterministic race invocations cover both directions against actual Vendor
cancellation, Shipper pickup, assignment, reassignment and Vendor confirmation.
The Ops side is the TD fixture, so these are **component concurrency tests**.
Separate transactions, latches, bounded waits, backend PIDs and actual
`pg_blocking_pids`/`pg_stat_activity` statements establish the Order wait before
release. Unexpected SQL exceptions, 40P01 or lock timeouts fail the tests.

Required follow-up: integrate QD's approved hooks and run the same behavior/race
matrix against the production Ops bean, add QD Shipment audit/physical-evidence
assertions, then run full Java 21 PostgreSQL `clean verify`. That release gate is
deferred while the required hook is missing. This slice is for HP/QD review and
is not a completed end-to-end Ops cancellation feature.

## Fresh verification result (2026-10-12)

Java 21.0.12.1, PostgreSQL 17.6 through Testcontainers; Maven exit 0,
`BUILD SUCCESS`:

```powershell
.\mvnw.cmd -Ppostgres-it -Dtest=ArchitectureTest '-Dit.test=OpsOrderCancellationIT,VendorOrderIT,ShipperOrderLifecycleIT,ShipmentAssignmentIT,ShipmentFulfillmentIT,ShipperFulfillmentIT,RoleGovernanceIT' verify
```

| Suite | Tests | Failures | Errors | Skips |
| --- | ---: | ---: | ---: | ---: |
| ArchitectureTest (Surefire) | 7 | 0 | 0 | 0 |
| OpsOrderCancellationIT (TD fixture) | 48 | 0 | 0 | 0 |
| VendorOrderIT | 73 | 0 | 0 | 0 |
| ShipperOrderLifecycleIT | 48 | 0 | 0 | 0 |
| ShipmentAssignmentIT | 6 | 0 | 0 | 0 |
| ShipmentFulfillmentIT | 6 | 0 | 0 | 0 |
| ShipperFulfillmentIT | 6 | 0 | 0 | 0 |
| RoleGovernanceIT | 6 | 0 | 0 | 0 |
| **Total** | **200** | **0** | **0** | **0** |

Surefire: 7; Failsafe: 193. Fresh XML reports are in `target/surefire-reports`
and `target/failsafe-reports`; captured Maven log is
`$env:TEMP\ordops-implementation-focused.log`. The log records actual Order
blockers for all ten TD fixture race schedules. Existing multi-role regressions
passed; no deadlock or unexpected lock timeout occurred. Expected fault-injection
constraint errors in existing rollback tests do not represent failed tests.
`git diff --check` passed. Full PostgreSQL `clean verify` was not run because QD's
required production hook is absent.

Changed files: three new Order DTOs (`OpsCancellationReason`,
`OpsShipmentExpectation`, `OpsOrderCancellationCommand`), new internal
`OpsOrderAuthority`, the existing lifecycle interface/implementation and production
`OrderPlacementService`, new `OpsOrderCancellationIT`, and this report. No QD
production, ADMIN-07 controller, governance policy or Payment code was modified.
