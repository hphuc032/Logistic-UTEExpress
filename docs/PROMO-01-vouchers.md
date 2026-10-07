# PROMO-01 Voucher

STATUS: READY FOR REVIEW

Owner: Tiến Đạt (TD). Reviewer: Hoàng Phúc (HP). Dependency: CHK-02.
Scope: shop/platform model, limits, usage, lock quota, checkout apply.

## Inspected starting state

The working tree was clean on `feature/promo-01-vouchers`. HEAD and local
`origin/develop` were both `27e7694e8841f6ae1d62fe3feeedf283e4140838`.
No branch switch, index write, commit, push, merge or rebase is part of this work.

CHK-01 already reads the authenticated buyer's selected cart, enforces one shop,
locks/checks products and their shop/category availability, and resolves the owned
address and persisted shipping quote. It writes no checkout state. CHK-02 locks
the active buyer before the buyer/key lookup, replays a matching hash before any
mutable checkout reads, locks the cart, recomputes quote/commission, and persists
stock/order/items/history/COD payment/cleanup in one transaction.

The existing request/hash already carried an optional voucher code, but placement
rejected nonblank codes. Orders already persist order-level `discount_total` and
validate `grand_total = subtotal - discount_total + shipping_fee`. The money
contract uses BigDecimal, whole VND HALF_UP and storage scale 2. Merchandise after
any item discount is the voucher/minimum-spend base; shipping is excluded.

The database inventory documented `vouchers` and `voucher_usages` and their fields,
but no existing migration or entity implemented them. The new migration follows
the latest existing migration `V20261006090000__td_ord04_shipping_snapshot.sql`.
CurrentAccountIdProvider, existing buyer checkout role guards and owned order
projections remain the identity/authorization boundaries.

## Voucher rules

One optional voucher per checkout; codes are globally unique canonical ASCII
letters/digits/hyphen/underscore, begin with a letter/digit, and contain at most
64 characters. Input is stripped and normalized with Locale.ROOT uppercase;
raw input is bounded to 128 characters. Null/blank means no voucher.

SHOP requires a target shop and applies only to that shop. PLATFORM has no target
shop and applies to any otherwise eligible single-shop checkout. There is no
stacking or platform shop allowlist in the supplied repository contract.

Rules are persisted: scope, type, value, optional max_discount, min_subtotal,
starts_at, ends_at, positive total_limit/per_user_limit, active, creator,
timestamps and optimistic version. FIXED is positive whole VND; PERCENTAGE is
greater than zero and at most 100, stored as NUMERIC without lossy rate rounding.
Start is inclusive; end is exclusive. Instants use UTC/microsecond precision.

Unknown, malformed, inactive, future, expired, wrong-shop, below-minimum, exhausted
global quota and exhausted buyer limit produce catalogued public errors. Rules
never come from browser amounts or eligibility claims.

For FIXED use persisted value. For PERCENTAGE use subtotal * value / 100 at full
precision. Apply optional maximum and cap at subtotal, then Money.round once.
The applied discount cannot exceed merchandise subtotal or make payable negative.
Commission remains Money.round((subtotal - discountTotal) * authoritative rate / 100).
The shared implementation now lives in common.money.Money; checkout.dto.Money
delegates to it, preserving its existing public API and avoiding a module cycle.

## Quote, placement and locking

JSON/form quote accepts only an optional code in addition to existing selections.
Quote resolves rules and current counts without locking/reserving a voucher or
writing a usage. Quote eligibility is advisory; later checkouts can exhaust quota.

Placement first resolves the existing no-voucher merchandise/shipping quote, then
locks and refreshes the requested voucher row, revalidates current eligibility and
both counts, and calculates discounted totals and commission. Successful placement
records a single REDEEMED usage with voucher/buyer/order/discount/time/version.
New order, usage, stock, COD payment and cart cleanup commit or roll back together.

Lock ordering extends the established placement order:

1. Active buyer; buyer/key replay or conflict before any other reads.
2. Buyer cart/selected lines.
3. Products ascending; referenced shops ascending; categories ascending.
4. Voucher PESSIMISTIC_WRITE row lock, refreshed persisted rules and quota counts.
5. New Order/items/history, usage, COD initialization (Order then Payment), cleanup.

