# CHK-01 MASTER PLAN FIX REPORT

Branch: `feature/chk-01-quote`. Existing PR: #27. Do not merge; wait for exact-head CI and review.

## Master Plan / T21 correction

Latest develop `77935ca` was merged with merge commit `8185cca`, without conflicts.
The implementation was reviewed after merging, including ADMIN-05, current Catalog,
Cart, Inventory, Address and Shipping contracts.

ONE CHECKOUT = ONE SHOP = ONE prospective order. A cart may contain products from
multiple shops, but all selected products in a checkout must resolve to exactly one
shop. Mixed-shop selections fail with `CONFLICT` (HTTP 409); the buyer must select
products from one shop per checkout. No partial quote or implicit split is returned.

`CheckoutPreview` now contains exactly one non-null `CheckoutQuote quote`. The JSON
contract is `quote.items`, `quote.address`, `quote.totals`, etc.; the previous list and
aggregate totals have been removed. Only CHK-01 consumers used the preview wrapper;
existing order-domain consumers of `CheckoutQuote` keep their contract.

The service resolves all selected cart items server-side, validates availability,
locks/checks inventory, reloads current purchasable Catalog snapshots and collects
all distinct shop IDs. Exactly one shop is required before invoking SHIP-00 once.
One subtotal, one shipping fee and one grand total are calculated using existing
`Money` and `OrderTotals` rules. No shipping pricing algorithm changed.

A fixed `ErrorCode.Detail.SINGLE_SHOP_CHECKOUT` message retains the existing
`CONFLICT` code and provides clear Vietnamese guidance through JSON and HTML.
`ApplicationException.publicMessage()` exposes only catalogued messages; arbitrary
exception text is never returned. Other error codes and their default messages
remain unchanged. The shared handler change is covered by the full regression gate.

## Preserved contracts

- USER/VENDOR authorization, authenticated cart ownership and owned addresses.
- Selected items only, positive quantities, whole-VND current Catalog pricing.
- JSON rejects price/subtotal/shipping/total/ownership injection; forms bind only
  address ID, provider ID and service code.
- Catalog's authoritative ACTIVE product / APPROVED shop / active category predicate
  blocks MODERATED products and SUSPENDED shops. No moderation logic is duplicated.
- SHIP-00 validates active provider/rate and destination. Unknown or inactive services
  and unsupported destinations fail without a fallback fee.
- Inventory uses only `lockAndCheck`; no decrement, reservation or restore occurs.
- No Order, OrderItem or Payment creation, cart cleanup or other cart/product writes.
- CSRF, escaped HTML, logout/login ownership and safe unsupported-province handling.
- Quotes are temporary snapshots. CHK-02 must revalidate all facts at submission.
- Discounts remain zero and commission fields unresolved (`null`).

The original minimal SHIP-00 discovery extension remains: `availableServices` on
`ShippingQuoteService`, its database implementation and an active destination query
in `ShippingRateRepository`. Admin permissions and fee calculation are unchanged.

## Required coverage

- T21-A: multiple selected products from one shop return one quote with subtotal
  250300, shipping 17000 and total 267300 through PostgreSQL/MVC. Unit coverage
  verifies exactly one call to Shipping.
- T21-B: selected products from different shops fail at service, JSON and HTML
  boundaries; JSON returns CONFLICT and no quote, HTML explains how to select one
  shop. Unit coverage proves no Shipping call on rejection.
- T21-C: unknown service, inactive rate, inactive provider and unsupported destination
  are blocked through PostgreSQL/MVC.
- ADMIN-05: MODERATED product and SUSPENDED shop selections are blocked through MVC
  using real schema statuses and required moderation reasons.
- Database snapshots before/after these requests compare every row of products,
  carts, cart_items, orders, order_items and payments, proving stock/cart unchanged
  and no order/payment creation. Existing ownership/injection/security tests remain.
- Unselected unavailable products from another shop do not enter checkout.

## Quality gates

All required gates PASS. Commands (JDK 21, Maven wrapper 3.9.11):

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd package
.\mvnw.cmd -Ppostgres-it clean verify
git diff --check
```

Actual final XML totals (74 TEST-*.xml files, testcase counts cross-checked):

| Runner | Suites | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: | ---: |
| Surefire | 52 | 314 | 0 | 0 | 0 |
| Failsafe | 22 | 196 | 0 | 0 | 0 |
| Total | 74 | 510 | 0 | 0 | 0 |

`failsafe-summary.xml` independently confirms 196 completed, no timeout, zero
failures/errors/skips/flakes. Unexpected skipped: 0. These totals represent the
final full verify run, not a sum of repeated gates.

| Gate | Result |
| --- | --- |
| clean test | BUILD SUCCESS; 314 tests, 0 failures/errors/skipped |
| package | BUILD SUCCESS; 314 tests, JAR created and Spring Boot repackaged |
| -Ppostgres-it clean verify | BUILD SUCCESS; 510 tests; 02:44 min; finished 2026-10-01T08:08:57+07:00 |
| git diff --check | PASS |

| Required coverage / regression | Actual final result |
| --- | --- |
| CHK-01 | CheckoutQuoteServiceTest 22 + CheckoutQuoteIT 43 = 65 PASS |
| T21-A same shop | PASS, one quote and one shipping fee |
| T21-B multiple selected shops | BLOCK PASS; service + JSON + HTML; no mutation |
| T21-C invalid shipping | BLOCK PASS; 4 PostgreSQL/MVC cases |
| MODERATED product | BLOCK PASS |
| SUSPENDED shop | BLOCK PASS |
| CART | CartServiceTest 17 + CartDatabaseIT 10 + CartActionsIT 33 = 60 PASS |
| USER-02 | AddressControllerTest 6 + AddressServiceTest 6 + User02AddressIT 6 = 18 PASS |
| SHIP-00 | ShippingConfigWebTest 4 + ShippingDemoActivationTest 1 + ShippingConfigIT 9 = 14 PASS |
| ADMIN-05 | ModerationWebTest 2 + ModerationIT 6 = 8 PASS |
| Security | Seven security unit suites 42 + Auth02AuthenticationIT 5 = 47 PASS; OtpSecurityTest 3 also PASS |

Regression rows are subsets of the full totals. Existing checkout/money contracts
also passed (CheckoutContractTest 3, MoneyContractTest 5).

Environment recovery: the initial invocation found JAVA_HOME pointing at JDK 26
and stopped at Enforcer before tests; all completed gates explicitly selected JDK
21. A sandbox process interruption was retried outside the sandbox. The first
PostgreSQL attempt could not initialize Docker (22 suite initialization errors).
Docker Desktop was started and postgres:17.6 availability verified; the subsequent
unfiltered clean verify passed in full. No failed attempt is presented as a pass.

## Scope and handoff

Correction changes checkout service/preview DTO/page/template/tests, the fixed
business-error detail catalog and handler, and this report plus order contracts.
No schema, moderation implementation, shipping pricing algorithm, build config,
place-order, payment, voucher or promotion implementation is changed.

Generated build output and logs are excluded from the commit. Existing local
ignored tooling/artifacts predate this correction and are not staged. New gate logs
are stored outside the repository in the system temporary directory.

Status: all local quality gates PASS; ready for the dedicated correction commit
and normal push to PR #27. Final commit/push identifiers are recorded in the handoff
after committing this report. Do not merge; exact-head CI and review remain required.
