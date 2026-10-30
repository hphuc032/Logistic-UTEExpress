# CHK-02 Place Order review report

Branch: `feature/chk-02-place-order`.
Audited HEAD/base and local `origin/develop`: `ec8498d378d1dea5cae54beca98f80be569565cf`.
No switch, merge, rebase, cherry-pick, staging, commit, push or PR operation.

## Read-only audit

Audit passed before any edit. Working tree was clean and `ec8498d` was confirmed as
an ancestor of HEAD (HEAD was exactly that commit). No dependency blocker remained.

- CHK-01 service/controllers/DTOs, unit/IT tests and completion report preserve one
  selected shop, server prices/quantities, owned address, stock checks and SHIP-00.
- Order/OrderItem, repositories, ORD-00/01 docs/tests and migration provide immutable
  snapshots, NEW creation with initial history, whole-VND money, version and unique
  buyer/checkout key. Lifecycle writes order, items, history in that order.
- Cart owns its entities/repositories. Its mutations take a pessimistic owner-cart
  lock. Selected item IDs and quantities can be safely checked and removed under it.
- CurrentAccountIdProvider resolves the persisted authenticated principal, while
  AccountIdentityService can lock/check an active buyer; AddressQueryService enforces
  address ownership. No subject-to-ID parsing or browser buyer ID is needed.
- DatabaseInventoryService joins the caller transaction, locks ascending products,
  checks purchasability and stock, and permits one decrease of that checked batch.
- ShippingQuoteService has a database implementation and active provider/rate checks.
  Order stores fee and address snapshots; Shipment lifecycle/service persistence is
  deferred by the existing schema contract.
- DatabaseCommissionQueryService implements CommissionQueryService. The merged
  ADMIN-06 migration, policy repository, snapshot, tests and docs provide the policy
  at checkoutAt and the Order policy FK. Missing/latest inactive policy is CONFLICT.
- Idempotency contract includes product IDs/quantities, address, provider/service,
  payment method and optional voucher, with deterministic normalization and unique
  `(buyer_id, checkout_key)`. Matching hashes replay; different hashes conflict.
- There is no existing buyer order destination. The existing checkout page receives
  a placement receipt through Post/Redirect/Get, without adding order management.

## Implementation and transaction

OrderPlacementService remains in module Order to preserve ArchitectureTest's acyclic
Order -> Checkout DTO dependency. It extends the lifecycle authority and uses its
protected creation primitive. Existing `createNew` still requires trusted authorization;
later transitions/expiry remain denied by default. No status setter was introduced.

`placeOrder` is the single outer REQUIRED transaction:

1. Resolve the authenticated buyer and validate/canonicalize/hash CheckoutRequest.
2. Lock/check the active buyer row, then look up buyer/key. Replay returns here;
   mismatched hash conflicts before accessing mutable checkout state.
3. Reject unsupported ONLINE/voucher input; lock the buyer's cart and compare the
   complete selected product/quantity set against the request.
4. Re-run CHK-01 in this transaction: owned address, selected availability, inventory
   lock/check, fresh catalog prices, exactly one shop, shipping and totals.
5. Capture one server checkoutAt; call `requireEffectivePolicy(checkoutAt)` and
   calculate `Money.round((subtotal - discountTotal) * ratePercent / 100)`.
6. Decrease the previously locked inventory batch; create Order, item snapshots and
   initial `null -> NEW` history with buyer actor and checkoutAt.
7. Recheck/delete only the selected cart IDs represented by this order, flush and commit.

Locks live until the outer transaction ends. No REQUIRES_NEW, external payment,
shipment, notification or irreversible side effect occurs. A failure after flushed
stock/order/items/history/cart deletion rolls back every effect. Sequence gaps after
rollback are normal; no completed idempotency row survives.

The buyer row lock serializes duplicate requests across processes, not just in-memory.
The existing buyer/key UNIQUE remains the final guard. Cart locks serialize quantity,
selection and removal changes. Product row locks in ascending ID order prevent oversell
between different buyers; decrease uses the existing checked-batch contract.

The hash is SHA-256 over a versioned length-prefixed UTF-8 encoding. Items sort by ID;
null/blank voucher normalizes to empty, key whitespace is stripped, and service code
must match SHIP-00's exact syntax. Price, totals and key are not hash inputs. A replay
reauthorizes the active buyer and returns the persisted order even after the address,
cart, price, stock or policy changes. It never clears newly added cart rows.

JSON returns a DTO, HTTP 201 on creation and 200 on replay. Forms whitelist selection
fields, use Thymeleaf CSRF and show a receipt on the existing checkout page. Input
prices/totals/commission/ownership cannot affect stored values.

