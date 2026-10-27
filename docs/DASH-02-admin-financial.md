# DASH-02 — Admin financial reporting

QD implements; TD reviews attribution/SQL/commission/refunds; HP reviews Admin authorization and final acceptance. PR #59 is synchronized with develop e6d94c5 (DASH-01 #60 and WS-02 #58 merged). Ops/Account/Audit Phase 1 routes, filters and definitions remain intact. Do not mark DASH-02 COMPLETED until the team review/merge decision.

## Routes and scope

- GET /admin/dashboard links to GET /admin/dashboard/revenue (HTML).
- GET /admin/dashboard/revenue/metrics is the same Admin-only JSON read model, no-store.
- Optional positive shopId narrows an already-authorized Admin read; omitted means explicit ALL_SHOPS scope. No null Shop filter can broaden a Vendor request: Vendor still derives its owned approved Shop server-side and calls the primitive positive-Shop entry point.
- from/toExclusive are ISO local dates, both supplied or both omitted. Default current month; max 366 days and the same RevenuePeriod validation as DASH-01. Today/month/selected are displayed separately; timezone Asia/Ho_Chi_Minh with UTC [start,end) boundaries. The original Ops inclusive from/to filter is unchanged and separate.
- HTTP/method checks plus persisted ACTIVE ADMIN role are required. Manager, Buyer, Vendor and Shipper are denied; a forged/stale principal cannot override DB eligibility. Manager HTML has no financial link. Admin may read historical data across all Shops, including non-current/non-approved ones; this is not a Vendor ownership authority.

## Single financial authority / dependency graph

DASH-01 RevenueAmount, RevenueMetrics, RevenuePeriod, RevenueSummary and exact existing SQL move mechanically to the neutral reporting module. Order's RevenueService retains Vendor authorization and snapshot ownership; governance's AdminRevenueService owns Admin authorization and scope. Both use reporting.service.RevenueProjectionService. Dependencies are Order -> reporting and governance -> reporting; reporting has no Order/governance dependency, avoiding the existing Order -> governance cycle.

Java DTO namespace changes to reporting.dto and the internal projection bean changes from Order repository to shared service; TD must review this extraction. Vendor route/JSON fields/formulas remain unchanged, and all existing Vendor acceptance cases are retained with mechanical imports/spy references adjusted. No Order/Payment lifecycle, financial ledger, commission policy, Shipping, Inventory, voucher or applied migration changes.

The shared scoped SQL literals remain the DASH-01 query. Global SQL changes only the initial scoped Order selection to all Orders; payment/evidence/event/grain/aggregation logic is identical. Scope is a validated explicit enum DTO issued by trusted wrappers, never HTTP-bound. No OR/NULL predicate is added to the existing Vendor query. No copied financial SQL in governance and no current-policy recomputation.

The shared service requires a caller transaction and enforces read-only REPEATABLE_READ. Each wrapper rejects an outer transaction and opens its own read snapshot, including persisted authorization and all window queries. It takes no Account/Order locks and writes no financial facts. Snapshot governance changes do not retroactively invalidate the response; the next request rechecks eligibility. This is reporting consistency, not a write-authority/real-time revocation lock protocol.

## Metrics

All amounts are merchandise after discount, excluding shipping, exactly as merged DASH-01:

| Metric | Authority / formula |
| --- | --- |
| Delivered Sales | Persisted successful delivery evidence at delivered_at |
| Collected Payments | Exactly one valid COD PAID record at paid_at, amount matches grand_total; report merchandise portion |
| Refunds | Exact successful Payment/receipt immutable SUCCEEDED refund at server_timestamp, less original shipping |
| Net Collected Revenue | Collected Payments - Refunds |
| Net Commission | Original accrued snapshot - original snapshot reversed in refund period |
| Net Revenue After Commission | Net Collected Revenue - Net Commission |

