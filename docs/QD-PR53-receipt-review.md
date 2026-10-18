# QD review — PR #53 RETURN-01 Phase 2

Date: 2026-10-18. Reviewed PR head d2870ce24bab9d1440c23d8a8941f8da8249757c against develop 85352d9. Technical review scope: migration, DB constraints, lock ordering, concurrency and rollback. No TD source edits, GitHub approval submission or merge performed.

Outcome: no blocking finding in this scope at the reviewed head. HP's latest receipt-only contract supersedes the earlier GOOD/DAMAGED restock proposal: receipt is not inspection, restockable stays NULL, and neither refund nor stock/Payment/Shipment mutation is enabled. This review does not approve future restocking or refund policy implementation.

## Schema and evidence

V20261018090000 is later than merged Phase 1 V20261013090000. Phase 1 migration is unchanged. The new migration adds received_by/receipt_reference, replaces receipt/time checks, retains unique Order/ReturnRequest association and enforces all-or-none receipt fields, APPROVED evidence, nonblank bounded reference, FK actor, receipt >= decision timestamp and updated_at equality. restockable remains NULL by check.

A BEFORE UPDATE trigger protects successful receipt's actor/time/reference/Order/ID against overwriting, clearing and moving through ordinary direct SQL updates. Runtime authority additionally requires fresh expected Order and ReturnRequest versions and absent prior receipt. A global unique receipt_reference is not required by this contract: per-Order uniqueness is enforced by existing unique(order_id), and a handover document can legitimately cover multiple Orders. Vendor attestation is the agreed evidence source; this is not independent carrier/document verification.

Receipt migration tests cover fresh and Phase 1 upgrade, preserve existing statuses and original migration checksum, validate FK/evidence checks and immutable updates, then verify repeat migration is a no-op. Notification migration remains compatible with the added receipt migration.

## Transactions, locks and effects

Dedicated receiveReturn validates input and rejects existing outer transactions, then opens its own lifecycle transaction. The existing Order is locked/refreshed before ReturnRequest, then persisted ACTIVE/VENDOR Account and approved owned Shop. No Account/Shop lock is acquired before waiting for an existing Order; no new global Role lock edge is introduced. Shipment and Payment are not part of this receipt path.

Only RETURN_REQUESTED plus same-Order APPROVED ReturnRequest and both expected versions can receive. Server records actor/time; lifecycle changes Order to RETURNED while ReturnRequest stays APPROVED and restockable stays NULL. Generic lifecycle transition cannot bypass the dedicated receipt boundary. Effects/history and after-commit notification hint follow the existing lifecycle; receipt reference is not copied into general status-event reason.

The tests verify no change to Payment/Shipment/inventory/voucher side effects, one receipt/history/event on duplicate/concurrent requests, rollback of receipt/history/beforeCommit failure, a waiting retry succeeding if first writer rolls back, waiting on actual ReturnRequest lock/version refresh, and Account/Shop/role restriction in both lock acquisition schedules. Lock waits are demonstrated using PostgreSQL blocking evidence, not sleeps alone. Role-revocation concurrency uses test SQL under the owning Account lock; it establishes the lock protocol and is not a claim that a new production VENDOR-revocation endpoint was implemented.

## Independent verification

Java 21, real PostgreSQL 17.6 Testcontainers:

```powershell
.\mvnw.cmd -q -Ppostgres-it '-Dtest=ArchitectureTest' '-Dit.test=ReturnManagementIT,ReturnReceiptMigrationIT,NotificationMigrationIT' clean verify
```

PASS, exit 0: 159 tests = 7 architecture + 149 ReturnManagementIT + 2 ReturnReceiptMigrationIT + 1 NotificationMigrationIT, zero failures/errors/skips, Failsafe timeout=false. Flyway validated 24 migrations and repeat upgrade was a no-op. Log is local ignored pr53-qd-review.log in the review worktree. This is QD focused verification, not an independent full 1,351-test rerun. TD's full verification remains author-reported evidence in PR #53.

Read-only diff checks confirm merged Phase 1 migration and Shipping/Payment production source unchanged. git diff --check passed; reviewed checkout clean. GitHub exact-head Maven test/package CI was successful and behind=0 at review time. Recheck head/base and required reviews before merge; a later source change needs review of that delta.

## Remaining QD dependency

SHIP-03 actual failure/retry still lacks an approved trusted Shipping-only Order guard, final failure evidence/endpoint and role-revocation protocol. Existing attempt-2 compatibility is merged but does not implement failure/retry. QD must not fabricate guard evidence or write Order/Payment/inventory while TD implements RETURN-01 Phase 2 / PAY-02.
