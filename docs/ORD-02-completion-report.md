# ORD-02 Buyer Orders review report

Owner: TD. Branch: `feature/ord-02-buyer-orders`.
Audited HEAD/base and local `origin/develop`: `26f54d7f64812efdbbefb8c5f40d52c2521f1383`.
Validation resumed on 2026-10-03 with the required branch and both refs unchanged.
The dirty worktree intentionally contained the interrupted implementation. It was
reviewed in place, including all untracked files; no implementation restart or Git
staging, commit, branch switch, reset, merge, rebase, push or PR operation occurred.

## Repository audit and scope

Reviewed Order, OrderItem, OrderStatusHistory, Payment, repositories, DTOs,
lifecycle/placement services and controllers, CHK-02 and order tests/reports,
order/security/architecture/UI contracts, database inventory and migrations,
and all three repository progress documents. The original Master Plan document
is absent; the task's supplied authoritative ORD-02 scope is used. Older reports
are historical evidence, not a substitute for the current merged implementation.

The existing schema supports this task. No entity, migration, dependency, security
configuration, lifecycle mutation or placement contract was changed.

## Endpoints and authorization

- `GET /orders?page=0&size=20`: buyer-scoped order summaries and pagination metadata.
- `GET /orders/{id}`: buyer-scoped detail, snapshot items/totals/address, timeline
  and existing payment records.
- Both paths serve JSON for `Accept: application/json` and shared-layout Thymeleaf
  pages for `Accept: text/html`. Browser navigation is linked from the navbar and
  the existing checkout receipt. Existing security cache-control includes `no-store`.

BuyerOrderService enforces the existing USER-or-VENDOR buyer policy at the service
boundary. ADMIN, MANAGER and SHIPPER alone receive 403, even for an owned order;
VENDOR receives only its own purchases, as already specified by order-contracts.md.
No vendor fulfillment or privileged order access is granted.

CurrentAccountIdProvider obtains the persisted ID from UteExpressPrincipal, the same
pattern used by checkout/address/cart. The existing JWT filter checks the persisted
active account/token version and restores current roles. No subject parsing or
buyerId/userId HTTP binding is introduced. Such query parameters cannot change scope.
Unauthenticated requests retain the existing JSON 401 UNAUTHENTICATED behavior.

Detail queries `findByIdAndBuyerId` before reading any children. Missing, foreign and
nonpositive IDs all receive the same safe 404 RESOURCE_NOT_FOUND response. Malformed
numeric IDs receive the existing controlled 400 response. DTOs exclude buyer/actor
IDs, internal reasons, commission, checkout key/hash, version, inventory flags and
payment provider references/attempt keys. Templates escape all snapshot text.

Strict HTML input/not-found errors render the local `order/error` view with the same
catalogued public code/message and 400/404 status used by JSON. Review also found
that HTML role-denial attempted JSON serialization before falling back to the
security filter. A controller-local HTML 403 handler now renders ACCESS_DENIED
directly. JSON still uses the shared advice; anonymous requests still use the
existing security JSON 401 response. No global error/security configuration changed.

## Pagination and reads

Page is zero-based (default 0); size defaults to 20 and must be 1–100. Negative page,
invalid size and offsets above JPA's signed-integer maximum return 400
VALIDATION_FAILED. Malformed/overflowing numeric arguments use 400 INVALID_REQUEST.
Empty parameters follow Spring's declared defaults. Out-of-range valid pages are
empty while retaining the buyer-scoped count; no page clamping or global-table read.
Unknown sort/identity parameters do not alter the fixed server query.

Spring Data applies database limit/offset and buyer predicates to content and count.
Ordering is `created_at DESC, id DESC`; timestamp ties have a deterministic ID order.
The list loads only its page of scalar Order entities, maps explicit summaries and
does not fetch child collections, shops, catalog items or payments per row. Detail
uses one owned-order query, one item query, one history query and one payment query.
Both service reads use read-only REPEATABLE_READ transactions so multiple queries
within an HTTP read see the same PostgreSQL snapshot. GET does not mutate state.

Items use stored product/pricing snapshots, never current catalog data. Subtotal
already includes per-product reductions; discountTotal is the separate order-level
discount, and grandTotal remains subtotal - discountTotal + shippingFee. Display is
whole VND with existing formatting. Instants display in explicitly labelled UTC.

