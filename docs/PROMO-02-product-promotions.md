# PROMO-02 Product Promotions

Owner: Tien Dat. Reviewer: Hoang Phuc. Dependencies: VENDOR-02 and CHK-01.

## Model and schedule

One promotion targets one Product and discounts its current base price by a persisted
percentage greater than zero and at most 100. Product and creator identities are immutable
through the application. Product price remains the base price: no scheduler rewrites it.
Promotion rows have name, percentage, active flag, UTC/microsecond start/end timestamps,
creator, timestamps and optimistic version. The window is `[starts_at, ends_at)`.
Scheduled/running/expired/disabled labels are derived rather than persisted lifecycle states.

Enabled schedules for a Product cannot overlap, including future schedules. Adjacency is
allowed. Disabled rows are retained and may overlap; enabling them rechecks the invariant.
Edits and disable affect future pricing only. Existing order price snapshots never change.
There is no promotion usage counter, reservation or cancellation effect.

The new Flyway migration is `V20261008090000__td_promo02_product_promotions.sql`, after
the 19 existing migrations and their prerequisite tables. It installs `btree_gist` in the
application schema if absent and creates a partial GiST exclusion constraint on Product
identity and `tstzrange(starts_at, ends_at, '[)')`. Migration failure is intentional if the
role cannot install/use the extension; there is no weakened fallback overlap check.
Preinstalled extensions must be accessible on the migration search path.

PostgreSQL 17.6 provides btree_gist 1.7. A disposable NOSUPERUSER database owner has been
verified to install it and create the exclusion constraint. ProductPromotionIT additionally
runs the entire migration chain, validates it, and performs a no-op second migration under
a separate non-superuser database owner. This verifies the tested role configuration;
deployment must provision equivalent database CREATE and schema DDL/extension permissions.
No credentials or shared database settings are changed.

## Ownership and locking

Vendor URLs are Product-scoped:

- `GET /vendor/products/{productId}/promotions`
- `GET /vendor/products/{productId}/promotions/new`
- `POST /vendor/products/{productId}/promotions`
- `GET /vendor/products/{productId}/promotions/{id}/edit`
- `POST /vendor/products/{productId}/promotions/{id}/update`
- `POST /vendor/products/{productId}/promotions/{id}/disable`

Catalog's ProductPromotionManagementService resolves the authenticated Vendor, approved
owned Shop and owned Product. Foreign Product IDs and promotion/Product mismatches yield
safe not-found responses, including invalid form submissions. The controller binds only
form DTO fields. URI identifiers have no writable DTO property; they remain path-derived.
Creator, Shop/owner IDs and prices cannot be mass assigned. CSRF, Vendor method guards,
Thymeleaf POST forms, escaped output and PRG remain enabled. Forms use Asia/Ho_Chi_Minh.
Updates and disable require the persisted expected version. Duplicate/overlap/stale writes
produce safe conflict feedback. Product rows, promotion rows and history are never deleted.

Management locks `Account -> Product -> approved Shop -> Promotion`. Resolve the Shop
identifier first without its lock, then lock/refresh Product before locking/revalidating Shop.
Promotion persistence mutations require the caller's transaction and this authority boundary.
They must not be called as independent mutation APIs. The Product sentinel serializes all
application schedule writers with checkout even when no promotion row exists. Exclusion
constraints separately reject overlap from independent SQL writers.

Checkout retains its existing `buyer -> cart -> Products ascending -> Shops -> Categories
-> Voucher -> new Order/Payment` ordering. Promotion reads occur while Product locks are held.
No promotion lock is required for pricing, because schedule mutations acquire Product first.
Cancellation and voucher management ordering do not acquire new promotion locks.

Dependencies stay one-way: Catalog -> Promotion service/DTO contracts. Promotion never
imports Catalog entities/repositories/services. ArchUnit rules are retained unchanged.

## Pricing and display

The SQL function `uteexpress.product_prices_at(pricingAt)` is the shared read projection
for current base price, per-unit discount and effective price. It joins an eligible persisted
schedule at an explicit timestamp. One timestamp covers every Product in the query;
search count and page queries share a microsecond server timestamp. Checkout's final
Product batch is re-read after Product/Shop/Category lock waits. Preview is advisory.

The canonical formula is:

```text
discountSnapshot = Money.round(basePrice * discountPercent / 100)
finalUnitPrice = basePrice - discountSnapshot
lineTotal = finalUnitPrice * quantity
subtotal = SUM(lineTotal)
```

Whole-VND discount rounding is HALF_UP, once per unit. Percentage precision is preserved;
quantity multiplication does not introduce another discount rounding step. A 100% promotion
can produce zero merchandise. Base price must still be positive. Checkout rejects overflow.
PromotionPricingService supplies the Java reference calculation; PostgreSQL parity tests
cover half-dong values, fractional percentages, maximum base prices and 100% discounts.

SQL filtering, counts, sorting and LIMIT/OFFSET use effective price before pagination.
Original base-price DTO fields keep their meaning; explicit discount/effective fields are
added, with compatibility constructors for existing no-promotion callers. Catalog cards,
detail, home, Shop/Category pages, cart, favorites/recent and checkout use effective prices.
Cart has current prices, without a reservation. Invalid lines still contribute zero.
Stock/moderation/Shop/Category availability behavior is preserved.

## Checkout, vouchers and history

Placement replays a matching buyer/key/hash before mutable cart, Product or promotion reads.
A new placement recalculates prices inside its existing transaction and populates existing
OrderItem `unit_price`, `discount_snapshot`, `final_unit_price`, quantity and `line_total`.
No new Order or OrderItem columns are needed. Later Product/promotion edits, disable and
expiry cannot reprice an order. Snapshot views use persisted amounts.

PROMO-01 eligibility and percentage calculations use the post-promotion subtotal.
`discount_total` remains voucher-only: Product discounts are never added again.

```text
grandTotal = subtotal - voucherDiscount + shippingFee
commissionAmount = Money.round((subtotal - voucherDiscount) * commissionRate / 100)
paymentAmount = grandTotal
```

Existing quota locks, usage, cancellation release, shipping selection/fee, stock decrement,
COD initialization and lifecycle effects remain in the same outer transaction. Quote writes
neither usage nor stock. Shipping errors still fail rather than inventing a fee.

PROMO-03 stacking/priority/allocation, ORD-05 and SHIP-02 are outside this implementation.

## Validation

PromotionTest covers Java calculation/domain/form boundaries. ProductPromotionIT covers
real migrations and non-superuser extension permissions, database checks and overlap shapes,
Vendor ownership/allowlists/version/CSRF/JWT forms, effective discovery pagination and SQL/Java
parity, schedules, current cart/engagement prices, checkout/vouchers/snapshots/replay/cancellation,
full-discount orders, rollback/retry, direct SQL overlap concurrency and application lock waits.
Activity observations clear PostgreSQL's cached statistics snapshot during each wait poll.
Existing regressions and the full Java 21 PostgreSQL gate must also pass; exact fresh report
totals and reviewed tracked/untracked file inventory are recorded in the verification report.
