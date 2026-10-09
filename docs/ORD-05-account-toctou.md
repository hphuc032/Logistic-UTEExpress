# ORD-05 account eligibility race: owner handoff

Status: reproduced; unresolved. Quoc Dat owns Shipping and Hoang Phuc owns Identity/governance.
This ORD-05 follow-up changes tests and documentation only. It does not change either owner's production code.

## Exact interleaving

`ShipperOrderLifecycleIT.accountLockCommittedAfterFinalEvidenceCheckStillAllowsDelivery`
uses the real prepare, COD collection, Shipment delivery, Order completion and administrative
account-lock services. A test-only spy pauses the collected-COD guard, immediately after
completion has obtained persisted Shipment evidence and checked the assigned account.
Latches and bounded waits let the administrative transaction commit before completion resumes.

1. Delivery holds Order, Shipment and Payment locks. `ShipmentFulfillmentService.assigned`
   reads an ACTIVE account with the SHIPPER role using an unlocked `EXISTS` query.
   `requireFulfillmentEvidence` returns the persisted DELIVERED Shipment at original version + 1.
2. Before `ShipperOrderAuthority` finishes its collected-COD guard and writes Order DELIVERED,
   a separate ADMIN calls `AccountGovernanceService.setLocked` for the assigned shipper.
   `IdentityAccountGovernanceService.setLocked` locks the User with JPA PESSIMISTIC_WRITE,
   persists LOCKED, increments token version and commits its account audit.
3. Delivery resumes with its existing authenticated principal and commits PAID Payment,
   DELIVERED Shipment, DELIVERED Order, Shipment audit, lifecycle history and after-commit event.
   No account eligibility check or protecting account lock spans the final check through commit.

With the current Hibernate PostgreSQL dialect, PESSIMISTIC_WRITE on User uses
`FOR NO KEY UPDATE`. Shipment audit actor foreign keys take KEY SHARE, which permits
that account status update. An audit foreign key therefore does not serialize eligibility.
Token invalidation also does not reauthenticate an already-running request.
This witness concerns account locking; it does not assume role revocation has identical lock behavior.

The characterization test deliberately asserts this observed insecure outcome so the existing
full verification remains runnable. A passing witness means the race is present, not fixed.
After the owner fix, replace that expectation with the agreed serialized eligibility outcome.

## Lock-order-safe proposal for Quoc Dat and Hoang Phuc

Agree on a transaction-wide eligibility guard before acquiring Order. Identity/governance
should expose a MANDATORY internal service returning scalar facts, taking a shared lock on
the SHIPPER eligibility/role guard and a User lock that conflicts with account status changes
(for example PostgreSQL FOR SHARE, not FOR KEY SHARE). Validate persisted ACTIVE status and
SHIPPER membership under those locks, using the server-resolved actor. Hold them until the
outer fulfillment transaction completes. Expose no Identity entities or repositories.
Enforce guard ownership at the internal boundaries too: prepare obtains it before its
first Order lock; complete requires that transaction's already-held guard and revalidates
assignment under Shipment lock. A direct completion caller must establish the same guard
before taking any Order lock. Use a server-owned transaction resource to recognize a held
guard, never a caller-created DTO or principal claim. Coordinator discipline alone must not
leave an unguarded internal entry point.

Adopt the same hierarchy in fulfillment **and assignment/reassignment**:

`role eligibility guard -> Account -> Order -> Shipment -> Payment`

When multiple role guards/accounts are required, lock each set in a defined sorted order.
Governance mutations must use the same role-before-account hierarchy. Hoang Phuc should
verify the existing ADMIN guard and SHIPPER grant/revoke paths against it. Quoc Dat should
move assignment/reassignment eligibility locking before Order/Shipment and arrange for the
future fulfillment coordinator to obtain the guard before ORD-05 prepare. Keep Order before
Shipment before Payment, original-version checks, and persisted assignment revalidation.

Simply adding an Account lock in ORD-05 prepare before Order is unsafe with today's assignment
path: assignment takes Order (and Shipment on reassignment) before its SHIPPER role and Account
locks. Concurrent fulfillment could hold Account while waiting for Order, with assignment
holding Order while waiting for Account. Adding a late Account lock after Shipment/Payment
also violates the proposed hierarchy. These paths must change together through owner services;
Order must not reach into Identity or Shipping repositories or create a reverse dependency.

Acceptance tests should cover both schedules: an account restriction committed before the
eligibility guard causes ACCESS_DENIED with no fulfillment changes; fulfillment holding the
guard makes restriction wait until fulfillment commits or rolls back. Also cover role
revocation, assignment/reassignment, cancellation and failure rollback using bounded lock
observations. There must be no committed fulfillment performed after a prior committed
restriction has invalidated the guarded identity. Delivery already serialized before a
restriction may complete first.

## Rollback and concurrency evidence in this follow-up

The voucher test obtains a real locked VoucherApplication, records its usage, confirms and
assigns the Order, then persists a database-valid discount mismatch. It observes provisional
Shipment cancellation/audit and stock restoration before invoking the real voucher release
guard, which returns CONFLICT. Complete persisted row snapshots and application events verify
rollback. Current lifecycle events are published after commit; there is no persisted outbox.

The full delivery race holds the actual Order row until both workers, with identical original
versions and stale managed snapshots, are observed blocked through `pg_blocking_pids`.
After releasing the row, exactly one complete delivery commits and the other returns CONFLICT.
Assertions count real successful collection/Shipment/completion calls and persisted payment,
delivery audit and lifecycle history, while preserving stock, usage, assignment history and
prior lifecycle/audit records. Synchronization has bounded waits and no sleep-based races.
