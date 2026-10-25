# DASH-01 Vendor revenue dashboard

Owner TD; technical reviewer QD; business/security approver HP. Base develop
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
| Commission Accrued | Delivery event | original stored commission |
| Commission Reversed | Successful refund event | original stored commission |
| Net Commission | Components in this period | accrued - reversed |
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

## Trusted evidence and completeness

Delivery requires a persisted delivered_at with matching SHIPPING->DELIVERED history,
unambiguous valid COD collection evidence, and valid whole-VND Order money snapshots.
The current Order status is NOT used to remove a dated event: return/refund retain
the sale. A missing delivery timestamp on a delivered/return/refund-state legacy row
is instead a diagnostic, never a fabricated positive delivery event.

Payment evidence is preaggregated per Order; exactly one COD PAID Payment with a
non-null paid_at, no expiry, and amount equal to original grand_total is required.
Extra attempts/ONLINE/amount mismatches are unsupported or ambiguous evidence, not a
random choice of MAX(PAID). Current placement/collection/refund production supports
COD; this dashboard does not introduce or assert an ONLINE success producer.

Refund validates same-Order Payment/receipt links, full amount matching grand_total,
original paid/received timestamp snapshots and chronological server_timestamp. It
requires a complete APPROVED receipt with matching RECEIVE_RETURN history/actor.
An Order marked REFUNDED with a missing ledger is unknown, not a zero refund. Receipt
quantity requires received_at/by/reference, APPROVED and matching RETURNED history.
There is one receipt per whole Order; all original item quantities reverse once.

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
adding 1,200 Orders across two Shops, 3,600 Items, 1,200 Payments and 120 whole
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

## Verification

Java 21.0.12.1, Maven Wrapper 3.9.11, Testcontainers PostgreSQL 17.6.

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
.\mvnw.cmd -B --no-transfer-progress -Ppostgres-it '-Dtest=RevenueServiceTest,ArchitectureTest' '-Dit.test=VendorRevenueIT' verify
.\mvnw.cmd -B --no-transfer-progress -Ppostgres-it '-Dtest=RevenueServiceTest,ArchitectureTest' '-Dit.test=VendorRevenueIT,BuyerOrderIT,VendorOrderIT,CodPaymentIT,ManualCodRefundIT,ReturnManagementIT,RefundLedgerMigrationIT,ShipperFulfillmentIT' verify
.\mvnw.cmd -B --no-transfer-progress -Ppostgres-it clean verify
```

Focused verification: 19 unit/architecture + 31 PostgreSQL tests, zero failures,
errors or skips, exit 0. Relevant regression: 19 unit/architecture + 416 PostgreSQL
tests, zero failures/errors/skips, exit 0. After those runs, only additional acceptance
assertions/tests and more detailed EXPLAIN metadata were added; full clean verification
on that final source is recorded below before delivery. Do not sum overlapping reruns.

Initial compile caught a wrong Shop DTO accessor; fixed to the actual shopName contract.
The first focused run passed 19 unit/architecture tests but had 2 fixture errors among
25 PostgreSQL tests (0 failures/skips): quantity changed without matching line_total,
and SUSPENDED Shop lacked moderation_reason. Fixtures were corrected; no constraint,
production owner guard or applied migration was weakened.

Final Java 21 clean verify completed successfully, Maven exit 0:

| Scope | Classes | Tests | Failures | Errors | Skips |
| --- | ---: | ---: | ---: | ---: | ---: |
| Full Surefire | 68 | 636 | 0 | 0 | 0 |
| Full PostgreSQL Failsafe | 54 | 1,143 | 0 | 0 | 0 |
| Total | 122 | 1,779 | 0 | 0 | 0 |
| DASH-01 unit subset | 1 | 12 | 0 | 0 | 0 |
| DASH-01 PostgreSQL subset | 1 | 34 | 0 | 0 | 0 |

The DASH-01 subsets are already included in the full total. Failsafe records
flakes=0, timeout=false. Compile and package phases passed within clean verify.
Logs remain local in .work-dash01-focused.log, .work-dash01-regression.log and
.work-dash01-full.log; raw XML is in target/surefire-reports and target/failsafe-reports.
Full source verification preceded the final documentation/plan capture only; no
production/test source changed after this successful full run.

Full-run teardown emitted PostgreSQL connection-refused/Hikari pool diagnostics and
"Surefire is going to kill self fork JVM" after System.exit(0). The root cause was
not investigated or suppressed in this task. These diagnostics did not produce a
test error, Failsafe timeout or nonzero Maven exit. Mockito dynamic-agent/CDS and
AuthenticationProvider configuration warnings also remain. Expected Review cleanup
ERROR logs are generated by existing fault-injection tests; restricted media keys
are not copied into these public documentation artifacts.

Captured EXPLAIN results from the final full run (warm queries, selected Shop=37):

| Query/window | Planning ms | Execution ms | Root shared buffer hits |
| --- | ---: | ---: | ---: |
| Metrics / one day | 0.486 | 3.903 | 100 |
| Top products / one day | 0.590 | 2.187 | 99 |
| Metrics / one year | 0.478 | 3.067 | 100 |
| Top products / one year | 0.410 | 4.786 | 138 |

Actual disposable DB population at capture: 1,216 Orders, 1,216 Payments, 3,632 Items,
1,338 history rows, 123 ReturnRequests and 122 Refunds, including other tests' fixtures.
No new index is proposed on the strength of this small measured fixture.

MockMvc verifies rendered HTML, escaped snapshots, JSON formula/completeness fields,
role/anonymous/forged-filter denial and unavailable UI. No browser screenshot or
clean-machine deployment verification is claimed. Production authorization follows
read-snapshot semantics; imported invalid/undated history may require owner-led data
remediation, never automatic mutation by this dashboard.
