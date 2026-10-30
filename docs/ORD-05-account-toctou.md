# ORD-05 account eligibility TOCTOU regression

The former vulnerability witness has been replaced with assertions for the merged
Shipping security fix. Quoc Dat owns Shipping and Hoang Phuc owns Identity/governance.
This follow-up changes regression tests and documentation only.

## Implemented lock order

Fulfillment acquires `Order -> Shipment -> assigned Shipper Account -> Payment`.
`ShipmentFulfillmentService.assigned` locks the assigned `users` row using PostgreSQL
`FOR SHARE`, checks persisted ACTIVE status, then checks persisted SHIPPER membership.
The Shipping boundary uses MANDATORY propagation, so the shared account lock lasts
until the outer coordinator commits or rolls back, including after Order completion
returns. Different orders can share the same shipper account eligibility lock.

Account locking uses the Identity account write lock. Role governance locks the
global role row, then the account `FOR UPDATE`, before changing `user_roles` and
rotating the token. Both account locks conflict with fulfillment's `FOR SHARE`.
Fulfillment never locks the global role row: taking it after Account would invert
governance's role-before-account order. Governance must not acquire Order, Shipment
or Payment while holding its role/account locks. Assignment/reassignment keep their
existing Order-first ordering; no Account-before-Order hierarchy is introduced.

An audit actor foreign key only takes KEY SHARE and does not protect eligibility
against PostgreSQL NO KEY UPDATE. Token invalidation does not reauthenticate an
already-running request. The explicit Shipping FOR SHARE lock protects this boundary.

## Deterministic PostgreSQL regression schedules

`ShipperOrderLifecycleIT.fulfillmentAccountLockMakesAdminRestrictionWaitUntilOuterCommit`
reuses the former final-evidence pause, immediately before the real collected-COD
guard. Each parameter invokes a real administrative account lock or SHIPPER role
revocation in an independent transaction. `pg_blocking_pids` proves that governance
waits on the fulfillment transaction. The restriction cannot finish at the guard,
or after all fulfillment boundaries return while the outer transaction remains open.
Only after delivery commits may the restriction commit. Payment is PAID, both Order
and Shipment are DELIVERED, and exactly one delivery history, audit and after-commit
event exist. The final account is LOCKED or its persisted SHIPPER role is absent.

`ShipperOrderLifecycleIT.adminRestrictionCommitsBeforeEligibilityAndDeliveryFailsWithoutEffects`
first performs the real account lock or role revocation while holding the governance
transaction open. A competing delivery with the old SHIPPER principal takes Order
and Shipment and is observed blocked on the account lock. Governance commits first;
fulfillment then returns ACCESS_DENIED. Complete persisted row snapshots show unchanged
Order, Shipment, Payment, history, stock, Shipment audits, voucher usage and assignment
history, with no lifecycle event. ACTIVE status alone is insufficient after role revocation.

Synchronization uses latches, bounded futures, PostgreSQL blocker observations and
bounded worker lock timeouts. No assertion expects the old insecure schedule.

## Atomicity and duplicate-delivery evidence

The T32 regression rejects a wrong COD amount both before and after provisional
Shipment delivery. It checks complete row snapshots after each failure and after
duplicate collection. An injected failure after real Order completion observes PAID
Payment, DELIVERED Shipment/Order and provisional history/audit through JDBC in the
outer transaction, then verifies their complete rollback and absence of after-commit
events. A separate history persistence failure also rolls back collected COD and
Shipment delivery.

The voucher regression obtains a real locked VoucherApplication and records usage,
then creates a database-valid discount mismatch. It observes provisional Shipment
cancellation/audit and stock restoration before the real voucher release guard fails
with CONFLICT. Complete row snapshots and application events verify rollback; usage
remains REDEEMED. Current lifecycle events are after-commit events, not a persisted outbox.

The full delivery race holds the actual Order row until both delivery workers with
identical original versions and stale managed snapshots are observed blocked through
`pg_blocking_pids`. Exactly one delivery commits; the loser returns CONFLICT. Assertions
count successful collection, Shipment mutation and Order completion plus persisted
Payment, delivery audit and lifecycle history, preserving prior and unrelated evidence.

Public fulfillment coordinator/routes remain the separate SHIP-02 owner integration
described in `SHIP-02-ORD-05-boundary.md`. These regressions exercise the real internal
Order, Shipping, Payment and governance services in PostgreSQL transactions.