No migration was added or changed. Existing Order/OrderItem snapshot fields, database
uniqueness and constraints are reused. The previously deferred shop/product Order FKs
remain unchanged; no unrelated schema task is included.

## Verification

All commands use the Maven wrapper 3.9.11 and Temurin JDK 21.0.12.1.

| Gate | Tests | Failures | Errors | Skipped | Result |
| --- | ---: | ---: | ---: | ---: | --- |
| Focused CHK-02: CheckoutRequestHashTest + PlaceOrderIT | 3 + 48 = 51 | 0 | 0 | 0 | PASS |
| Regression unit, including architecture | 86 | 0 | 0 | 0 | PASS |
| Regression PostgreSQL/MVC | 104 | 0 | 0 | 0 | PASS |
| Focused H1/M1: CheckoutQuoteServiceTest, DatabaseCatalogQueryServiceTest, PlaceOrderPageControllerTest, CheckoutRequestHashTest | 33 | 0 | 0 | 0 | PASS |
| Focused H1/M1 PostgreSQL: PlaceOrderIT | 51 | 0 | 0 | 0 | PASS |
| Relevant regression unit suites | 103 | 0 | 0 | 0 | PASS |
| Relevant regression PostgreSQL/MVC suites | 130 | 0 | 0 | 0 | PASS |
| Full clean verify - Surefire (54 suites) | 319 | 0 | 0 | 0 | PASS |
| Full clean verify - Failsafe (25 suites) | 254 | 0 | 0 | 0 | PASS |
| Full clean verify - total | 573 | 0 | 0 | 0 | PASS |

Focused command: `./mvnw.cmd -Ppostgres-it -Dtest=CheckoutRequestHashTest -Dit.test=PlaceOrderIT verify`.

Full command: `./mvnw.cmd -Ppostgres-it clean verify`. BUILD SUCCESS, process exit 0,
04:21 min, completed 2026-10-03 11:00:42 +07:00. Totals were independently parsed
from all 79 TEST XML files and matched their testcase counts. The run completed with
zero failures/errors/skips. Flyway validated all 17 existing migrations, Hibernate
schema validation passed, and the JAR was built. Regression and focused results are
subsets/repeated runs, not added to the 573 total.

The latest relevant regression selects CheckoutQuoteServiceTest, CheckoutContractTest,
MoneyContractTest, CartServiceTest, OrderDomainCoreTest, OrderContractTest,
ShippingConfigWebTest, ShippingDemoActivationTest, ArchitectureTest,
DatabaseCatalogQueryServiceTest, PlaceOrderPageControllerTest, CheckoutRequestHashTest
and ProductServiceTest; PostgreSQL suites are CheckoutQuoteIT, CartActionsIT,
CartDatabaseIT, OrderDatabaseIT, CommissionPolicyIT, ShippingConfigIT, ModerationIT,
ShopApprovalIT, VendorProductIT and ProductCatalogIT. The combined run passed 103 unit
and 130 integration tests.

CHK-02 proves immutable server snapshots, one shop/order, effective commission,
selective cleanup, authenticated ownership/CSRF/unknown-field rejection, stale/invalid
selection rejection, rollback after stock/order/items/history/cart cleanup have all
flushed, and retry with the same key after rollback. Concurrency tests deliberately
hold a PostgreSQL product lock until both workers report database lock waits through
`pg_stat_activity`; duplicate submit returns one order/replay, while competing buyers
produce one success and one CONFLICT with correct stock and loser cart preserved. H1
adds PostgreSQL interleaving tests that hold the selected shop or category availability
row lock during placement; the concurrent suspension/deactivation cannot commit
inside that interval. Checkout uses the same shop row lock as moderation and the same
category row lock as `CategoryService.setActive`, then re-reads availability.

The initial focused run exposed a test-spy setup error: stubbing the Spring proxy
entered a MANDATORY transaction interceptor outside a transaction. Stubbing the
unwrapped target fixed that fixture. The H1/M1 integration run passed 51 cases.
The first regression found the new service absent from the no-database HTTP fixture
mocks. Adding one mock per existing fixture fixed context startup without changing
assertions. M1 preserves null sparse form slots in the immutable copied request so
Jakarta validation returns the existing controlled invalid-form response; its HTTP
integration test confirms no order, stock or cart changes, and the contiguous-index
test reaches placement. Failed attempts are not counted as successful gate runs.

Docker Desktop needed startup; actual PostgreSQL 17.6 containers ran outside the
sandbox. Build logs are in the system temporary directory: `chk02-focused.log`,
`chk02-regression.log`, `chk02-full-verify.log`. No deployed/live-browser test is claimed;
HTTP and Thymeleaf behavior is verified through Spring MockMvc with real PostgreSQL.

## Changed files

Production (paths under `src/main/`):