All application redemption writers go through VoucherService. The voucher row
serializes both global and per-user count-and-insert, across buyers/processes.
At PostgreSQL READ COMMITTED, counts after a lock wait observe the preceding
checkout's committed usage. Only active REDEEMED rows count against either limit;
RELEASED rows remain audit history and consume neither quota. Counts use the (voucher_id,user_id) prefix index;
there is no independently mutable counter to drift from the audit rows.

Like InventoryService, a transaction-bound checked application guards recordUsage:
it requires the same authenticated buyer/application and can be consumed only
once in that transaction. The service is MANDATORY for both lock and write. Its
resource is cleared after commit/rollback; failed placement can retry.

The critical PostgreSQL test uses disjoint products, shops and categories for two
buyers, holds the voucher row in a third transaction, and observes both requests
waiting specifically on voucher queries before releasing it. It then asserts one
order/payment/usage, one quota error, and only the winner's stock decrement.
Additional same-buyer overlap covers duplicate-key replay and different-key safety;
the existing buyer lock serializes those requests. Sequential per-user exhaustion
tests prove the explicit buyer-limit error after rebuilding the selected cart.

## Pre-delivery cancellation release

The Master Plan requires usage to be held at successful placement, released exactly
once on successful cancellation before delivery, and retained after a post-delivery
return/refund. Usage is REDEEMED with released_at NULL at creation. Eligible
CANCEL_NEW/CANCEL_CONFIRMED changes it to RELEASED with a server Clock timestamp.
The database rejects invalid state/timestamp combinations. No usage row is deleted;
voucher/buyer/order identity, original discount and redemption time are immutable.

The existing central lifecycle hook calls VoucherService.releaseForCancellation only
after vendor authorization, state/version validation, inventory restoration and Shop
locking. Lock order is vendor account -> existing Order -> ascending Products ->
Shop -> Voucher -> VoucherUsage. Confirmation keeps Order -> Payment -> Shop.
The release boundary joins the lifecycle transaction with MANDATORY propagation,
rechecks persisted NEW/CONFIRMED status and absence of delivered_at, and derives
voucher/buyer/discount facts from the already locked Order and its persisted usage.
It never locks the buyer account or re-runs mutable voucher eligibility.

An edited, inactive, expired voucher still releases. The Voucher row serializes
release with placement's active-quota check; the locked usage's REDEEMED -> RELEASED
transition preserves the first release timestamp. Status/history, stock and release
commit or roll back together. A final history failure or outer rollback leaves the
original REDEEMED usage and consumed quota intact. Managed release flushes together
with the lifecycle Order, preserving its existing version increments.

Legacy/no-voucher Orders with no usage are a no-op. A voucher snapshot without usage,
unexpected usage without a snapshot, or mismatched buyer/voucher/discount is a
CONFLICT and rolls back cancellation; no historical backfill is invented.

Checkout replay after cancellation returns the original Order before voucher reads,
keeps usage RELEASED and never reserves quota again. Order voucher ID/code/scope and
discount snapshots, plus original usage facts, are never erased or recalculated.
Historical buyer/vendor display continues using Order snapshots. Confirmation/ready
and rejected post-delivery actions do not release usage. Return/refund functionality
remains outside this implementation and must not restore voucher quota.

## Idempotency and historical facts

Canonical voucher selection participates in the existing length-prefixed SHA-256
v1 hash. Existing null/blank no-voucher hashes are byte-for-byte preserved; successful
voucher orders did not exist in the prior production placement contract.
Case/outer-whitespace variants replay the same selection; changed/removed voucher
conflicts with the same key. Replay returns before catalog/cart/voucher/payment
reads or writes, so deactivation, edits, renamed codes and exhausted quotas cannot
consume another use or rewrite historical facts.

New voucher orders store scalar voucher_id/code/scope snapshots and the already
immutable applied amount in discount_total. The Order factory rejects a voucher
application whose amount differs from its totals. There is no live voucher relation
in Order display; both owned buyer/vendor projections and templates read only
the persisted snapshots. All-null snapshot columns remain valid for legacy and
no-voucher orders. The migration performs no historical update or usage backfill.

## Database and security