## Timeline and data limitations

Timeline is exactly the persisted `order_status_history` rows, ordered by
`created_at ASC, id ASC`, exposing only fromStatus, toStatus and createdAt. A null
fromStatus represents the recorded initial creation. CHK-02 writes that initial NEW
row. Equal timestamps, cancellations and repeated statuses after return rejection
are retained as recorded. No enum-based progress steps, future states, missing edges,
or synthetic entries from createdAt/updatedAt/deliveredAt/cancelledAt are generated.
An order without history shows an explicit empty-history message alongside current
status. It is not presented as a complete shipment/payment event history.

The order stores shopId, not a shop-name snapshot: the UI displays that reference.
It stores address and shipping fee, not shipping provider/service/assignment details.

Payment method/status belong to persisted Payment attempts (one order to many
attempts). Order has neither field, and CHK-02 creates no Payment even though it
currently accepts only COD. Consequently ORD-02 never infers COD, UNPAID or an
aggregate payment state. Detail shows an explicit absence message when records are
missing; otherwise it shows each recorded method/status/amount/creation timestamp
in creation/ID order. PaymentReadService is only a DTO read boundary after order
ownership is verified. It adds no attempt creation, payment selection or processing.

## Verification

Status: **READY FOR REVIEW**. All commands used Java 21 from
`C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot`.

| Gate | Surefire | Failsafe | Failures | Errors | Skipped | Exit |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Focused BuyerOrderServiceTest / BuyerOrderIT, after HTML fix | 8 | 34 | 0 | 0 | 0 | 0 |
| Relevant unit/web and PostgreSQL regressions | 186 | 91 | 0 | 0 | 0 | 0 |
| `.\mvnw.cmd -Ppostgres-it clean verify` (full repository) | 327 | 294 | 0 | 0 | 0 | 0 |

Focused command: `.\mvnw.cmd -Ppostgres-it -Dtest=BuyerOrderServiceTest -Dit.test=BuyerOrderIT verify`.
The 25-class regression unit/web selection includes all 19 modified HTTP fixtures,
BuyerOrderServiceTest (8), OrderDomainCoreTest (24), OrderContractTest (3),
CheckoutRequestHashTest (3), PlaceOrderPageControllerTest (2), and
CheckoutQuoteServiceTest (22). Integration selection: BuyerOrderIT (34),
PlaceOrderIT (51), OrderDatabaseIT (6). These counts overlap the full gate;
they are not additional distinct tests. Full XML totals: 55 Surefire suites and
27 Failsafe suites. Failsafe summary confirms 294 completed, zero failures/errors/
skips/flakes, no failureMessage and timeout=false. Full Maven process exited 0.

The first attempts could not access a Docker daemon. Docker Desktop was started
with approval, then the focused, regression and full gates ran against real
PostgreSQL 17.6 containers. The full integration JVM emitted a 30-second shutdown
diagnostic after all tests completed; its dump shows SpringApplicationShutdownHook
waiting in HikariPool.shutdown while closing cached application contexts. Maven
still reported BUILD SUCCESS and exited 0. This teardown diagnostic is recorded,
not counted as a failed test or suppressed by changing repository configuration.

Logs remain under `$env:TEMP`: `ord02-focused-resume.log`,
`ord02-regressions.log`, `ord02-full-clean-verify.log`. XML/dump reports are under
the ignored `target/surefire-reports` and `target/failsafe-reports` directories.

New coverage: BuyerOrderServiceTest and PostgreSQL/MockMvc BuyerOrderIT exercise
ownership, safe missing/foreign responses, USER/VENDOR and denied roles, real JWT
identity and inactive-account rejection, query-identity tampering, pagination and
ties/limits/empty pages, bounded query/entity load counts, snapshot money/address
mapping, escaped HTML/UTC formatting, recorded/absent payments and history, return
rejection timestamps, no GET mutation, and an order placed through real CHK-02.

