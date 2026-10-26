# DASH-01 I7 — QD scoped delta review

**PASS. Findings: Critical 0 / High 0 / Medium 0 / Low 0 within the requested delta scope.**

- Baseline: da96b215de8bb987870463b6d9d36521ce590f08.
- Reviewed HEAD: 0bff248835574386dcf285163cbcc99b63362c53, PR #60.
- Delta verified: one commit, five files, 233 additions / 14 deletions (RevenueMetrics, Vendor template, two Order test files and documentation).
- Source checkout was clean; QD did not change TD code, fixtures or applied migrations. This report is a separate docs branch, not a new feature HEAD.

## Findings and contract confirmation

1. Formulas exactly match the requested contract: Net Collected Revenue = Collected Payments - Refunds; Net Revenue After Commission = Net Collected Revenue - Net Commission. Both reuse existing RevenueAmount.subtract and exact BigDecimal arithmetic. Net Vendor Sales stays delivery-based; it is not substituted for collected revenue.
2. Unknown collection/refund makes both derived results unavailable (null/complete=false). Unknown commission affects after-commission result while a complete collection-minus-refund result stays available. Known zero stays zero. Diagnostics sum dependent component unknownEvents, not distinct underlying events; the same bad source may contribute through more than one component as already documented. No fabricated zero or hidden incompleteness.
3. Signed amounts are retained: no clamping, absolute-value conversion or floating-point computation. Subtracting negative Net Commission increases the result algebraically without asserting a new payout. Test values above the IEEE-754 exact-integer boundary remain BigDecimal-exact in the DTO.
4. Refund/commission from different reporting periods remain separate. Collection 90,000 in December yields 90,000; January commission 9,000 without collection yields -9,000 after commission; receipt-only February yields zero; March refund 90,000 with reversal 9,000 yields -90,000 collected net and -81,000 after commission. Earlier sales periods are not rewritten.
5. JSON exposes both additive derived properties with value/complete/unknownEvents. Thymeleaf renders Today/Month/Selected from the corresponding window, including negative, zero and unavailable cells. Labels/caption describe within-period components and shipping exclusion. Existing escaped product snapshot rendering remains covered.
6. No SQL/repository, service/controller authorization, timestamp attribution, shipping treatment, commission snapshot, event grain, Order/Payment lifecycle, financial ledger or migration delta exists. These fields reuse the already-aggregated merchandise components. They introduce no item/payment joins, query or financial re-aggregation. Statement-count/equal-value/multi-item/multiple-attempt tests remain valid, so reading the metrics does not double count money or change attribution.

## Independent scoped execution

On exact reviewed HEAD, Java 21 and PostgreSQL 17.6 Testcontainers:

```powershell
.\mvnw.cmd -q -Ppostgres-it '-Dtest=RevenueServiceTest,ArchitectureTest' '-Dit.test=VendorRevenueIT' clean verify
```

**30 unit/architecture + 46 PostgreSQL tests = 76 PASS**, zero failures/errors/skips/flaky cases, Maven exit 0, Failsafe timeout=false/flakes=0. This includes exact arithmetic, independent period adjustments, null diagnostics, JSON/rendered cells in all windows, signed totals, unchanged SQL statement count and existing attribution/ownership checks. The existing VendorRevenueIT also generated actual production-query EXPLAIN evidence; SQL itself is unchanged by this delta.

Raw local log qd-dash01-business-delta.log stays ignored. Per-class counts/report hashes are in DASH-01-I7-QD-delta-evidence.json. These are local run artifacts, not an external-runner attestation. Existing infrastructure startup/teardown warnings are not counted as additional checks.

## Acceptance boundary

Exact-head GitHub CI SUCCESS and behind=0 were verified for 0bff248. TD reports full 675 unit + 1,187 PostgreSQL = 1,862 PASS and 12 Node tests; QD did not independently rerun that entire suite. Do not label this scoped 76-test run as full regression or add overlapping totals together.

QD scoped technical delta review is PASS. HP still owns final business acceptance; GitHub reviewer approval/branch protection and merge remain separate team gates. No GitHub approval, merge, settings change or new production feature is performed by this report.