`V20261007090000__td_promo01_vouchers.sql` creates vouchers/voucher_usages, adds
nullable order snapshot columns, and enforces scope/type/code/value/window/limits/
whole-money checks, creator/shop/user/voucher FKs and unique code/usage-order.
The composite usage (order_id,user_id) FK references orders(id,buyer_id), preventing
a usage attributed to another buyer; the supporting order uniqueness is included.
History FKs do not cascade delete. Snapshot voucher_id is a logical immutable
identity; display never joins a mutable voucher. Hibernate remains validate-only.

No authentication authority, ownership rule, order transition or security
configuration changes. Buyer identity is resolved on the server. Strict JSON
binding rejects forged discount/buyer/shop/quota fields. Existing CSRF, role,
active-account and foreign-order protections remain in force. No voucher
management endpoint is introduced, so this increment grants no creation/edit
privileges. Vouchers must be provisioned through reviewed owner-controlled data;
automated tests provision isolated fixtures only.

## Changed files and purpose

All paths below are relative to the repository.

| Files | Purpose |
| --- | --- |
| src/main/java/com/uteexpress/promotion/dto/VoucherScope.java | SHOP/PLATFORM contract |
| src/main/java/com/uteexpress/promotion/dto/VoucherType.java | FIXED/PERCENTAGE contract |
| src/main/java/com/uteexpress/promotion/dto/VoucherCode.java | Shared canonical input/hash identity |
| src/main/java/com/uteexpress/promotion/dto/VoucherApplication.java | Immutable applied DTO |
| src/main/java/com/uteexpress/promotion/entity/Voucher.java | Persisted rules and deterministic eligibility/math |
| src/main/java/com/uteexpress/promotion/entity/VoucherUsage.java | Immutable redemption facts with guarded release state/evidence |
| src/main/java/com/uteexpress/promotion/repository/VoucherRepository.java | Lookup and PostgreSQL pessimistic lock |
| src/main/java/com/uteexpress/promotion/repository/VoucherUsageRepository.java | Active quota counts, locked persisted Order facts and usage |
| src/main/java/com/uteexpress/promotion/service/VoucherService.java | Preview, locked redemption and transaction-bound cancellation release |
| src/main/java/com/uteexpress/common/money/Money.java | Shared existing VND implementation |
| src/main/java/com/uteexpress/common/exception/ErrorCode.java | Safe voucher-specific messages |
| src/main/java/com/uteexpress/checkout/dto/Money.java | Compatibility facade, unchanged money semantics |
| src/main/java/com/uteexpress/checkout/dto/QuoteRequest.java | Optional bounded voucher selection |
| src/main/java/com/uteexpress/checkout/dto/CheckoutRequest.java | Bound voucher input size |
| src/main/java/com/uteexpress/checkout/dto/CheckoutQuote.java | Applied snapshot and compatible constructor |
| src/main/java/com/uteexpress/checkout/service/CheckoutQuoteService.java | Server-side voucher quote |
| src/main/java/com/uteexpress/checkout/controller/CheckoutPageController.java | Form input and actionable voucher errors |
| src/main/java/com/uteexpress/order/service/CheckoutRequestHash.java | Canonical voucher identity in existing hash |
| src/main/java/com/uteexpress/order/service/OrderPlacementService.java | Atomic discounted placement and existing cancellation release hook |
| src/main/java/com/uteexpress/order/entity/Order.java | Immutable voucher snapshots |
| src/main/java/com/uteexpress/order/dto/BuyerOrderDetail.java | Safe historical projection, compatible constructor |
| src/main/java/com/uteexpress/order/service/BuyerOrderService.java | Owned buyer snapshot display |
| src/main/java/com/uteexpress/order/service/VendorOrderService.java | Owned vendor snapshot display |
| src/main/java/com/uteexpress/order/controller/PlaceOrderPageController.java | Public voucher validation messages |
| src/main/resources/db/migration/V20261007090000__td_promo01_vouchers.sql | Additive persisted domain/usage/snapshots |
| src/main/resources/templates/checkout/quote.html | Input, authoritative discount, canonical placement selection |
| src/main/resources/templates/order/detail.html | Historical buyer voucher label |
| src/main/resources/templates/vendor/orders/detail.html | Historical vendor voucher label |
| src/test/java/com/uteexpress/promotion/VoucherTest.java | Domain, grammar, boundaries, money tests |
| src/test/java/com/uteexpress/promotion/VoucherCheckoutIT.java | PostgreSQL checkout/quota/release/rollback/replay/locking/security/UI/constraint tests |
| src/test/java/com/uteexpress/checkout/CheckoutQuoteServiceTest.java | New dependency and server-fact quote test |
| src/test/java/com/uteexpress/order/service/CheckoutRequestHashTest.java | Canonical voucher hash and malformed input |
| src/test/java/com/uteexpress/order/OrderDatabaseIT.java | Extend legacy upgrade proof through voucher migration; null snapshots/no usage backfill |
| src/test/java/com/uteexpress/FoundationHttpTest.java | Mock new persistence boundary in existing datasource-free HTTP fixture |
| src/test/java/com/uteexpress/account/controller/AddressControllerTest.java | Mock new boundary in datasource-free fixture; assertions unchanged |
| src/test/java/com/uteexpress/account/controller/ProfileControllerTest.java | Same fixture dependency integration |
| src/test/java/com/uteexpress/catalog/controller/PublicCatalogControllerTest.java | Same fixture dependency integration |
| src/test/java/com/uteexpress/catalog/controller/VendorProductControllerTest.java | Same fixture dependency integration |
| src/test/java/com/uteexpress/governance/AccountGovernanceWebTest.java | Same fixture dependency integration |
| src/test/java/com/uteexpress/governance/CategoryWebTest.java | Same fixture dependency integration |
| src/test/java/com/uteexpress/governance/ModerationWebTest.java | Same fixture dependency integration |
| src/test/java/com/uteexpress/governance/OpsDashboardTest.java | Same fixture dependency integration |
| src/test/java/com/uteexpress/governance/ShopApprovalWebTest.java | Same fixture dependency integration |
| src/test/java/com/uteexpress/identity/controller/EmailVerificationControllerTest.java | Same fixture dependency integration |
| src/test/java/com/uteexpress/identity/controller/PasswordResetControllerTest.java | Same fixture dependency integration |
| src/test/java/com/uteexpress/identity/controller/RegistrationControllerTest.java | Same fixture dependency integration |
| src/test/java/com/uteexpress/security/AuthenticationWebTest.java | Same fixture dependency integration |
| src/test/java/com/uteexpress/security/CsrfCookieWebTest.java | Same fixture dependency integration |
| src/test/java/com/uteexpress/security/SecurityFoundationTest.java | Same fixture dependency integration |
| src/test/java/com/uteexpress/shipping/ShippingConfigWebTest.java | Same fixture dependency integration |
| src/test/java/com/uteexpress/shop/controller/ShopRegistrationControllerTest.java | Same fixture dependency integration |
| src/test/java/com/uteexpress/ui/UiLayoutTest.java | Same fixture dependency integration |
| docs/order-contracts.md | Active placement/hash contract extension |
| docs/PROMO-01-vouchers.md | Architecture, implementation and review evidence |