Nineteen existing no-database HTTP fixtures each add mocks for BuyerOrderService
and PaymentReadService because every fixture uses full `@SpringBootTest` application
scanning with the `test` profile disabling datasource/JPA/Flyway. BuyerOrderService
must be mocked to wire the new controller without order repositories. Separately
scanned PaymentReadService must also be mocked because PaymentRepository is absent,
even when BuyerOrderService is mocked. All 19 fixture changes (38 mock declarations)
are necessary and retained; unnecessary removals: zero. Existing assertions remain
unchanged. ORD-02's integration tests use actual PostgreSQL and repositories.

## Final review and diff audit

- High: no unresolved IDOR, cross-buyer leak, client-identity binding,
  authorization bypass or GET mutation finding. JWT identity/active-account,
  USER/VENDOR policy, denied roles, list/detail ownership, child-query guard and
  database state invariance are covered by passing tests.
- Medium: the HTML 403 serialization/fallback finding was fixed locally and
  focused/regression/full tests passed afterward. Pagination limits and maximum
  JPA offset, deterministic timestamp/ID ordering, database page/query bounds,
  persisted-only history/payment facts, required HTML/JSON 400/404 responses and
  CHK-02 regressions are verified. No unresolved ORD-02 finding.
- Low: completion-report state and verification documentation updated; no
  unresolved naming, duplication or formatting finding.

Tracked modifications: four repository query additions, two navigation/receipt
template links and nineteen HTTP fixtures. `git diff --stat`: 25 files changed,
53 insertions(+), 3 deletions(-). The three deletions expand empty repository
interfaces; they do not remove existing functionality.

All 13 untracked ORD-02 files were reviewed explicitly:

```text
docs/ORD-02-completion-report.md
src/main/java/com/uteexpress/order/controller/BuyerOrderController.java
src/main/java/com/uteexpress/order/dto/BuyerOrderDetail.java
src/main/java/com/uteexpress/order/dto/BuyerOrderPage.java
src/main/java/com/uteexpress/order/dto/BuyerOrderSummary.java
src/main/java/com/uteexpress/order/service/BuyerOrderService.java
src/main/java/com/uteexpress/payment/dto/PaymentRecordView.java
src/main/java/com/uteexpress/payment/service/PaymentReadService.java
src/main/resources/templates/order/detail.html
src/main/resources/templates/order/error.html
src/main/resources/templates/order/list.html
src/test/java/com/uteexpress/order/BuyerOrderIT.java
src/test/java/com/uteexpress/order/service/BuyerOrderServiceTest.java
```

`git diff --check` passes; separate whitespace checks on all 13 untracked files
also produce zero diagnostics. No staged paths. No unrelated edits, new migrations,
entities, dependencies, IDE files, build files, logs, recognizable secret patterns,
debug code or scope expansion in the reviewable worktree. Generated test artifacts
stay in ignored target; validation logs stay outside the repository.

## Out of scope

PAY-01, payment processing/gateways/callbacks, COD collection, ORD-03 vendor lifecycle,
shipping assignment/tracking, admin order management, cancellation/return/refund
actions, filtering/search, notifications and new audit/history schemas. No Master
Plan changes, staging, commit, push, PR, merge, rebase or branch switch.

## Worktree command output

`git status --short` (the untracked template directory contains the three files listed above):

