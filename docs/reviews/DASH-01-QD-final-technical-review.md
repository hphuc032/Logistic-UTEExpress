# DASH-01 PR #60 — QD final technical scope

**PASS for SQL / payment attribution / refunds / commission / measured plans.** No Critical/High blocker found in this reviewed scope. Feature SHA: `1e608133708e322e5ab741cf50803381cf63bc49`. This is not business acceptance, GitHub approval or merge authorization.

## Source and independent verification

The feature HEAD is unchanged from QD's earlier independent 20 unit + 108 PostgreSQL scoped PASS at 1e60813 (reported separately at commit 2004e82). HP/TD's full 1,789-test result belongs to that feature source; QD did not independently repeat that full suite.

At final review, develop advanced to 5b5164d (WS-02 #58 merged), leaving PR #60 behind by 7 commits. QD merged the feature and latest develop only on `verify/dash-01-final-technical`, tested source `06c52928b98fc9d5df26b7f97d93a6a594f01896`. Merge was clean; no production/test/fixture/migration edits or constraint relaxation. Revenue SQL/service files are byte-for-byte unchanged by integration.

```powershell
.\mvnw.cmd -q -Ppostgres-it '-Dtest=RevenueServiceTest,ArchitectureTest' '-Dit.test=VendorRevenueIT,ManualCodRefundIT,RefundLedgerMigrationIT,NotificationSocketIT' clean verify
```

Java 21, PostgreSQL 17.6. **20 unit/architecture + 137 PostgreSQL = 157 PASS**, zero failures/errors/skips, flakes=0, timeout=false, Maven exit0. This is independent scoped compatibility verification, not full regression at a future TD-synced HEAD. Do not add the prior 128-test run to this overlapping result. Raw local log qd-dash01-final-technical.log stays ignored; sanitized XML counts/hashes and exact plan observations are in the companion evidence JSON.

## Technical conclusions

- SQL grain: scoped Orders stay one row per Order through unique Payment/ReturnRequest evidence. Financial event streams aggregate before any OrderItem quantity joins. Equal-valued Orders and multi-item Orders are preserved without SUM(DISTINCT) or fanout.
- Attribution: payment_counts counts every PAID first; only exactly one PAID row is selected, and its ID/amount/paid_at travel together. Unsuccessful historical attempts are ignored. COD, non-expiry, amount equality and dated evidence are checked. Multiple PAID is rejected by existing DB uniqueness and cannot pick an arbitrary row in SQL.
- Refund: exact successful Payment ID and approved receipt ID/timestamps match the immutable ledger. Refund must equal original grand_total and be recorded after payment/receipt. Merchandise refund subtracts the stored shipping fee, so shipping does not enter Vendor earnings. Same-Order unsuccessful payment is unavailable; cross-Order payment fails composite FK. Missing ledger/evidence is explicit unknown, not complete zero.
- Commission: persisted commission amount/rate snapshot is used; NULL policy ID with valid snapshots and legitimate zero stay valid. No current-policy recomputation, new financial ledger or pricing mutation. Full refund reverses the original commission in refund period. Missing/invalid snapshots propagate unknown diagnostics to derived metrics.
- Reporting time: delivered_at, paid_at, received_at and refund server_timestamp remain independent streams. Net Vendor Sales = delivered goods minus merchandise refunds; Net Commission = accrued snapshot minus reversal. Original sales period is not rewritten after return/refund.
- Authorization/consistency: current ACTIVE VENDOR and approved owned Shop derive server scope under REPEATABLE_READ. Shop filters or amounts are not trusted from browser. Read model does not lock or mutate lifecycle. Statement count remains fixed as item count increases; ArchUnit passes without a governance -> Order cycle.

## Fresh PostgreSQL plans

VendorRevenueIT EXPLAINs the actual production METRICS_SQL and TOP_SQL after warm-up, with 1,200 added Orders across two Shops, 3,600 Items, 2,400 Payments (one PAID + unsuccessful attempt per Order) and 120 receipts/refunds. Actual DB includes prior fixtures: 1,219 Orders, 2,423 Payments, 3,638 Items, 1,343 history rows, 125 ReturnRequests and 123 Refunds. Selected Shop owns 600 representative Orders. Full fresh plans are in DASH-01-QD-final-plans.txt.

| Query | Window | Planning ms | Execution ms | Root shared hits |
| --- | --- | ---: | ---: | ---: |
| METRICS_SQL | one day | 1.823 | 10.658 | 169 |
| TOP_SQL | one day | 2.759 | 11.293 | 168 |
| METRICS_SQL | one year | 2.473 | 11.772 | 169 |
| TOP_SQL | one year | 1.778 | 15.676 | 207 |

Plans show scoped CTE/materialization and hash/sequence operations appropriate to this fixture; hash batches remain one, observed sorts are in memory and no temp/external spill is reported. Scans of history/undated diagnostics can still grow with Shop history. Local warm-cache timings are not a production SLA, load test or a reason to invent an index. Profile larger distributions before coordinating future indexes with QD/TD.

## Merge gates / handoff

1. TD must sync PR #60 with latest develop and provide the resulting exact HEAD. QD did not rewrite or push the TD branch.
2. TD runs full PostgreSQL clean verify and exact-head CI on that synced HEAD; review any resulting delta. The local integration branch is evidence, not the feature PR and not a merge shortcut.
3. HP completes business/authorization acceptance and the reviewer approval/branch-protection process. No approve/merge/settings action is performed by this report.
4. DASH-02 reuse still needs the neutral reporting-module/scope contract recorded at 2004e82; do not copy these SQL formulas or introduce a governance -> Order dependency just for Admin revenue.