## Explicit scope limits and review decisions

No PROMO-02 product promotion/scheduling, PROMO-03 work, voucher stacking,
voucher management UI/API, shipping assignment/lifecycle, COD collection,
return/refund, Admin/Ops cancellation, or unrelated catalog/security changes.
Voucher release on existing successful pre-delivery cancellation is part of PROMO-01.
No new cancellation authority or return/refund workflow is introduced.

HP should review the explicit choices where the supplied plan provides no tighter
rule: PLATFORM applies to all single-shop checkouts, finite positive limits are
required, one voucher per order, and inclusive start/exclusive end. The Master Plan
release-on-cancel rule is mandatory, not a deferred reviewer choice. No unresolved prerequisite was
found during inspection. Quote does not guarantee quota availability at placement.
The serialized quota row is a throughput tradeoff for predictable correctness;
large-volume counter/reservation optimization is intentionally deferred.

## Original implementation verification evidence (before release fix)

Java 21 focused gate (2026-10-07): BUILD SUCCESS, 74 Surefire + 144 PostgreSQL
Failsafe = 218 tests, zero failures/errors/skips. This includes VoucherTest,
CheckoutQuoteServiceTest, CheckoutRequestHashTest, CheckoutContractTest,
ArchitectureTest, FoundationHttpTest, PlaceOrderPageControllerTest and
VoucherCheckoutIT, OrderDatabaseIT, PlaceOrderIT, CheckoutQuoteIT, CheckoutFlowIT.
The earlier focused run found test harness expectations/proxy stubbing issues;
the full gate then identified fixture wiring and the old migration-count assertion.
These were corrected without changing unrelated production behavior or weakening
the tested contracts. The legacy migration test now additionally proves no voucher
backfill and preservation of all old columns.

