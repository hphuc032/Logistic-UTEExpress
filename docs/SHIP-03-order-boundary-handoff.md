# SHIP-03 TD Order boundary: partial implementation and QD dependencies

Contract: `git show 78cd7d7:docs/SHIP-03-interface-protocol.md`.
Implementation branch: `feature/ship-03-order-boundary`, based on `85352d9`.

## Current behavior

The `OrderLifecycleService` methods have the approved signatures and use
`shipping.dto.DeliveryAttemptOperation` (`REPORT_DELIVERY_FAILURE`, `RETRY_DELIVERY`).
The production lifecycle bean applies `MANDATORY` and `SHIPPER` to both methods.
It requires a trusted current account ID, validates positive Order ID, non-null known
operation and both original version tokens (neither negative nor `Long.MAX_VALUE`).
It acquires the Order pessimistic write lock, refreshes its managed state under that
lock, then checks `SHIPPING`, the original Order version, a present `ready_at`, and
absence of delivery/cancellation/inventory-release facts. Verify repeats these Order
checks; it does not accept an incremented Order token after an Order change.

**Both public methods remain blocked with `CONFLICT` even for eligible Orders.**
There is no successful prepare or verify path while the QD hooks are absent. This is
an incomplete integration, not successful Shipment/evidence verification. The
existing SHIP-02 APIs cannot prove SHIP-03 failure/retry evidence and are not reused.
No placeholder Shipping service, fabricated facts or alternate evidence query is
introduced. Adding QD's service later also requires explicit TD wiring and tests;
the current rejection does not automatically disappear when that service exists.

Neither method saves Order, changes status/version/timestamps, appends Order history,
registers an after-commit event, calls Payment, or adds an `OrderAction` self-transition.
Shipment mutations, evidence persistence, migrations, coordinators and controllers
are unchanged. The only new Shipping production type is the agreed operation enum.

## Required QD handoff

Supply the approved `ShipmentAttemptService` read/lock methods:

```java
ShipmentDeliveryFacts lockForOperation(Long orderId, Long expectedShipmentVersion,
    DeliveryAttemptOperation operation);
ShipmentDeliveryFacts requireOperationEvidence(Long orderId, Long originalShipmentVersion,
    DeliveryAttemptOperation operation);
```

Supply `shipping.dto.ShipmentDeliveryFacts` wrapping the existing
`ShipmentFulfillmentFacts` and relevant immutable `DeliveryFailureEvidence` snapshot.
TD has not created speculative versions of those DTOs or duplicate hook interfaces.
The hooks must reload actual persisted Shipment/evidence, validate current assignment
and persisted ACTIVE/SHIPPER eligibility, and retain locks in Order -> Shipment ->
Account order through transaction completion. The current principal/identity checks
do not establish those persisted Shipping authorization facts.

Pending source checks include the actual expected Shipment version, attempt/maxAttempts,
pickup/no-delivery facts, absence of duplicate failure evidence, and valid attempt-1
evidence for retry. Pending target checks include original Shipment version + 1,
failure status with unchanged source attempt, retry status SHIPPING at attempt 2,
evidence identity/Shipment/attempt/actor/source-version/server-time/reason/reference,
retained attempt-1 failure evidence and absence of attempt-2 failure on retry.
These checks must be wired and verified before either Order method can succeed.

The QD coordinator must reject an existing outer transaction before opening a fresh
one, then call prepare -> Shipping mutation -> verify using the original tokens.
`MANDATORY` alone cannot detect an Account lock already held by an outer caller.
No coordinator is implemented by this TD slice.

## Verification scope

`ShipmentOnlyOrderGuardTest` independently exercises Order token/lifecycle guards,
lock-before-refresh-before-facts ordering, and stale managed snapshots. Its successful
guard calls are not successful public boundary calls and do not mock Shipping authority.
`ShipperOrderLifecycleIT` exercises both public methods for transaction/role/identity
rejection and repeated missing-hook rejection, comparing persisted state and Order
events. Existing shipper lifecycle regressions remain in that targeted class.

Successful failure/retry, persisted evidence immutability, duplicate/stale Shipment
rollback, actor-revocation/multi-role schedules for the new operations, and attempt-2
COD delivery/wrong-amount rollback remain integration acceptance work after QD handoff.

Validation on 2026-10-18 with JDK 21 / Maven wrapper:

- `./mvnw.cmd '-Dtest=ShipmentOnlyOrderGuardTest,OrderContractTest,ArchitectureTest' test`:
  exit 0; 51 tests, including 41 independent Order guard cases; no failures/errors/skips.
- `./mvnw.cmd '-Ppostgres-it' '-Dit.test=ShipperOrderLifecycleIT' verify`:
  exit 0; all 464 unit tests and 60 targeted PostgreSQL 17.6 integration cases passed,
  with no failures/errors/skips. This is not the full PostgreSQL integration suite.
- `git diff --check`: exit 0.

Remaining log warnings: existing unchecked operations in `ProductPromotionIT` during
test compilation, Mockito/Byte Buddy dynamic-agent/CDS warnings, and the Spring Security
AuthenticationProvider/UserDetailsService configuration warning. The targeted IT also
logs the deliberate `ord05_test_history` constraint violation from an existing rollback
test; it is expected test evidence, not a failing SHIP-03 mutation.
