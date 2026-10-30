# ORD-01 Order domain core

Owner: TD. Reviewer: QD. Implements the Java domain core on ORD-00 and DB-01.
The approved contracts, status graph, Money and OrderTotals are unchanged.

## Models and lifecycle

Order, OrderItem, OrderStatusHistory and Payment use JPA field access, Long identity
keys, scalar cross-owner IDs, Instant timestamps, STRING enums and NUMERIC(19,2)
money mappings. There are no User, Shop, Product or CommissionPolicy placeholders.
Order has a nullable-until-persisted @Version Long; Hibernate initializes and advances
it. Checkout key/hash and the buyer/key uniqueness mapping preserve the future
idempotency contract; no replay or checkout placement is implemented.

OrderItem validates immutable product/pricing snapshots and post-promotion line
arithmetic. Order creation reconciles subtotal with the supplied item snapshots.
Subtotal excludes product promotion discounts already applied to lineTotal;
discountTotal is only order-level discounts. Grand total is subtotal minus discount
plus shipping; commission base excludes shipping. Commission is a stored trusted
snapshot, not an extra buyer charge. Payment starts UNPAID with the exact trusted
OrderTotals grandTotal. UNPAID and PAID are the only approved payment states here.
No collection, online callback or payment status mutation workflow is implemented.

OrderLifecycleServiceImpl in com.uteexpress.order.service implements the existing
OrderLifecycleService interface as a top-level, deliberately unannotated class.
Order has no application-service, repository, identity or transaction dependencies.
Its applyValidatedTransition domain operation applies an already-authorized and
policy-validated target and maintains timestamps; it is not a controller API.
OrderTransitionPolicy remains the single graph definition in the service layer.
Order exposes no status setter or constructor accepting a status.
The internal createNew domain-core persistence hook creates NEW, item snapshots
and initial history atomically. It accepts no buyerId: trusted authorizeCreation
must derive the persisted buyer/actor ID from CurrentUserProvider via HP integration.
This hook is not the public CHK-02 checkout workflow.
It does not create payments, change inventory or place a checkout. Initial history
has null from_status; no initial event is emitted because ORD-00's transition
event requires a non-null previous status.

Transition work uses a REQUIRED TransactionTemplate: authentication, load,
authorization, expected status/version, graph validation, status/timestamps,
order flush and history flush. Authorization precedes state/version errors to
avoid revealing foreign-order details. Optimistic flush conflicts become CONFLICT.
The afterCommit synchronization publishes the immutable event only after the
outermost transaction commits. A returned event inside an enclosing transaction
is provisional until commit. Consumers must not dispatch that return value early.
The publisher is already after commit; consumers can use ordinary event listeners.
Delivery durability/retry/outbox remains deferred, including listener failure
after the database has committed. No exactly-once delivery is promised.

## Identity and business guards

The skeleton consumes the existing CurrentUserProvider. That interface currently
returns an opaque subject and roles, not users.id. No subject parsing, resolver,
security implementation or browser actor field was introduced.

The protected authorize and authorizeCreation hooks deny by default. They are
trusted server integration points, not browser APIs. A future implementation must
obtain persisted identity through HP's approved boundary, verify ownership/scope,
account/shop restrictions and every applicable ORD-00 action guard, and lock any
dependent evidence in the same transaction. The transition guard returns a verified
actor ID and a safe reason; unchecked browser text is never copied automatically.
Vendor/Ops cancellation reasons and other actor-specific rules remain guard duties.
Core checks also require a nonblank approved reason for confirmed cancellation,
failed-delivery cancellation and return rejection. No reason is truncated.

The internal expiry method denies until verified expiry, all payment-attempt checks
and callback serialization exist. User commands always reject EXPIRE_PAYMENT.
requireHumanActor applies only to human transition/creation paths and rejects null.
OrderStatusHistory intentionally accepts null actors for approved system actions.
The future trusted expiry boundary will use null-actor history without the human
validator; it currently denies every call and adds no scheduler or expiry workflow.
No roles alone authorize an operation. Test-only subclasses provide controlled
evidence to exercise persistence/transaction sequencing, not production permissions.

The lifecycle is intentionally not auto-registered as a Spring bean until these
integrations and the schema exist. No ORD-00 signature changed. ArchitectureTest
now permits exactly CurrentUserProvider and CurrentUser as public SEC-01
contracts at the security package root; other security implementation types remain
private. This narrow accommodation needs architecture/HP review.

## Database migration: approved phased FKs

