# DASH-01 Vendor revenue dashboard

Owner TD; technical reviewer QD; business/security approver HP. Initial implementation base develop
`bce7ff09e2a91af4c86ac23aeedf0d5a802adc33`. The task reads the Master Project Plan
(D2, G, H, I7, M2, T45) and QD technical contract
[`6abaadf2efe0b635127ce91e1ee1432d0d46dc55`](https://github.com/hphuc032/Logistic-UTEExpress/blob/6abaadf2efe0b635127ce91e1ee1432d0d46dc55/docs/DASH-01-QD-read-contract-review.md).
The HP policy supplied for this implementation settles the primary measure as
**Delivered Sales minus merchandise Refunds**, rather than net cash collection.
PR #57 was already merged by its owners before this task; this task neither merges
it nor edits its branch. No other feature branch is integrated.

## Route, authorization and periods

`GET /vendor/dashboard` supports HTML and `Accept: application/json`. Optional ISO
`from` and `toExclusive` dates must be supplied together; the interval must be positive,
at most 366 days, and in years 1900..9999. Default selected interval is the current
month. Today and this month are also returned. Unknown query fields, including
shopId/ownerId/amount, are rejected. No request contains authoritative identity or money.

HTTP and service method guards require VENDOR. The service derives the Account ID
from CurrentAccountIdProvider, reads persisted ACTIVE/VENDOR through
IdentityAuthenticationService, then resolves the owned APPROVED Shop through
VendorShopQueryService. Every SQL stream uses this one server-selected Shop ID.
No HP/QD repository or entity is imported. Shop and product names render escaped.

RevenueService rejects an existing transaction, then starts a fresh read-only
REPEATABLE_READ transaction encompassing identity, role, Shop and aggregate reads.
Authorization is at that snapshot: a revocation committed after it does not interrupt
the response, and the next request is denied. No stronger mid-response revocation or
write-lock guarantee is claimed. No Account-first lifecycle call is made.

One injected Clock instant defines all local boundaries in Asia/Ho_Chi_Minh. UTC
TIMESTAMPTZ bindings implement [start,end); no session-timezone DATE filter is used.
For example, 2027-01-01 is [2026-12-31T17:00Z,2027-01-01T17:00Z).

## Metrics and event grain

Let goods = persisted Order.subtotal - Order.discount_total, and commission =
persisted Order.commission_amount. Promotion is already reflected in subtotal;
voucher discount is not subtracted twice. Shipping is never Vendor earnings.

| Metric | Source event/time | Amount |
| --- | --- | --- |
| Delivered Sales | Successful Order delivery, delivered_at | goods |
| Collected Payments | Valid COD PAID evidence, paid_at | goods (Payment amount excludes its matching shipping component in this view) |
| Refunds | SUCCEEDED full COD ledger, server_timestamp | refund.amount - original Order.shipping_fee |
| Net Vendor Sales | Components in this reporting period | Delivered Sales - Refunds |
| Net Collected Revenue | Collection/refund components in this reporting period | Collected Payments - Refunds |
| Commission Accrued | Delivery event | original stored commission |
| Commission Reversed | Successful refund event | original stored commission |
| Net Commission | Components in this period | accrued - reversed |
| Net Revenue After Commission | Collection/refund/commission components in this reporting period | Net Collected Revenue - Net Commission |
| Total Orders | Order.created_at | count |
| Delivered Orders | Order.delivered_at | count of persisted timestamps |
| Cancelled Orders | Order.cancelled_at | count of persisted timestamps |
| Net product quantity | delivered_at and RECEIVE_RETURN received_at | delivered quantity - whole-receipt quantity |

Refund timestamp is ADMIN recording/completion time, not bank transfer time. Receipt
timestamp is the trusted RECEIVE_RETURN recording time. Returns reverse quantities
before/independently of refunds; a refund never reverses quantity again. No restocking,
inventory policy, financial ledger or payout/remittance is implemented. Period values
may be negative; no clamping or allocation back to the sale period occurs.

For example, goods 90,000, shipping 15,000, commission 9,000: delivery contributes
90,000 sales and 9,000 commission; later full cash refund of 105,000 contributes
90,000 merchandise refund and 9,000 commission reversal in its own period. Collected
Payments remains a separate 90,000 collection in the original paid_at period.

## HP final business delta — Master Plan I7

Delta baseline: `da96b215de8bb987870463b6d9d36521ce590f08`, synchronized with develop
`5b5164dd29ba5465ffefe19d431da7b70e41357a`. Existing financial rules are preserved.

The Master Project Plan I7 requires merchandise collected after discount/refunds,
excluding shipping, grouped by collection/refund times, and a separate amount after
commission. HP's final delta adds two derived RevenueMetrics properties to each
today/month/selected window, exposed additively in JSON and directly in the HTML table:

- `netCollectedRevenue = collectedPayments - refunds`.
- `netRevenueAfterCommission = netCollectedRevenue - netCommission`.

These reuse the existing RevenueAmount.subtract operation with exact BigDecimal
arithmetic. They add no SQL, queries, constructor inputs or persisted evidence.
Existing Net Vendor Sales remains delivery-based and Net Commission remains
accrued minus reversed. Authorization, REPEATABLE_READ snapshot, event timestamps,
UTC half-open reporting periods, Order/refund grain and shipping exclusion stay intact.

An unknown collection or refund makes both new metrics unavailable. An unknown
commission makes only the after-commission result unavailable when collection/refund
are complete. Values remain null with `complete=false`; diagnostics sum source
unknownEvents, so the same underlying event can affect more than one component.
Known zero remains zero. Negative cash or commission is never clamped; subtracting
negative Net Commission increases the after-commission amount without inventing a payout.

For collection 90,000 in December, delivery/commission 9,000 in January and a full
merchandise refund 90,000/commission reversal 9,000 in March:

| Period | Collected Payments | Refunds | Net Commission | Net Collected Revenue | Net Revenue After Commission |
| --- | ---: | ---: | ---: | ---: | ---: |
| December | 90,000 | 0 | 0 | 90,000 | 90,000 |
| January | 0 | 0 | 9,000 | 0 | -9,000 |
| February (receipt only) | 0 | 0 | 0 | 0 | 0 |
| March | 0 | 90,000 | -9,000 | -90,000 | -81,000 |

The sales period is not rewritten and the 15,000 shipping portion contributes to
neither metric. Period tests use a collection immediately before the local year
boundary and delivery immediately after it, with unsuccessful attempts dated in
other periods; only the validated successful Payment dates collection.

The delta changes only RevenueMetrics, the Vendor template, Order unit/PostgreSQL
tests and this documentation. Focused and full verification evidence for this
delta is recorded separately from the historical payment-attribution run below.

Acceptance tests cover exact values above the floating-point integer boundary,
known zero/refund zero, refund exceeding collection, signed commission, incomplete
collection/refund/accrual/reversal, and invalid persisted payment/refund/commission
evidence. PostgreSQL + MockMvc assert JSON fields and rendered table cells for all
three windows, including negative and unavailable values. Multi-item/equal-valued
Orders and repeated payment attempts keep the expected totals; reading both new
properties leaves analytical statement counts unchanged.

### I7 delta verification — 2026-10-26

Independently executed with Java 21.0.12.1, Maven Wrapper 3.9.11 and real
Testcontainers PostgreSQL 17.6:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
.\mvnw.cmd -B --no-transfer-progress -Ppostgres-it '-Dtest=RevenueServiceTest,ArchitectureTest' '-Dit.test=VendorRevenueIT' verify
.\mvnw.cmd -B --no-transfer-progress -Ppostgres-it clean verify
node --test src/test/js/notifications.test.cjs
```

| Run / scope | Classes | Tests | Failures | Errors | Skips | Flakes | Exit |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Focused unit/architecture | 2 | 30 | 0 | 0 | 0 | 0 | 0 |
| Focused PostgreSQL | 1 | 46 | 0 | 0 | 0 | 0 | 0 |
| Full clean Surefire | 71 | 675 | 0 | 0 | 0 | 0 | 0 |
| Full clean PostgreSQL Failsafe | 55 | 1,187 | 0 | 0 | 0 | 0 | 0 |
| Full Maven total | 126 | 1,862 | 0 | 0 | 0 | 0 | 0 |

Focused/full Failsafe summaries both report timeout=false. Full clean verify took
08:12 min and includes compile/package. Focused tests overlap the full run. Included
DASH-01 subsets are RevenueServiceTest 23 and VendorRevenueIT 46. Supplemental Node
22.11.0 tests: 12 passed, 0 failed/skipped/cancelled, exit 0; client code is unchanged.

No production/test source changed after full clean verify; only this documentation
was finalized. Generated production SQL remains byte-identical to the tracked SQL
artifact, so EXPLAIN SQL/plans are not replaced with unrelated new measurements.
No tests were disabled or skipped. Existing Hikari closed/refused-connection teardown
diagnostics and slow fork shutdown after System.exit(0) occurred; test XML still has
zero errors/failures/flakes and no Failsafe timeout, with Maven exit 0.

Local logs: .work-dash01-i7-focused.log, .work-dash01-i7-full.log and
.work-dash01-i7-node.log. Raw current XML: target/surefire-reports and
target/failsafe-reports. Exact-head CI is checked after push and recorded on PR #60.
This delta is ready for QD scoped review; no final business acceptance, PR merge or
DASH-01 COMPLETED status is implied.

## Trusted evidence and completeness

Delivery requires a persisted delivered_at with matching SHIPPING->DELIVERED history,
unambiguous valid COD collection evidence, and valid whole-VND Order money snapshots.
The current Order status is NOT used to remove a dated event: return/refund retain
the sale. A missing delivery timestamp on a delivered/return/refund-state legacy row
is instead a diagnostic, never a fabricated positive delivery event.

HP's final payment-attribution policy allows zero or more unsuccessful UNPAID
attempts. `payment_counts` counts **all** PAID rows per Order before validating any
of them. `payment_facts` joins back to the PAID row only when `paid_count=1`;
payment_id, paid_amount and paid_at are direct columns from that same row. The
single PAID row must be COD, have expired_at IS NULL and non-null paid_at, and its
amount must equal original grand_total. No independent MIN/MAX over attempts or
bool_and across unsuccessful attempts is used. An unsuccessful attempt's method,
amount, expiry and even a stray paid_at cannot invalidate or date a valid success.

No PAID row, invalid successful evidence, or multiple PAID rows fails closed.
Counting precedes content validation: an invalid second PAID cannot be filtered
away to choose the valid-looking row. For zero or multiple PAID rows the join
returns no successful fields; the existing undated/unknown handling applies.
The production partial unique index `uq_payments_order_id_paid` already prevents
multiple PAID rows per Order. Tests assert SQLSTATE 23505 and the index definition,
rather than dropping/weakening it to fabricate an unreachable duplicate fixture.
The SQL count guard independently avoids choosing a winner or multiplying money.
Current placement/collection/refund production supports COD; this dashboard does
not introduce or assert an ONLINE success producer. The schema has UNPAID/PAID
statuses; failed/expired attempts remain UNPAID, not a fabricated FAILED status.

Refund validates the exact successful Payment ID (not merely an arbitrary
same-Order attempt), receipt links, full amount matching grand_total,
original paid/received timestamp snapshots and chronological server_timestamp. It
requires a complete APPROVED receipt with matching RECEIVE_RETURN history/actor.
An Order marked REFUNDED with a missing ledger is unknown, not a zero refund. Receipt
quantity requires received_at/by/reference, APPROVED and matching RETURNED history.
There is one receipt per whole Order; all original item quantities reverse once.
The composite `fk_refunds_payment_order` rejects a Payment from a different Order
(SQLSTATE 23503). It permits a same-Order UNPAID ID; the dashboard rejects that
evidence even when its amount and paid_at match the successful Payment exactly.
Refund fixture helpers explicitly select the unique PAID row, so retry attempts
also cannot fan out the test ledger. No production Payment/Refund writer changes.

Commission amount/rate must be finite, within stored bounds, and whole-VND where
applicable. Valid NULL commission_policy_id and valid zero amount/rate are legitimate.
No current policy is read; amount is not recalculated from a current or historical
rate. The schema has no provenance flag distinguishing an imported placeholder zero
from a valid zero, so this read model does not invent one.

Each financial DTO is `{value, unknownEvents, complete}`. Unknown source events make
the affected value null, not zero. Derived totals propagate unavailability; other
independent complete metrics remain usable. SQL zero aggregates are displayed only
when the unknown count is zero. Missing timestamps cannot be assigned to a period;
such scoped legacy evidence conservatively makes the affected totals unavailable
for each requested period. Diagnostics count component events, not unique Orders.
Order count cards count stored timestamps only; they do not synthesize missing events.

Top-products returns known events and an explicit completeness/unknown count, not a
claim that an incomplete list is the definitive ranking. Ranking is net quantity
descending, Product ID ascending, limited to 10. Negative and zero quantities are
preserved. Names use the highest persisted item ID's name snapshot among the selected
known events, not live Product values; historical names can differ between periods.

## SQL, query count and architecture

RevenueQueryRepository contains the exact METRICS_SQL and TOP_SQL. Its scoped Order
CTE feeds separate delivery, collection, refund, receipt and count streams. Money
is aggregated at Order/refund grain before any item join. Quantity joins items only
after selecting the Order delivery/receipt events and uses UNION ALL. No SUM(DISTINCT
amount) workaround or current-status=DELIVERED-only filter exists.

Today/month/selected metrics use at most three aggregate statements; identical
periods reuse results. One top-product statement serves the selected interval.
The normal default request has three analytical statements; a distinct custom
interval has four. Count is independent of number of OrderItems. Identity/role/Shop
lookups are additional fixed authorization reads. No per-item lazy lookup occurs.

Only new Order module files, the Vendor template, Order tests and this task's
documentation are added. Existing Order/Payment lifecycle, Shop/Product, policy,
Inventory, Shipping, SecurityConfig, shared fragments and Ops dashboard are untouched.
The dashboard depends on existing public identity/Shop services. No governance->Order
dependency is introduced; Order already depends on governance, so DASH-02 must not
import RevenueService without resolving its own dependency graph.

## PostgreSQL plans and limits

VendorRevenueIT runs the exact production SQL after warm-up using PostgreSQL 17.6,
adding 1,200 Orders across two Shops, 3,600 Items, 2,400 Payments (1,200 PAID and
1,200 unsuccessful UNPAID attempts) and 120 whole
receipts/refunds. The selected Shop owns 600 of those Orders. Sparse one-day and
dense one-year windows are measured with EXPLAIN (ANALYZE, BUFFERS); actual total
table counts, server version, bindings, times and plans are recorded separately.
Other acceptance fixtures can coexist in that disposable database.

See DASH-01-explain.txt and DASH-01-explain-queries.sql for the captured run and exact
queries. Existing indexes are retained; there is no new migration or reserved version.
Plans materialize scoped Order/evidence CTEs and use sequential/hash operations on
this fixture. This is acceptable measured behavior, not a requirement for index scans.
Diagnosing undated legacy evidence means work can grow with a Shop's history even
for a sparse window. Larger production distributions need fresh plans before index
coordination with QD. These local timings are not a hardware-independent SLA.

## Historical payment-attribution verification (before the I7 delta)

### HP-approved payment attribution fix (PR #60)

The payment-attempt correction changes only the Order-owned read query and its
tests/documentation. No lifecycle, authorization, commission policy, migration,
index or shared module changes are included. Regression cases exercise:

| Evidence/case | Expected result |
| --- | --- |
| One or 3/7 unsuccessful attempts, then one valid COD PAID | Complete money, each Order counted once; success ID/amount/time asserted directly |
| Extra UNPAID after PAID | Complete money; no arbitrary earlier/later attempt wins |
| Equal-valued, multi-item Orders with retries | Both Orders count; neither money nor commission fans out |
| Zero PAID (missing Payment or only UNPAID attempts) | Unavailable collection/delivery money, never a complete zero |
| PAID ONLINE, expired, missing paid_at or wrong amount | Unavailable successful evidence |
| Second PAID, COD or contradictory ONLINE/expired/amount/time | Unique index rejects with SQLSTATE 23505; original successful evidence remains intact |
| Refund links exact valid success among earlier/later attempts | Merchandise refund and original commission reversal valid, once |
| Refund links same-Order UNPAID with identical amount/paid_at | Refund/reversal and derived totals unavailable; delivery/collection remain complete |
| Refund links a different Order's PAID | Composite FK rejects with SQLSTATE 23503; no refund inserted |
| Collection December, delivery January, receipt February, refund March with retries | Each independent event keeps its own period; no rewrite of January sales |
| Invalid/unknown evidence | Null value with diagnostics propagates through affected derived totals |

Focused verification of the final test source: 20 unit/architecture tests and
43 PostgreSQL tests, failures=0, errors=0, skips=0, Maven exit=0. Relevant regression,
full clean verification and regenerated plans are recorded below.

### Commands and results

Java 21.0.12.1, Maven Wrapper 3.9.11, Testcontainers PostgreSQL 17.6.

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
.\mvnw.cmd -B --no-transfer-progress -Ppostgres-it '-Dtest=RevenueServiceTest,ArchitectureTest' '-Dit.test=VendorRevenueIT' verify
.\mvnw.cmd -B --no-transfer-progress -Ppostgres-it '-Dtest=RevenueServiceTest,ArchitectureTest' '-Dit.test=VendorRevenueIT,BuyerOrderIT,VendorOrderIT,CodPaymentIT,ManualCodRefundIT,ReturnManagementIT,RefundLedgerMigrationIT,ShipperFulfillmentIT' verify
.\mvnw.cmd -B --no-transfer-progress -Ppostgres-it clean verify
```

Verification on the HP-approved payment-attribution source (Java 21.0.12.1,
Maven Wrapper 3.9.11, PostgreSQL 17.6):

| Run / scope | Classes | Tests | Failures | Errors | Skips | Maven exit |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Focused unit/architecture | 2 | 20 | 0 | 0 | 0 | 0 |
| Focused PostgreSQL | 1 | 43 | 0 | 0 | 0 | 0 |
| Relevant unit/architecture | 2 | 20 | 0 | 0 | 0 | 0 |
| Relevant PostgreSQL regression | 8 | 428 | 0 | 0 | 0 | 0 |
| Full clean Surefire | 68 | 637 | 0 | 0 | 0 | 0 |
| Full clean PostgreSQL Failsafe | 54 | 1,152 | 0 | 0 | 0 | 0 |
| Full clean total | 122 | 1,789 | 0 | 0 | 0 | 0 |

Focused/regression reruns overlap with the full run and must not be added to it.
DASH-01 subsets in the full total: RevenueServiceTest 13, VendorRevenueIT 43.
Surefire XML reports flakes=0; Failsafe summary reports flakes=0, timeout=false.
All three commands exited 0; compile/package passed within full clean verify
(total Maven time 07:50 min). No production/test source changed after this run;
only documentation and captured plan artifacts were finalized afterwards.

The first fix-focused run passed 20 unit/architecture + 42 PostgreSQL tests.
One more only-UNPAID case and extra equal-value/multi-item retry assertions were
then added; focused, relevant and full runs above all verify that final source.
No failing test run or constraint relaxation was needed for this correction.
Original implementation verification remains in Git history and is superseded
by these source-specific results.

Logs remain local in .work-dash01-attribution-focused.log,
.work-dash01-attribution-regression.log and .work-dash01-attribution-full.log;
raw XML is in target/surefire-reports and target/failsafe-reports.

Full-run teardown again emitted PostgreSQL connection-refused/closed-connection
Hikari diagnostics and "Surefire is going to kill self fork JVM" after
System.exit(0). These did not produce a test error, Failsafe timeout or nonzero
Maven exit. Their infrastructure root cause was not investigated or suppressed
in this scoped fix. Mockito dynamic-agent/CDS and AuthenticationProvider warnings
also remain. Expected constraint/rollback fault-injection logs are not test
failures; restricted media keys from other tests are not published here.

Captured EXPLAIN (ANALYZE, BUFFERS) results from this full run after warm-up,
using the exact new production SQL (selected Shop=41):

| Query/window | Planning ms | Execution ms | Root shared buffer hits |
| --- | ---: | ---: | ---: |
| Metrics / one day | 1.103 | 4.554 | 169 |
| Top products / one day | 0.507 | 2.797 | 168 |
| Metrics / one year | 0.546 | 3.767 | 169 |
| Top products / one year | 0.675 | 5.279 | 207 |

Actual disposable DB population at capture: 1,219 Orders, 2,423 Payments,
3,638 Items, 1,343 history rows, 125 ReturnRequests and 123 Refunds, including other
tests' fixtures. The added representative data includes one failed/UNPAID attempt
per PAID Order. Both plan and SQL artifacts are regenerated from the full run's
VendorRevenueIT, without substituting a simplified query. No new index is proposed
on the strength of this small measured fixture; timings are local observations.

MockMvc verifies rendered HTML, escaped snapshots, JSON formula/completeness fields,
role/anonymous/forged-filter denial and unavailable UI. No browser screenshot or
clean-machine deployment verification is claimed. Production authorization follows
read-snapshot semantics; imported invalid/undated history may require owner-led data
remediation, never automatic mutation by this dashboard.
