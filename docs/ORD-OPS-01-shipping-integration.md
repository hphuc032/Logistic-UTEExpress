# ORD-OPS-01 production Shipping integration

Integration branch: `verify/ord-ops-01-shipping-integration`.
Starting HEAD: `243994cb89b8d1cf4d25340d5bdad82003918f9f`, with a clean worktree.
Both TD `3112842` and QD `06849b2` are ancestors of this HEAD.
This work is uncommitted; no branch switch, commit, push, PR or merge was performed.

The original reviewed reports [ORD-OPS-01-implementation.md](ORD-OPS-01-implementation.md)
and [SHIP-OPS-01-hooks.md](SHIP-OPS-01-hooks.md) were inspected. This report supersedes
the earlier missing-hook/component-fixture status for this integration worktree.
The original documents retain their historical baseline and verification results.

## Production integration

`OrderPlacementService`, the existing sole production lifecycle bean, now delegates
its Ops lock/read step to `ShipmentFulfillmentService.lockForOpsCancellation`.
After persisted eligibility and Order/Shipment expectation checks, it delegates
resolution to `cancelLockedForOpsOrder`, supplying the original locked Shipment
version, or null for authoritative absence. Null is never inferred from an
unlocked Order-side query. Both hooks run through the existing Spring Shipping
proxy and retain QD's class-level `MANDATORY` propagation and authority guards.

`cancelForOps` continues to reject an active outer transaction before acquiring
resources and uses its own lifecycle `TransactionTemplate`. Its lock sequence is:

1. Order, locked and refreshed.
2. Shipment, or authoritative absence serialized by the retained Order lock.
3. Persisted ACTIVE Ops Account and ADMIN/MANAGER eligibility.
4. Recheck Order status/version and explicit Shipment absence/presence/version;
   resolve the already locked Shipment through Shipping.
5. Products in ascending ID order through `InventoryService.restore`.
6. Voucher, then VoucherUsage through `VoucherService.releaseForCancellation`.

The Shipping mutation's second Shipment read reuses a lock already held by the
same transaction. Shipping hooks acquire no application Account/Role/Product lock
and write no Order. Persisted eligibility and the retained Account lock remain in
the Order orchestrator; no global Role lock is introduced after that Account.

Only NEW without Shipment, CONFIRMED without Shipment, and CONFIRMED with ready
evidence and a clean ASSIGNED Shipment qualify. Pickup/delivery evidence, attempts,
terminal/later states, stale expectations and inconsistent Order evidence conflict.
Shipping cancels/version-increments once and appends its own fixed-reason audit.
Inventory/voucher effects, Order state/history/audit and Shipping effects share
one transaction. Order events remain after-commit only. Payment and persisted
Order/item/Shipping snapshots are preserved.

No Shipping production, HTTP/Admin UI, migration, Payment or governance policy
code was changed. The generic lifecycle core still denies unwired workflows by
default; the production Ops bean now supplies both real Shipping operations.

## Production E2E and concurrency coverage

`OpsOrderCancellationIT` no longer constructs `FixtureLifecycle` or implements
test-owned Shipment reads/mutations. Every Ops cancellation uses the Spring
production Order bean. Spies call real methods to record hook invocations, pause
at transaction boundaries and inject failures; they do not substitute Shipping
behavior in success or race tests. Assignment, reassignment, pickup, Vendor
cancellation and confirmation use their actual production services too. The
contender harness does not prelock Order on behalf of those services.

The 60 cases cover roles/identity, persisted revocation/inactive eligibility,
outer-transaction rejection, malformed expectations, absent/present/version
conflicts, allowed states, physical and inconsistent evidence, duplicates,
once-only stock/voucher restoration, Payment and snapshot preservation, history,
both module audits and after-commit events.

Twelve deterministic schedules use separate transactions, bounded latches/futures,
distinct backend PIDs and a separate PostgreSQL observer. Every schedule requires
`wait_event_type=Lock`, the holder in `pg_blocking_pids(waiter)`, and the actual
production Order-lock statement in `pg_stat_activity` before releasing the holder.

| Contender | Ops commits first | Contender commits first |
| --- | --- | --- |
| Vendor cancel NEW, no Shipment | Vendor CONFLICT; Ops cancellation persists | Ops CONFLICT; Vendor cancellation persists |
| Vendor cancel CONFIRMED, assigned Shipment | Vendor CONFLICT; Ops cancels Order/Shipment | Ops CONFLICT; Vendor cancels Order/Shipment |
| Shipper pickup | Pickup CONFLICT; Ops cancels Order/Shipment | Ops CONFLICT; PICKED_UP Order/Shipment persist |
| Assignment | Assignment CONFLICT; Order CANCELLED, Shipment remains absent | Ops CONFLICT; ASSIGNED Shipment persists |
| Reassignment | Reassignment CONFLICT; original assignment is cancelled | Ops CONFLICT; new shipper and incremented Shipment version persist |
| Vendor confirmation | Confirmation CONFLICT; NEW Order cancelled | Ops CONFLICT; Order CONFIRMED persists |

