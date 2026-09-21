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

## Migration dependency — integration dependency for a later task

No Flyway migration was added or modified. DB-01 has only a schema-comment baseline
and no agreed deferred-FK mechanism. The following planned constraints remain required:

| Planned constraint | Missing parent | Owner |
| --- | --- | --- |
| fk_orders_buyer_id | users | HP |
| fk_orders_shop_id | shops | HP |
| fk_orders_commission_policy_id | commission_policies | QD |
| fk_order_items_product_id | products | HP |
| fk_order_status_history_actor_id | users | HP |

When those migrations exist, TD must add the four owned tables in dependency order,
including internal order FKs, all named CHECKs, uniqueness/indexes, money constraints,
timestamps and version DEFAULT 0. Preserve the PAID-only partial unique payment/order
index; ordinary JPA uniqueness cannot represent it. JPA annotations describe mapping
intent and do not create constraints under ddl-auto=validate.

Production startup and the PostgreSQL Spring integration profile cannot currently
validate these entities against the schema-only baseline: the four tables are absent.
This is an explicit deployment/integration dependency, not a passing database claim.
No validation configuration, existing integration test or baseline was weakened.

Snapshot column names receiver_name, phone, province_code, district and detail follow
the existing AddressSnapshot vocabulary; DB-01 lists a receiver/address snapshot
without exact columns. Strings currently use JPA's default length except TEXT reasons.
Commission rate uses an unbounded numeric declaration pending TD/QD precision review.
Confirm these details when authoring the actual migration. Item snapshots and history
are append-only in the domain API; no mutable-record update timestamp is added to them.

## Validation boundary

OrderDomainCoreTest covers mapping metadata, snapshot arithmetic/reconciliation,
payment vocabulary/attempt mappings, nullable history actor, initialization, legal
and illegal transitions, stale expected status/version, flush conflict handling,
history writes/failure, fail-closed identity/authorization, and Spring after-commit /
outer-rollback event behavior. Existing ORD-00 tests retain the exhaustive graph and
Money regressions.

Repositories are mocked; the transaction fixture exercises real Spring synchronization
and participation, not PostgreSQL durability. Real database FK/CHECK/unique enforcement,
version increments, concurrent sessions and durable history rollback need the approved
migration and PostgreSQL integration tests. Missing future workflow/UI modules are not
blockers for this domain skeleton. There are no remaining Java domain-core blockers.
