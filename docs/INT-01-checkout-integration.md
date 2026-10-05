# INT-01 checkout integration

Base `origin/develop`: `9bd254ba019363ca1f482dd01b7df5b26006ac1c`.

## Audit and defect

The audit covered AUTH/JWT and the persisted principal, USER-01/02, vendor/catalog,
CART-01/02, SHIP-00, CHK-01/02, ORD-01/02/03, ADMIN-06 commission, PAY-01,
SecurityConfig, exception handling, Flyway migrations, Thymeleaf pages, PostgreSQL
integration tests, and `docs/order-contracts.md`.

The existing server pipeline already composes the owner-module contracts correctly:

`CurrentAccountIdProvider -> CartService -> CheckoutQuoteService -> OrderPlacementService -> BuyerOrderService`

Order placement retains the existing transaction and lock order, recalculates prices and
shipping on the server, snapshots the effective commission policy, creates the initial
`NEW` history through `OrderLifecycleService`, initializes one `UNPAID` COD payment,
decrements inventory once, and removes only the selected cart items.

One integration defect was found: product detail offered no HTML action for an
authenticated buyer to add the product to the persisted cart. A human demo therefore
required a manually constructed JSON request or URL. The fix adds a narrow
CSRF-protected form adapter to the existing `CartService`; it introduces no pricing,
ownership, availability, or persistence logic in the controller.

No DTO, repository, migration, SecurityConfig, state machine, pricing, shipping,
commission, payment, or checkout service contract needed modification.

## Integrated demo flow

1. Log in with a persisted active USER and receive the existing JWT cookie.
2. Browse the public catalog and open an active product from an approved shop and
   active category.
3. Add a quantity through the product-detail form; `CartService` derives the buyer from
   the principal and persists the cart item.
4. Open the cart, retain the selected item, choose an owned address and an active
   shipping provider/service, and request the server quote.
5. Place a COD order with the server-issued checkout key.
6. Receive one `NEW` order with immutable product, address, totals and commission
   snapshots; stock is decremented and only checked-out cart rows are removed.
7. Open My Orders and the owned order detail, including its `UNPAID` COD payment.

## Preserved invariants

- A checkout contains products from exactly one shop; mixed-shop selection is rejected.
- Buyer, address, cart and order ownership come from the authenticated principal and
  owner-module services. Extra browser identity fields are not bound.
- Price, totals, shipping, commission, order status and payment status remain
  server-authoritative. The MVC binders allow only the documented form fields.
- Checkout revalidates availability and stock under the existing PostgreSQL locks.
- Buyer plus checkout key retains the existing replay/conflict idempotency contract.
- Order snapshots are not updated after creation, and `OrderLifecycleService` remains
  the order-state authority.
- CSRF and existing role/method-security policies remain enabled and unchanged.

## Verification

The new `CheckoutFlowIT` uses PostgreSQL 17.6 and real application contracts. It creates
an active persisted USER, logs in through `/login`, uses the JWT cookie and CSRF token,
opens catalog detail, adds to the database cart, quotes, places COD, and opens buyer
history/detail. It verifies exactly one `NEW` order, snapshot values, a stock change from
10 to 8, selected-cart cleanup, commission at 7%, and one `UNPAID` COD payment. Extra
buyer, shop, price, total, commission, order-status, and payment-status parameters do
not affect persistence.

Existing PostgreSQL suites provide the negative and concurrency matrix:

- `CheckoutQuoteIT`: foreign address/cart, multi-shop, unavailable product/shop/category,
  invalid shipping, unsafe fields, authentication and CSRF.
- `PlaceOrderIT`: ownership, multi-shop, tampering, availability/stock/policy failures,
  idempotent replay, changed-payload conflict, complete rollback, duplicate concurrency,
  oversell prevention, immutable snapshots and selective cleanup.
- `BuyerOrderIT`: principal-scoped history/detail and safe foreign-order denial.
- `CodPaymentIT` and ORD-03 suites: PAY-01 initialization and lifecycle regressions.

All gates used Java 21 and Maven Wrapper 3.9.11 on 2026-10-05:

| Gate | Surefire | Failsafe | Failures | Errors | Skipped | Result |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| `mvnw.cmd clean test` | 339 | - | 0 | 0 | 0 | PASS |
| `mvnw.cmd package` | 339 | - | 0 | 0 | 0 | PASS |
| `mvnw.cmd -Ppostgres-it clean verify` | 339 | 419 | 0 | 0 | 0 | PASS |

The full PostgreSQL gate ran 758 tests. Flyway applied all existing migrations to fresh
PostgreSQL containers, Hibernate schema validation passed, and Maven finished with
`BUILD SUCCESS`. `git diff --check` passed.

## Deferred scope and notes

PROMO, shipment assignment, delivery, returns/refunds, WebSocket, dashboards and VNPAY
remain outside INT-01. No new migration is required. Testcontainers emits harmless
Hikari warnings while previously created per-suite containers shut down; all XML reports
and Maven gates record zero failures and errors.