All races check exact stock, voucher status, Order/Shipment status/version,
Order history/audit, Shipment audit/assignment-history counts, Payment rows and
immutable Order/item snapshots. The loser cannot contribute partial effects.
Unexpected SQL exceptions, deadlocks or lock/statement timeouts fail the tests.

Three Shipping failure cases exercise read-hook failure after the real read,
cancel-hook failure after the real Shipment mutation/audit, and a real PostgreSQL
constraint failure while Shipping appends its audit. Three further rollback cases
fail inventory restoration, voucher release, or the flushed Order/history/audit
step. Each compares complete before/after Order/items, Shipment, inventory,
VoucherUsage, Payment, both audits and assignment history, and requires no event.

## Fresh verification, 2026-10-13

Java `21.0.12.1`; PostgreSQL `17.6` through Testcontainers. Docker sandbox access
initially blocked container startup; rerunning outside the sandbox resolved it.
That initial infrastructure failure is not counted as a passing test run.

Focused command (Maven exit 0, BUILD SUCCESS):

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
.\mvnw.cmd -Ppostgres-it -Dtest=ArchitectureTest '-Dit.test=OpsOrderCancellationIT,VendorOrderIT,ShipperOrderLifecycleIT,ShipmentAssignmentIT,ShipmentFulfillmentIT,ShipperFulfillmentIT,RoleGovernanceIT' verify
```

| Suite | Tests | Failures | Errors | Skips |
| --- | ---: | ---: | ---: | ---: |
| ArchitectureTest (Surefire) | 7 | 0 | 0 | 0 |
| OpsOrderCancellationIT | 60 | 0 | 0 | 0 |
| VendorOrderIT | 73 | 0 | 0 | 0 |
| ShipperOrderLifecycleIT | 48 | 0 | 0 | 0 |
| ShipmentAssignmentIT | 6 | 0 | 0 | 0 |
| ShipmentFulfillmentIT | 18 | 0 | 0 | 0 |
| ShipperFulfillmentIT | 6 | 0 | 0 | 0 |
| RoleGovernanceIT | 6 | 0 | 0 | 0 |
| **Total** | **224** | **0** | **0** | **0** |

Focused Surefire: 7; Failsafe: 217. Captured log:
`$env:TEMP\ordops-production-focused.log`, with all twelve production race
observations, including actual waiter SQL and blocker PIDs. XML totals were
checked before the full clean gate removed the focused reports.

Full gate: `.\mvnw.cmd -Ppostgres-it clean verify`, Maven exit 0, BUILD SUCCESS;
completed at `2026-10-13T10:01:53+07:00` in 5 minutes 53 seconds.

| Full clean gate | Suites | Tests | Failures | Errors | Skips |
| --- | ---: | ---: | ---: | ---: | ---: |
| Surefire, including ArchitectureTest (7) | 59 | 423 | 0 | 0 | 0 |
| Failsafe, real PostgreSQL | 39 | 755 | 0 | 0 | 0 |
| **Total** | **98** | **1,178** | **0** | **0** | **0** |

Fresh XML reports in `target/surefire-reports` and `target/failsafe-reports`
were summed; `failsafe-summary.xml` also reports 755 completed, zero failures,
errors, skips and flakes, with no timeout. Captured full log:
`$env:TEMP\ordops-production-full.log`. This full run repeats all twelve production
race observations and all 60 Ops cases. No `40P01`, `55P03` or `57014` SQL state
appears in the full log. Expected fault-injection/uniqueness constraint errors
and warnings from stopped test-container connections do not represent test failures.

## Exact changed files

- `src/main/java/com/uteexpress/order/service/OrderPlacementService.java`: retain
  the existing Shipping service dependency and implement both Ops delegations.
- `src/main/java/com/uteexpress/order/service/OrderLifecycleServiceImpl.java`:
  update the obsolete missing-hook comment; transaction/guard/writer logic unchanged.
- `src/test/java/com/uteexpress/order/service/OpsOrderCancellationIT.java`: replace
  the Shipping fixture with production E2E coverage and strengthen races/rollback.
- `docs/ORD-OPS-01-shipping-integration.md`: this implementation/verification report.

Final `git diff --check` and the new report's whitespace check pass. HEAD remains
`243994cb89b8d1cf4d25340d5bdad82003918f9f` on the specified integration branch;
the worktree contains exactly the four files listed above. No original feature
branch or develop ref was modified.

No remaining local implementation or verification blockers. The changes are
ready for review; review/commit/PR/merge and delivery to develop remain outstanding
and were not performed by this task. QD PR #48's unmerged status is the supplied
task context, not a newly checked remote status.
