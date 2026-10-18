# SHIP-03 Order/Shipping integration validation

Date: 2026-10-18. Branch: `feature/ship-03-integration`. Base merge: `869369c`.
This report describes local, uncommitted integration changes on that base.

## Integrated boundary

`OrderPlacementService` retains SHIPPER authorization and MANDATORY transactions.
Its shipper authority locks and refreshes Order, checks SHIPPING and the original
Order version/lifecycle flags, then calls the existing QD-owned
`ShipmentAttemptService.lockForOperation` or `requireOperationEvidence` with the
original Shipment version. QD retains Shipment then Account eligibility locks.
The current account provider used before these locks only reads the trusted
security principal; it does not acquire an Account database lock.

Order validates the returned persisted Shipment identity, assignment, version,
state, attempt/max-attempts and pickup/no-delivery facts. Verify requires exactly
source Shipment version + 1. Failure evidence must match Shipment, actor, attempt,
source version, pickup chronology and the agreed reason/reference bounds. Retry
requires attempt-1 evidence with its source version preceding the retry source
token; target retry is SHIPPING/attempt 2. QD's hooks additionally validate retained
first-failure evidence for second-attempt failure and absence of duplicate current
failure evidence. Retry from failed attempt 2 conflicts.

The existing fresh-transaction coordinator runs prepare -> mutation -> verify.
Neither Order boundary writes Order, appends history, emits an event or invokes
Payment, inventory, voucher, refund or return effects. No QD-owned production API,
DTO, enum, controller, coordinator or migration was changed. No contract mismatch
requiring a QD API change was found.

## Executed checks

JDK 21.0.12.1, Maven Wrapper 3.9.11, PostgreSQL 17.6 via Testcontainers.

```powershell
.\mvnw.cmd '-Dtest=ShipmentOnlyOrderGuardTest,ShipmentOnlyOperationGuardTest,OrderContractTest,ArchitectureTest' test
.\mvnw.cmd '-Ppostgres-it' '-Dit.test=ShipperOrderLifecycleIT,ShipperFulfillmentIT,ShipmentFulfillmentIT,ShipmentAttemptMigrationIT' verify
git diff --check
```

| Check | Passed | Failed/errors/skipped | Exit |
| --- | ---: | --- | ---: |
| Focused unit/contract/architecture | 91 | 0/0/0 | 0 |
| All unit tests during verify | 504 | 0/0/0 | 0 |
| ShipperOrderLifecycleIT | 92 | 0/0/0 | 0 |
| ShipperFulfillmentIT | 9 | 0/0/0 | 0 |
| ShipmentFulfillmentIT | 23 | 0/0/0 | 0 |
| ShipmentAttemptMigrationIT | 2 | 0/0/0 | 0 |
| git diff --check | n/a | n/a | 0 |

The PostgreSQL total is 126. The 91 focused tests are included in the 504 unit
tests; they are not additional unique tests. The verify run passed 630 unique test
cases across the unit suite and these four integration classes. This was a targeted
PostgreSQL verify, not the entire PostgreSQL integration suite.

Logs: `.work-ship03-integration-unit.log` and
`.work-ship03-integration-postgres.log`; machine reports are in
`target/surefire-reports` and `target/failsafe-reports`.
The failsafe directory also contains reports from earlier runs; the 126 count
above comes from this invocation and its four selected class reports.

Coverage includes real coordinator first failure -> retry -> second failure,
third-retry rejection, HTTP JSON failure/form retry, unchanged persisted
Order/history/Payment/product/voucher/return/assignment snapshots and no Order
events, original-token repeatable verify, stale/duplicate/wrong-shipper requests,
persisted malformed/missing evidence, rollback of real mutation/evidence/audit on
verify failure, and coordinator rollback on audit failure. Existing attempt-2 COD
success/wrong-amount rollback tests now reach attempt 2 through real failure/retry
instead of test-only SQL.

New deterministic races cover duplicate failure/retry, failure versus delivery in
both schedules, and governance Account restriction/SHIPPER revocation in both
schedules for both operations with an ADMIN+VENDOR+SHIPPER actor. They use separate
transactions, bounded waits, backend PIDs, actual worker SQL and pg_blocking_pids;
SQL deadlocks/timeouts fail the tests. Existing SHIP-02 lifecycle regressions also
passed in the targeted classes.

Remaining log warnings are the existing unchecked operations in
`ProductPromotionIT`, Mockito/Byte Buddy dynamic agent/CDS messages and the
existing Spring Security AuthenticationProvider/UserDetailsService configuration
warning. Expected negative-test database errors include deliberately rejected
audit/history rows and immutable-evidence/constraint checks; no tests failed.

No commit, push, branch switch, merge or PR was performed.
