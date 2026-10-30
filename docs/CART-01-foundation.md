# CART-01 Cart foundation

Owner: Tiến Đạt. Base: `develop` at `e3de894` (PROD-00 merged).

## Repository audit

The starting working tree was clean on develop at `6e62a5a`. Fetch and
fast-forward brought it to `e3de894`. ORD-00 (PR #6), ORD-01 (PR #7) and
PROD-00 (PR #15) are merged. Cart had package placeholders only. No existing
Cart entity, repository, service, DTO, controller, template or migration
was replaced or duplicated. The original Master Plan DOCX is absent;
the implementation follows the supplied CART-01 requirements and the plan
extracts in DATABASE_SCHEMA, DATABASE_CONVENTIONS and order-contracts.

The project is a Java 21 / Spring Boot modular monolith with Controller →
Service → Repository boundaries. Modules: identity, account, catalog, shop,
engagement, cart, checkout, order, payment, promotion, shipping, notification,
governance, security and common. User persistence is `identity.UserEntity`;
product persistence and trusted prices are owned by Catalog. Authentication
uses JWT and `CurrentAccountIdProvider`; CSRF remains enabled.

## Persistence and concurrency

`V20260925022804__td_cart01_foundation.sql` adds carts and cart_items after
PROD-00. It does not modify merged migrations. PostgreSQL foreign keys enforce
user → cart and cart/product → cart_item. Unique constraints enforce one
cart per user and one line per cart/product. Quantity is non-null and positive.
Cart has an optimistic version; both tables have UTC timestamps. Selected
defaults to true, with no selection mutation endpoint in CART-01.

Identity and Catalog references are scalar IDs plus database FKs, consistent
with existing module boundaries. CartItem has a lazy JPA relationship to Cart
without delete cascades. There is no inverse collection to serialize or load
accidentally; HTTP returns immutable DTOs only.

Creation uses PostgreSQL `INSERT ... ON CONFLICT DO NOTHING`; mutations then
lock the owner's cart with `PESSIMISTIC_WRITE` until transaction completion.
Concurrent first adds and subsequent adds therefore serialize without duplicate
rows or lost increments. Quantity overflow is rejected without wrapping.
Future item mutation services must acquire the same cart lock before writes.

## HTTP contract

Both routes require USER or VENDOR and resolve the owner from the authenticated
principal in CartService. No owner/cart/item ID is accepted for selecting a cart.

| Route | Behavior |
| --- | --- |
| GET `/user/cart` | Current cart, or `{ "id": null, "items": [], "subtotal": 0 }` when not yet created; no write on GET |
| POST `/user/cart/items` | JSON `{ "productId": 123, "quantity": 2 }`; returns current CartView with HTTP 200 |

Send the existing CSRF token with POST, following the security contract.
Unknown JSON fields (including price, subtotal, userId, cartId and selected)
are rejected. Bean Validation rejects missing/nonpositive values; service
validation also protects callers outside HTTP. `getOrCreateCart()` is available
at the service boundary and always uses the current authenticated owner.

There is no product browse/detail UI implementation to attach a button to in
PROD-00, so CART-01 supplies JSON endpoints for verification and no new UI.
No form flow, template redesign or CART-02 controls are introduced.

## Catalog and scope boundaries

`CatalogQueryService.requirePurchasableProducts` is reused for product validation
and a batch current-price lookup. Subtotal is the sum of quantity × current
unit price for all cart lines, without a persisted price/total. It is a basic
cart summary, not a selected-item checkout quote, promotion or shipping total.

Catalog rejects missing/hidden products, inactive categories and unapproved
shops using RESOURCE_NOT_FOUND. Its all-or-nothing contract also means reading
a cart containing a newly unavailable product fails, and an add whose resulting
summary cannot be resolved rolls back. Complete unavailable-line display and
recovery belong to CART-02; do not substitute fake prices.

InventoryService still has no provider implementation and only defines
checkout/order locking, decrease and restore operations. Adding to a cart does
not reserve/decrease stock or impose an invented stock contract. VENDOR-02 and
later cart/checkout tasks own stock enforcement at their appropriate boundary.

## Verification

Use JDK 21 and the pinned Maven wrapper:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -Ppostgres-it clean verify
git diff --check
```

The default suite includes CartServiceTest (8 tests), with Catalog and principal
provider mocks for isolated business rules. CartDatabaseIT (10 tests) uses real
PostgreSQL, Catalog, JPA, service security and MockMvc: clean migration/rerun,
creation, duplicate add, invalid quantity/overflow, missing/unavailable product,
live price, ownership, unique/FK/check constraints, concurrent first adds,
request allowlist and CSRF. No stock mock or fake persistence is registered.

Existing HTTP-only test fixtures mock the new CartService because their test
profile deliberately disables persistence. Their assertions remain unchanged.

Final local result (2026-09-25): `-Ppostgres-it clean verify` passed, including
compile/package, 199 unit/web/architecture tests and 65 PostgreSQL integration
tests: 264 passed, zero failures/errors/skips. All 18 new Cart tests passed.
Flyway validated the clean schema and its second run applied zero migrations.
The initial shell used JDK 26; selecting the installed JDK 21 resolved the
Enforcer environment error without changing build requirements. Docker Desktop
was started for Testcontainers. `git diff --check` passed; no build artifacts
or credentials were added.

Deferred: quantity update/removal/selection UI, unavailable-product recovery,
checkout, order creation, shipping quote, payment, vouchers and promotions.