- `java/com/uteexpress/cart/service/CartService.java`
- `java/com/uteexpress/catalog/repository/CatalogReadRepository.java`
- `java/com/uteexpress/catalog/service/CatalogQueryService.java`
- `java/com/uteexpress/catalog/service/DatabaseCatalogQueryService.java`
- `java/com/uteexpress/checkout/controller/CheckoutPageController.java`
- `java/com/uteexpress/checkout/dto/CheckoutRequest.java`
- `java/com/uteexpress/checkout/service/CheckoutQuoteService.java`
- `java/com/uteexpress/governance/repository/CategoryRepository.java`
- `java/com/uteexpress/governance/service/CategoryQueryService.java`
- `java/com/uteexpress/governance/service/CategoryService.java`
- `java/com/uteexpress/order/controller/PlaceOrderController.java` (new)
- `java/com/uteexpress/order/controller/PlaceOrderPageController.java` (new)
- `java/com/uteexpress/order/dto/PlaceOrderResult.java` (new)
- `java/com/uteexpress/order/repository/OrderRepository.java`
- `java/com/uteexpress/order/service/CheckoutRequestHash.java` (new)
- `java/com/uteexpress/order/service/OrderLifecycleServiceImpl.java`
- `java/com/uteexpress/order/service/OrderPlacementService.java` (new)
- `java/com/uteexpress/shop/service/ShopAvailabilityService.java` (new)
- `resources/templates/checkout/quote.html`

Feature coverage (paths under `src/test/java/com/uteexpress/`):

- `order/PlaceOrderIT.java` (new)
- `order/service/CheckoutRequestHashTest.java` (new)
- `order/controller/PlaceOrderPageControllerTest.java` (new)
- `catalog/service/DatabaseCatalogQueryServiceTest.java`: availability-query fixture coverage
- `checkout/CheckoutQuoteServiceTest.java`: checkout availability query coverage
- `checkout/CheckoutQuoteIT.java`: preserve escaping and no-write checks; assert the
  newly available place-order form instead of its former absence.

The following existing HTTP fixtures each add only one `@MockitoBean` for the new
OrderPlacementService. Their `test` profile deliberately disables datasource/JPA;
they already mock other persistence services. Assertions and tested behaviors are
unchanged. CHK-02's own integration tests use actual PostgreSQL and repositories.
Paths are under `src/test/java/com/uteexpress/`:

- `FoundationHttpTest.java`
- `account/controller/AddressControllerTest.java`
- `account/controller/ProfileControllerTest.java`
- `catalog/controller/PublicCatalogControllerTest.java`
- `catalog/controller/VendorProductControllerTest.java`
- `governance/AccountGovernanceWebTest.java`
- `governance/CategoryWebTest.java`
- `governance/ModerationWebTest.java`
- `governance/OpsDashboardTest.java`
- `governance/ShopApprovalWebTest.java`
- `identity/controller/EmailVerificationControllerTest.java`
- `identity/controller/PasswordResetControllerTest.java`
- `identity/controller/RegistrationControllerTest.java`
- `security/AuthenticationWebTest.java`
- `security/CsrfCookieWebTest.java`
- `security/SecurityFoundationTest.java`
- `shipping/ShippingConfigWebTest.java`
- `shop/controller/ShopRegistrationControllerTest.java`
- `ui/UiLayoutTest.java`

Documentation: `docs/order-contracts.md` and this new report.

## Scope and operating assumptions

- COD is an accepted selection, not a Payment record or collection workflow. ONLINE
  and nonblank vouchers fail explicitly. No Payment/Shipment is created.
- A real effective commission policy and active shipping rate must be configured
  before checkout succeeds. There is no default rate or zero-fee fallback.
- Discounts remain zero as in CHK-01; commission excludes shipping and does not add
  to grand total. ADMIN-06's NUMERIC(7,4) rate is preserved without extra rounding.
- PostgreSQL's project-default READ COMMITTED isolation and normal CartService /
  InventoryService mutation boundaries are used. SQL outside those boundaries is not
  an application API. Same-buyer placements intentionally serialize on the account.
- No order detail page, payment gateway, cancellation, fulfillment or shipment
  lifecycle is introduced. The current order status may differ on a future replay
  after later workflow work, while its immutable checkout snapshots remain fixed.

Final scope review: 46 worktree paths (36 tracked modifications, 10 untracked source,
test, and report files). Root `exit` was confirmed as untracked terminal/diff output
and removed; no other untracked file was removed. No migrations, dependencies or
security configuration changed. `git diff --check` passed; whitespace inspection
also covered new files. The index is empty, changes remain unstaged, and the branch
is `feature/chk-02-place-order`.

STATUS: READY FOR RE-REVIEW. No staging, commit, push, merge or PR performed.