Final full gate: `mvnw.cmd -Ppostgres-it clean verify` with JAVA_HOME pinned to
`C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot`. BUILD SUCCESS on
2026-10-07 at 13:39:54 Asia/Ho_Chi_Minh, duration 4 minutes 32 seconds. PostgreSQL
17.6 Testcontainers, production Flyway configuration and Hibernate validation.

| Gate | Suites | Tests | Failures | Errors | Skipped | Flakes / reruns |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Surefire | 57 | 378 | 0 | 0 | 0 | 0 / 0 |
| Failsafe PostgreSQL | 32 | 464 | 0 | 0 | 0 | 0 / 0 |
| Total | 89 | 842 | 0 | 0 | 0 | 0 / 0 |

Totals and runtime Java 21.0.12.1 were read from the fresh TEST-*.xml reports after
clean verify. The new voucher classes contribute 21 unit + 36 PostgreSQL cases;
the quote/hash suites add two more unit cases. All existing regression suites ran.
Fresh databases applied all 18 versioned migrations; repeat migration applied zero.
The legacy upgrade applied ORD-04/PROMO-01 while preserving all old values and
leaving voucher snapshot fields null and voucher_usages empty.

`git diff --check`: exit 0, no whitespace errors. New files also checked with
`git diff --no-index --check`. Final branch and HEAD/base are unchanged. All 54
changed/added paths are listed above: 40 modified tracked files and 14 untracked
new files. No staged diff. `git diff --stat` covers the 40 tracked files:
159 insertions, 50 deletions; untracked files are separately included in the
manifest/status evidence. No git add, commit, push, branch switch, merge or rebase.

Self-review covered all tracked diffs and every new source/migration/test/document:
no product promotion/scheduling or PROMO-03 implementation, client-authoritative
discount, unlocked quota write, replay usage duplication, mutable historical
voucher lookup, ownership/authority change, direct status mutation or unrelated
production change was found. The datasource-free fixture declarations and legacy
upgrade assertions are necessary integration changes; their business assertions
and existing production behavior were retained.

Evidence artifacts under ignored `target/`: `promo-01-focused.log`,
`promo-01-full.log`, `promo-01-verification.json`, `promo-01-git-status.txt`,
`promo-01-diff-stat.txt`, `promo-01-name-status.txt`, `promo-01-diff-check.txt`,
plus the Surefire/Failsafe XML reports. These are local verification artifacts.

Environment note: after the final integration test finished, cached Spring test
contexts attempted to close pools whose Testcontainers databases were already
stopped. Failsafe logged its 30-second fork-JVM shutdown timeout after System.exit(0).
Maven returned exit 0 / BUILD SUCCESS; all 842 tests passed without retries/skips.
No unrelated test-container/pool lifecycle changes were made. HTML/API behavior
was exercised with PostgreSQL-backed MockMvc; a live-browser smoke was not run.

## Release fix verification (current gate)

Root cause: the original usage model only allowed REDEEMED and counted every audit
row. The existing cancellation effects restored stock without releasing voucher quota.
The fix implements the Master Plan release rule without adding any lifecycle authority.

Nine existing PROMO-01 paths were additionally edited: VoucherUsage,
VoucherUsageRepository, VoucherRepository, VoucherService, OrderPlacementService,
V20261007090000__td_promo01_vouchers.sql, VoucherCheckoutIT, this document and
order-contracts.md. VendorOrderAuthority remains unchanged; its existing guards and
Product-before-Shop locks run before the explicit release hook. The complete change
surface remains 54 files (40 modified tracked, 14 new/untracked), with nothing staged.