```text
 M src/main/java/com/uteexpress/order/repository/OrderItemRepository.java
 M src/main/java/com/uteexpress/order/repository/OrderRepository.java
 M src/main/java/com/uteexpress/order/repository/OrderStatusHistoryRepository.java
 M src/main/java/com/uteexpress/payment/repository/PaymentRepository.java
 M src/main/resources/templates/checkout/quote.html
 M src/main/resources/templates/fragments/navbar.html
 M src/test/java/com/uteexpress/FoundationHttpTest.java
 M src/test/java/com/uteexpress/account/controller/AddressControllerTest.java
 M src/test/java/com/uteexpress/account/controller/ProfileControllerTest.java
 M src/test/java/com/uteexpress/catalog/controller/PublicCatalogControllerTest.java
 M src/test/java/com/uteexpress/catalog/controller/VendorProductControllerTest.java
 M src/test/java/com/uteexpress/governance/AccountGovernanceWebTest.java
 M src/test/java/com/uteexpress/governance/CategoryWebTest.java
 M src/test/java/com/uteexpress/governance/ModerationWebTest.java
 M src/test/java/com/uteexpress/governance/OpsDashboardTest.java
 M src/test/java/com/uteexpress/governance/ShopApprovalWebTest.java
 M src/test/java/com/uteexpress/identity/controller/EmailVerificationControllerTest.java
 M src/test/java/com/uteexpress/identity/controller/PasswordResetControllerTest.java
 M src/test/java/com/uteexpress/identity/controller/RegistrationControllerTest.java
 M src/test/java/com/uteexpress/security/AuthenticationWebTest.java
 M src/test/java/com/uteexpress/security/CsrfCookieWebTest.java
 M src/test/java/com/uteexpress/security/SecurityFoundationTest.java
 M src/test/java/com/uteexpress/shipping/ShippingConfigWebTest.java
 M src/test/java/com/uteexpress/shop/controller/ShopRegistrationControllerTest.java
 M src/test/java/com/uteexpress/ui/UiLayoutTest.java
?? docs/ORD-02-completion-report.md
?? src/main/java/com/uteexpress/order/controller/BuyerOrderController.java
?? src/main/java/com/uteexpress/order/dto/BuyerOrderDetail.java
?? src/main/java/com/uteexpress/order/dto/BuyerOrderPage.java
?? src/main/java/com/uteexpress/order/dto/BuyerOrderSummary.java
?? src/main/java/com/uteexpress/order/service/BuyerOrderService.java
?? src/main/java/com/uteexpress/payment/dto/PaymentRecordView.java
?? src/main/java/com/uteexpress/payment/service/PaymentReadService.java
?? src/main/resources/templates/order/
?? src/test/java/com/uteexpress/order/BuyerOrderIT.java
?? src/test/java/com/uteexpress/order/service/BuyerOrderServiceTest.java
```

`git diff --stat` (tracked files only):

```text
 .../java/com/uteexpress/order/repository/OrderItemRepository.java     | 4 +++-
 src/main/java/com/uteexpress/order/repository/OrderRepository.java    | 4 ++++
 .../com/uteexpress/order/repository/OrderStatusHistoryRepository.java | 4 +++-
 .../java/com/uteexpress/payment/repository/PaymentRepository.java     | 4 +++-
 src/main/resources/templates/checkout/quote.html                      | 1 +
 src/main/resources/templates/fragments/navbar.html                    | 1 +
 src/test/java/com/uteexpress/FoundationHttpTest.java                  | 2 ++
 .../java/com/uteexpress/account/controller/AddressControllerTest.java | 2 ++
 .../java/com/uteexpress/account/controller/ProfileControllerTest.java | 2 ++
 .../uteexpress/catalog/controller/PublicCatalogControllerTest.java    | 2 ++
 .../uteexpress/catalog/controller/VendorProductControllerTest.java    | 2 ++
 src/test/java/com/uteexpress/governance/AccountGovernanceWebTest.java | 2 ++
 src/test/java/com/uteexpress/governance/CategoryWebTest.java          | 2 ++
 src/test/java/com/uteexpress/governance/ModerationWebTest.java        | 2 ++
 src/test/java/com/uteexpress/governance/OpsDashboardTest.java         | 2 ++
 src/test/java/com/uteexpress/governance/ShopApprovalWebTest.java      | 2 ++
 .../identity/controller/EmailVerificationControllerTest.java          | 2 ++
 .../uteexpress/identity/controller/PasswordResetControllerTest.java   | 2 ++
 .../uteexpress/identity/controller/RegistrationControllerTest.java    | 2 ++
 src/test/java/com/uteexpress/security/AuthenticationWebTest.java      | 2 ++
 src/test/java/com/uteexpress/security/CsrfCookieWebTest.java          | 2 ++
 src/test/java/com/uteexpress/security/SecurityFoundationTest.java     | 2 ++
 src/test/java/com/uteexpress/shipping/ShippingConfigWebTest.java      | 2 ++
 .../uteexpress/shop/controller/ShopRegistrationControllerTest.java    | 2 ++
 src/test/java/com/uteexpress/ui/UiLayoutTest.java                     | 2 ++
 25 files changed, 53 insertions(+), 3 deletions(-)
```