Failed/UNPAID attempts do not poison the one valid PAID record. Multiple/invalid success, missing ledger/receipt/payment/timestamps and invalid money/snapshot fail closed to unavailable diagnostics. Known zero and NULL policy ID with valid zero snapshots remain valid. No refund/commission is rewritten into the old sale period. Signed BigDecimal results remain negative where appropriate; no clamping or double discount. Diagnostics sum dependent component observations, not distinct Orders.

Global aggregation is over Order/refund event grain before item quantities. It never sums already-rounded per-Shop summaries, joins all Payment attempts to Items, or uses SUM(DISTINCT money). A bad Shop can make a system total unavailable while a complete Shop-filtered read stays available.

## Verification and plans

Focused Java 21 verification: 44 unit/UI/architecture + 100 PostgreSQL tests PASS, zero failures/errors/skips, Maven exit0, timeout=false/flakes=0. Includes all existing VendorRevenueIT (46), OpsDashboardReadIT (28) and new AdminRevenueIT (26). No production constraints/triggers or applied migrations were disabled/rewritten. Initial focused failures were fixture-only: MockMvc clearing SecurityContext and an expected rollback-only transaction; those were corrected and the full focused run repeated.

New cases cover multi-Shop/equal-value/multi-item Orders, unsuccessful attempts, Admin-Shop/Vendor parity, cross-period refund/commission, zero/legacy unknown snapshots, refund Payment mismatch, persisted-role/locked/revoked Admin, Manager/other-role/anonymous denial, duplicate/untrusted filters, half-open HCM boundaries, consistent snapshot across committed payment mutation and role revocation, explicit scope/caller transaction guards, fixed query count and HTML/JSON output.

Global numeric cases run on the pristine class database before deliberately undated legacy fixtures; global legacy diagnostics are intentionally not time-filterable. Each legacy case is also asserted under its own Shop, so previous unknown facts cannot make that case pass accidentally. Immutable ledger evidence is not deleted to reset fixtures.

Admin runs two default metric queries or three for a distinct custom period (identity/role reads additional); count does not scale with Shop/Item count. No new index/migration. EXPLAIN(ANALYZE,BUFFERS) uses exact scoped/global production queries, 1,200 added Orders across two Shops, 2,400 Payments, 3,600 Items, 120 receipts/refunds. It is a synthetic local baseline, not a production SLA/load test. Historical/undated completeness scans still scale with system history; profile real deployment distributions before an index decision.

Full Java 21 `-Ppostgres-it clean verify`: **682 unit/architecture (72 classes) + 1,241 PostgreSQL tests (57 classes) = 1,923 PASS**, zero failures/errors/skips/flaky cases, Maven exit0, Failsafe timeout=false/flakes=0. 12 Node tests PASS separately. Exact-head CI is recorded in PR #59 after push. Local logs remain ignored (dash02-finance-full-postgres.log, dash02-finance-node.log). Existing Hikari/container shutdown and fork termination after System.exit(0) warnings occurred; they did not cause test errors/timeout/nonzero exit and were not suppressed. TD/HP final review remains required; no self-merge or full-task completion is claimed here.


Extraction check against develop: scoped EVIDENCE/METRICS/TOP SQL literal block unchanged, SHA-256 dd3b2fdef06cca21b59f03ac270c52427f7c5c486593ab24d8500630c6900612. All four financial DTO bodies match after package substitution. New global scope only removes the initial Shop predicate. Global EXPLAIN reuses a parameter map whose Shop binding is unused by GLOBAL_METRICS_SQL; its actual query has no Shop predicate.

Fresh full-run plan observations (local synthetic fixture, not SLA):

| Query | UTC window | Execution ms |
| --- | --- | ---: |
| METRICS_SQL | one day | 8.862 |
| GLOBAL_METRICS_SQL | one day | 11.850 |
| METRICS_SQL | one year | 14.215 |
| GLOBAL_METRICS_SQL | one year | 15.230 |