The team explicitly approved creating the four TD-owned tables now, with every
currently valid FK and exactly three deferred FKs. Migration
`V20260923090000__td_ord01_domain_core.sql` creates `uteexpress.orders`,
`order_items`, `payments` and `order_status_history`. All current-branch and visible
origin-branch migration filenames were inspected after fetching: the highest was
SHIP-00's `20260922133000`; the chosen version is later and does not collide.
The fetched develop base remained `f6c9da8`; no additional merge/rebase was performed.

Created FKs: orders.buyer_id -> users.id; order_items.order_id -> orders.id;
payments.order_id -> orders.id; order_status_history.order_id -> orders.id;
order_status_history.actor_id -> users.id (nullable for system actions).

Exactly these three FKs are deferred:

| Required later constraint | Parent owner | Prerequisite |
| --- | --- | --- |
| fk_orders_shop_id: orders.shop_id -> shops.id | HP | Real shops migration, dependent on users |
| fk_orders_commission_policy_id: orders.commission_policy_id -> commission_policies.id | QD | Real commission_policies migration, dependent on users |
| fk_order_items_product_id: order_items.product_id -> products.id | HP | Real products migration, dependent on shops and QD categories (categories now merged) |

TD must add these named FKs in later, newly versioned migrations after the parent
migrations merge. Existing scalar IDs must be reconciled against real parent rows
before adding validated constraints. This approval defers FK creation only; it does
not remove the final schema requirements or authorize fake parent tables.

The migration preserves identity BIGINT keys, NUMERIC(19,2) money, unbounded NUMERIC
commission rate, VARCHAR(255) snapshots/enums, TEXT reasons, TIMESTAMPTZ instants,
and BIGINT version DEFAULT 0 compatible with Hibernate's nullable-before-persist
@Version. Named checks enforce approved enum vocabularies, valid money/arithmetic,
commission bounds, positive quantities and IDs, nonblank snapshots and nonnegative
version. PostgreSQL NaN money is rejected. Money scale remains two decimal places;
the Java Money boundary requires whole VND amounts before persistence.

Unique order code, buyer/checkout key, payment attempt key and nullable provider
reference are retained. The unique payment/order index applies only to PAID;
multiple UNPAID attempts remain valid. Buyer/time, shop/status, child order IDs and
history/order/time indexes support lookups. Cross-row subtotal reconciliation and
trusted payment amount remain domain responsibilities. No owner migrations, entities,
Hibernate validation settings or fake parent tables were added or changed.

## PostgreSQL verification state

`OrderDatabaseIT` adds six real PostgreSQL 17.6 Testcontainers tests through the
existing `postgres-it` profile. Coverage includes Flyway validation/repeat migration,
Hibernate validate, the exact FK set and absent deferred parents, all four entity
round trips, snapshots, version initialization/increments, stale independent sessions,
history-failure and outer-transaction rollback, after-commit events, FK/CHECK enforcement,
checkout uniqueness and the PAID-only partial payment index.

Verification on 2026-09-23 used Temurin Java 21.0.12.1 and Maven 3.9.11.
`clean test`: PASS, 148 tests, zero failures/errors/skips. `package -DskipTests`:
PASS. `Test-DatabasePlan.ps1`: PASS. `git diff --check`: PASS.
The full `-Ppostgres-it verify` run: PASS, 30 integration tests, zero failures,
errors or skips, including all six `OrderDatabaseIT` tests. PostgreSQL 17.6 started,
Flyway applied the ORD-01 migration, and Hibernate initialized successfully with
ddl-auto=validate unchanged. Docker named-pipe access required an authorized run
outside the sandbox. The integration fixture commission amount was corrected from
1.72 to 2.00 to satisfy the existing whole-VND Money contract; production validation
and database constraints were not weakened. There are no remaining verification
blockers.

## Validation boundary

OrderDomainCoreTest covers mapping metadata, snapshot arithmetic/reconciliation,
payment vocabulary/attempt mappings, nullable history actor, initialization, legal
and illegal transitions, stale expected status/version, flush conflict handling,
history writes/failure, fail-closed identity/authorization, and Spring after-commit /
outer-rollback event behavior. Existing ORD-00 tests retain the exhaustive graph and
Money regressions.

The unit-test repositories are mocked; that fixture exercises real Spring synchronization
and participation, not PostgreSQL durability. OrderDatabaseIT now supplies the real
database coverage described above; its execution status must be reported separately.
Missing future workflow/UI modules are not blockers for this domain skeleton.
