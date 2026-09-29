# CHK-01 IMPLEMENTATION REPORT

Owner: Tiến Đạt

Base commit: `3969cbe35973d05756d5d6d7402c96ccab43e71b` (`origin/develop`, CART-02 merge #24).

Branch: `feature/chk-01-quote`, created directly from the verified `origin/develop`.

Workspace: `C:\Logistic-UTEExpress`. No work in the old WebExam project.

## Dependencies verified

Actual implementations were read before editing; no substitute dependency was created.

| Dependency | Evidence and reused behavior |
| --- | --- |
| CART-02 | Merge `3969cbe`; `CartService.getCurrentUserCart()` reads without creating/mutating a cart; owner-scoped item query preserves selected flags and unavailable lines. |
| USER-02 | Merge `3644de2`, implementation `bf00b09`; `AddressService` implements `AddressQueryService`, with `findByIdAndUserId` for ownership and DB address snapshots. |
| SHIP-00 | Merge `9495d26`; real `DatabaseShippingQuoteService` and `ShippingRateRepository.findQuote` validate active provider/rate and supported destination; no fallback fee. |
| ORD contracts | ORD-00 merge `8c0511f`, ORD-01 merge `6e62a5a`; reuse `CheckoutQuote`, its item/address snapshots, `Money`, and `OrderTotals`. |
| Inventory/Product | VENDOR-02 merge `5d2f033`, implementation `eba5c1f`; real `DatabaseCatalogQueryService` reads current prices and checks active product/approved shop/active category. `DatabaseInventoryService.lockAndCheck` locks/checks stock without decrementing it. |

## Implemented

- `POST /user/checkout/quote`: JSON quote for all currently selected items in the authenticated buyer's cart.
- `GET /user/checkout/view`: owned address selection and active shipping choices.
- `POST /user/checkout/view`: rendered quote, selected products grouped by shop, group totals and overall totals.
- Cart page links to the preview page. There is no place-order endpoint or button.
- `QuoteRequest` contains only `addressId`, `shippingProviderId`, `shippingServiceCode`.
- `CheckoutPreview` wraps existing per-shop `CheckoutQuote` contracts and overall `OrderTotals`.
- Existing `CheckoutRequest` is intentionally not bound: its checkout key, client product quantities, payment and voucher fields are for future submission, outside this preview's contract.

## Security/ownership

- Existing `/user/**` rules and service method authorization apply; USER/VENDOR only.
- Identity comes from `CurrentAccountIdProvider` on every request; no buyer ID is accepted.
- Cart items/quantities are read through authenticated CART-02; there is no client cart/item selector to redirect checkout into another user's cart.
- Address ownership is checked through `AddressQueryService.requireOwnedAddress(currentAccountId, addressId)` before quote creation or shipping-option lookup.
- JSON unknown fields are rejected by existing configuration. HTML form binding allows only the three selection fields. Both transports are tested against injected prices, totals, ownership, items and quantities.
- CSRF, Security configuration, authentication, logout and JWT handling are unchanged. Tests include logout invalidation and login as another buyer.

## Server-side pricing

Prices, names and shop IDs come from `CatalogQueryService.requirePurchasableProducts` after inventory validation. Cart/browser display prices are not used for quote arithmetic. `BigDecimal`, `Money.requireAmount` and `OrderTotals.calculate` enforce whole-VND amounts, scale, bounds and exact arithmetic. No floating-point money is used.

Line total = current unit price × cart quantity. Group subtotal sums its lines; group total = subtotal + authoritative shipping fee. Overall totals sum groups. Discount fields are zero; commission fields are unresolved (`null`), not fabricated values or policies. The preview is not a persistable order command.

## Address validation

Only the buyer's DB addresses are displayed. Tampered address IDs fail, and shipping receives DB province/district/detail rather than submitted address fields. USER-02 permits some province values outside SHIP-00's supported format; the page displays an unsupported-shipping empty state with HTTP 200 instead of redirecting repeatedly. The user can choose or update an address.

## Cart validation

Missing/empty carts and no selected items fail. Every selected item must have positive quantity and be available. Mixed valid/invalid selections fail as a whole; selected invalid lines are never silently dropped. Catalog batch validation rejects missing/unavailable products and invalid shops/categories. Duplicate product IDs are rejected defensively. Unselected items never enter inventory requests, grouping or totals.

Missing-product behavior is tested at the service boundary. PostgreSQL normally prevents physical deletion of a referenced cart product through its foreign key; tests do not disable that constraint.

## Inventory validation

The service calls only `InventoryService.lockAndCheck` with the complete selected batch. It never calls `decrease` or `restore`, creates no reservation, and does not mutate a Product. The existing provider locks in ascending product-ID order in a short transaction. Insufficient and zero stock fail. Tests compare complete products/cart/order/payment rows before and after preview requests.

## Shipping integration and minimal contract gap

Shipping remains owned by Quốc Đạt. Before CHK-01, `ShippingQuoteService.quote(command)` required a known provider ID and service code, while active provider/rate listing lived in `ShippingConfigService`, which is restricted at class level to ADMIN/MANAGER. There was no buyer-usable internal read contract for discovering active services for an owned destination.

The checkout UI cannot safely obtain those choices by bypassing the shipping module, weakening admin authorization, hard-coding IDs/services, or asking users to guess them. The minimal extension is:

| Shared Shipping file | Change and reason |
| --- | --- |
| `shipping/service/ShippingQuoteService.java` | Add `availableServices(provinceCode)` to the existing provider boundary; reuse `ShippingRateView`, no duplicate service/DTO. |
| `shipping/service/DatabaseShippingQuoteService.java` | Read-only implementation with the existing province format and mapping to the existing DTO. |
| `shipping/repository/ShippingRateRepository.java` | One query filtering active provider AND active rate AND destination; deterministic provider/service ordering. |

These three files add 17 lines total. Existing quote calculation and validation are unchanged. No shipping schema, write API, admin permissions, seed, pricing algorithm or fallback is added. Checkout authorizes the address before invoking the read contract. Actual quote fees still come exclusively from `quote(...)`, called once per shop. Tests cover active/inactive rates/providers, unsupported destinations and updated database fees.

## Shop grouping

ONE SHOP = ONE prospective order group. Server-resolved shop IDs determine grouping, ordered by ID. Multiple products in one shop share one shipping quote; different shops receive separate quotes/fees. The UI explicitly displays each group. No Order entity is instantiated or persisted. `docs/order-contracts.md` distinguishes this multi-group preview from the existing single-shop submit contract.

## UI

Uses shared `layout/base`, alerts, surface/button classes and responsive table containers. The buyer selects an owned address, then a provider/service, and sees escaped products/address details plus subtotals, shipping and totals. Empty address/shipping states and business-error messages are present. Product and address HTML injection is tested through rendered Thymeleaf output. No new front-end dependency/tool was installed.

UI verification uses real rendered templates through MockMvc; no new desktop/mobile browser screenshot run was performed.

## Concurrency/snapshot semantics

A quote is temporary information, not a price/stock/shipping guarantee. Inventory locks last only for the service transaction, released before controller rendering/HTTP response; `open-in-view` remains false. Cart/address/shipping can change after they are read. No lock crosses requests and no stock is reserved. CHK-02 must reload ownership, selection, current prices, stock, shipping and applicable policies in its own atomic place-order transaction; this response must never be trusted as resubmitted evidence.

## Files changed and scope classification

Checkout-owned:

- `src/main/java/com/uteexpress/checkout/controller/CheckoutController.java` (new)
- `src/main/java/com/uteexpress/checkout/controller/CheckoutPageController.java` (new)
- `src/main/java/com/uteexpress/checkout/service/CheckoutQuoteService.java` (new)
- `src/main/java/com/uteexpress/checkout/dto/QuoteRequest.java` (new)
- `src/main/java/com/uteexpress/checkout/dto/CheckoutPreview.java` (new)
- `src/main/java/com/uteexpress/checkout/dto/CheckoutQuote.java` (Javadoc only)
- `src/main/resources/templates/checkout/quote.html` (new)
- `src/test/java/com/uteexpress/checkout/CheckoutQuoteServiceTest.java` (new)
- `src/test/java/com/uteexpress/checkout/CheckoutQuoteIT.java` (new)
- `docs/order-contracts.md` (preview versus submit semantics)
- `docs/CHK-01-completion-report.md` (new)

Required integration change:

- `src/main/resources/templates/cart/view.html`: one link into checkout preview.

Shared module change:

- The three Shipping files listed above, owned by Quốc Đạt, for the minimal active-service discovery gap only.
- No Hoàng Phúc-owned implementation, Security module, database migration, build configuration or existing regression test was modified.

## Tests and quality gate

Resumed focused command:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
.\mvnw.cmd -Ppostgres-it '-Dit.test=CheckoutQuoteIT' '-Dtest=CheckoutQuoteServiceTest,ArchitectureTest' verify
```

Focused result: BUILD SUCCESS; 21 CHK-01 unit tests + 7 architecture tests + 35 PostgreSQL/MVC tests; failures/errors/skipped all zero. The invalid-shop fixture supplies the schema-required rejection reason. No test was disabled or removed. An explicit zero-stock integration case was then added for the final full suite.

Final full command (no test filters or skip flags):

```powershell
.\mvnw.cmd -Ppostgres-it clean verify
```

Final actual totals, read from all 68 `TEST-*.xml` reports and cross-checked against testcase counts and the Maven log:

| Runner | Suites | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: | ---: |
| Surefire | 49 | 295 | 0 | 0 | 0 |
| Failsafe | 19 | 169 | 0 | 0 | 0 |
| Total | 68 | 464 | 0 | 0 | 0 |

`failsafe-summary.xml` independently confirms 169 completed, zero failures/errors/skipped and no timeout. It is not counted again in the totals. Unexpected skipped tests: 0. CHK-01: 21/21 unit and 36/36 PostgreSQL/MVC PASS (57 total); architecture: 7/7 PASS. The final integration suite includes the additional zero-stock case.

New test coverage: successful authenticated quote; owned/foreign addresses and carts; no/empty selection; unavailable/missing products; insufficient/zero stock; invalid quantity and money overflow; current catalog pricing; price/subtotal/shipping/total injection; shipping fee provenance; same/different-shop grouping and exact totals; no Order/OrderItem/Payment/product/cart writes; exclusion of unselected items; CSRF; logout/login ownership; unrelated role denial; HTML escaping; unsupported province without redirect loop; shipping choice filtering; Flyway validation and fresh stock on later quotes.

CART-01/CART-02 regression: PASS — `CartServiceTest` 17/17, `CartDatabaseIT` 10/10, `CartActionsIT` 33/33; 60 total.

USER-02 regression: PASS — `AddressControllerTest` 6/6, `AddressServiceTest` 6/6, `User02AddressIT` 6/6; 18 total.

SHIP-00 regression: PASS — `ShippingConfigWebTest` 4/4, `ShippingDemoActivationTest` 1/1, `ShippingConfigIT` 9/9; 14 total.

Security regression: PASS — all seven Surefire suites in `com.uteexpress.security` (42 tests) and `Auth02AuthenticationIT` 5/5; 47 total. `OtpSecurityTest` also passes 3/3. These are subsets of the full totals, not additional tests.

Flyway/schema: PASS — full-run log confirms successful validation of 14 migrations; `DatabaseBaselineIT` 1/1 validates Flyway, no pending migrations and an idempotent second migration run (zero migrations executed). Hibernate `ddl-auto: validate` remains enabled and PostgreSQL contexts started successfully. No schema change in this task.

Compile/package/full BUILD: PASS — `.work-chk-full-verify.log` records `BUILD SUCCESS`, finished at `2026-09-28T14:40:13+07:00` (03:05 min); Maven exit code 0 was reported by the completed command. The log confirms JAR creation and Spring Boot repackaging; `target/uteexpress-0.1.0-SNAPSHOT.jar` exists (64,497,839 bytes). The subsequent PowerShell `ParserError: EmptyPipeElement` was in report aggregation, not Maven. No implementation or build rerun was needed to finalize this report.

git diff --check: PASS (exit 0). Final change inventory: 15 files (6 modified tracked files and 9 untracked source/template/test/documentation files); the plain `git diff --stat` covers only the 6 tracked files (32 insertions, 1 deletion). Index is empty. No `.tools/`, `.work-*`, `target/`, logs, screenshots or temporary files are staged or present in the change inventory. Existing committed ADMIN-00 screenshots are unchanged.

## Out of scope intentionally

- CHK-02/place order and atomic order submission
- Order/OrderItem persistence
- Payment
- Voucher
- Promotion
- Stock reservation/decrement
- Cart cleanup after order

## Risks / notes

- The chosen provider/service applies to each shop, with a separate authoritative fee per group. SHIP-00 currently rates by provider/service/destination; shop-specific logistics and independent per-shop selections are not introduced.
- Unconfigured/unsupported destinations fail safely; there is no zero-fee fallback.
- Commission fields remain null in preview; CHK-02 must resolve required commission policy before persistence.
- Adding a method to the internal Shipping interface requires future implementations to supply service discovery; the repository has one real implementation, and the full suite checks current consumers.
- Build logs and generated artifacts remain ignored (`.work-*`, `target/`, `.tools/`); none belong in a commit.
- No commit, push, PR or merge was performed.

## Status

READY FOR COMMIT — full verification and final diff checks passed. No commit, push, PR or merge performed.