The uncommitted/unshared migration was amended in accordance with
DATABASE_CONVENTIONS.md: shared/merged migrations cannot change, while private
unshared migrations can be finalized before first shared use. No shared database,
checksum repair, historical update/backfill or destructive migration was performed.
Fresh PostgreSQL schemas and the legacy upgrade pass Flyway validation and a second
no-op migration; existing history FKs and one-usage-per-Order uniqueness are retained.

VoucherCheckoutIT adds 20 PostgreSQL cases across these 13 methods:

- preDeliveryCancellationReleasesOnceAndPreservesHistoricalFacts (NEW/CONFIRMED)
- cancellationRestoresGlobalAndPerUserQuotaWithoutDeletingHistory (global/user)
- historyFailureAfterReleaseRollsBackQuotaStockStatusAndHistory
- outerRollbackDoesNotExposeReleasedQuota
- cancellationReleasesEditedInactiveExpiredVoucherWithoutEligibilityChecks
- checkoutReplayAfterCancellationNeverReconsumesQuotaOrRewritesSnapshots
- noVoucherCancellationPreservesLegacyBehaviorWithoutInventedUsage (current/legacy)
- nonCancellationAndDeniedPostDeliveryReturnNeverReleaseUsage
- corruptVoucherUsageFailsCancellationAndRollsBackInventory (missing/discount)
- releaseRequiresExistingLifecycleTransaction
- postgresEnforcesUsageStateAndReleaseTimestampConsistency (three invalid combinations)
- concurrentCancellationHasOneLifecycleEffectAndOneRelease
- placementAndCancellationUseCompatiblePostgresLocksAndNeverExceedQuota (shared/independent Products)

The existing final-global-quota, per-user-quota and duplicate-key PostgreSQL
contention tests remain intact. New concurrency cases observe pg_stat_activity lock
waits and use bounded futures plus PostgreSQL lock/statement timeouts. With shared
Products, cancellation reaches Voucher while holding Product/Shop, then placement
waits on Product and succeeds after cancellation commits. With independent resources,
both reach Voucher; placement either observes the released slot or receives exactly
VOUCHER_QUOTA before cancellation commits and succeeds on its subsequent retry.
No Product/Shop lock is acquired after Voucher.

Final focused gate: Java 21, BUILD SUCCESS, 58 Surefire + 193 PostgreSQL Failsafe =
251 tests; zero failures/errors/skips. Suites: VoucherTest, CheckoutQuoteServiceTest,
CheckoutRequestHashTest, CheckoutContractTest, ArchitectureTest; VoucherCheckoutIT,
VendorOrderIT, PlaceOrderIT, OrderDatabaseIT. The focused gate was rerun after
tightening the concurrency assertion and passed on the final code.

Full `mvnw.cmd -Ppostgres-it clean verify`: BUILD SUCCESS / exit 0 on 2026-10-07 at
14:17:48 Asia/Ho_Chi_Minh, duration 4 minutes 14 seconds. Java 21.0.12.1,
PostgreSQL 17.6 Testcontainers, production Flyway and Hibernate validation.

| Gate | Suites | Tests | Failures | Errors | Skipped | Flakes / reruns |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Surefire | 57 | 378 | 0 | 0 | 0 | 0 / 0 |
| Failsafe PostgreSQL | 32 | 484 | 0 | 0 | 0 | 0 / 0 |
| Total | 89 | 862 | 0 | 0 | 0 | 0 / 0 |

Totals and Java version were read from fresh TEST-*.xml files after clean verify;
failsafe-summary.xml reports completed=484 and timeout=false. The pre-existing
30-second fork shutdown warning reappeared after successful tests as cached contexts
closed pools for stopped containers; Maven still returned exit 0. No unrelated
test-container/pool lifecycle changes were made.

Final self-review: only REDEEMED counts against both quotas; eligible cancellation
releases once; repeat/concurrent requests preserve the first timestamp; rollback
retains active usage; edited/inactive vouchers still release; history and snapshots
remain intact; cancelled checkout replay cannot consume again. Existing lock orders
and placement contention protection remain intact. No new cancellation permission,
PROMO-02/PROMO-03, return/refund implementation or unrelated production change.

Evidence under ignored target/: promo-01-release-focused.log,
promo-01-release-full.log, promo-01-release-verification.json and fresh
Surefire/Failsafe reports. Generated target/** remains excluded from source control.
