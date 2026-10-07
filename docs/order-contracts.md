# ORD-00 — Checkout / Order / Shipping contracts

Owner: Tiến Đạt. Reviewer: Quốc Đạt. Sprint S0, P0. Dependencies: ARCH-01, SEC-01.
DB-01 is the compatibility baseline. This is a contract handoff, not ORD-01 implementation.
The Master Plan is the source of truth; its I7 subtotal semantics are recorded below
from the finalized team decision. The original Master Plan DOCX is not present in this
checkout. DB-01 transcribes its E2/E3/E4 inventory.

## Authority and security

`OrderLifecycleService` is the only authority allowed to change an order status.
Controllers, Shipping, Payment and Admin must call it; never expose `setStatus` to them.
Initial NEW creation and its history use the ORD-01 lifecycle persistence primitive.
`OrderTransitionPolicy` only checks graph edges: it neither authorizes an actor nor writes
data. `OrderPlacementService` is the single production lifecycle bean. It reuses
`OrderLifecycleServiceImpl` orchestration for CHK-02 creation and ORD-03 vendor operations;
unintegrated Shipping, return, refund, Ops and system actions remain denied.

The future implementation obtains `CurrentUser` from the existing SEC-01
`CurrentUserProvider`, using the existing `RoleCode`. It must resolve `subject` to the
persisted `users.id` through the identity boundary, not parse the subject as a number.
Never receive buyer/vendor/actor IDs, roles, ownership booleans or guard booleans from HTTP.
An order ID, expected version or address ID is a selection, never evidence of ownership.
Missing authentication maps to `UNAUTHENTICATED`; wrong actor/scope/ownership to
`ACCESS_DENIED`; invalid input to `VALIDATION_FAILED`/`INVALID_REQUEST`; stale state,
failed business guards and duplicate transitions to `CONFLICT`, using ARCH-01
`ApplicationException`/`ErrorCode`/`ErrorResponse`. Do not leak foreign order details.

Buyer uses the SEC-01 user-facing role policy (USER or VENDOR) plus order ownership;
Vendor requires VENDOR plus shop ownership; assigned shipper requires SHIPPER plus the
current assignment; Admin requires ADMIN plus the operation's scope. Ops is a business
actor in the plan, not a new role. Its exact mapping to MANAGER/ADMIN and scope needs
reviewer confirmation; until defined, implementations must deny Ops operations.
Account/shop restrictions must be queried from trusted server data, not from roles alone.

`transition(command)` rejects EXPIRE_PAYMENT even for an authenticated admin.
`expireUnpaidOnlineOrder(orderId, expectedVersion)` is exclusively for an internal trusted
scheduler, never a controller endpoint or a user-selectable system identity. This split
is a contract requirement, not an implemented scheduler/authorization check.

## State machine

`order.dto.OrderStatus` contains exactly NEW, CONFIRMED, PICKED_UP, SHIPPING, DELIVERED,
CANCELLED, RETURN_REQUESTED, RETURNED, REFUNDED. DELIVERY_FAILED is absent.
`OrderAction` encodes each from/to pair; the complete actor/guard matrix below is normative.
No self transition or other edge is allowed, including NEW directly to DELIVERED.

| Action / edge | Actor | Required server-verified conditions |
| --- | --- | --- |
| CONFIRM: NEW → CONFIRMED | Vendor | Owns shop; COD valid or online payment PAID with matching total; account/shop unrestricted |
| CANCEL_NEW: NEW → CANCELLED | Buyer | Owns order; still unconfirmed |
| CANCEL_NEW: NEW → CANCELLED | Vendor / Ops | Owns shop or permitted Ops scope; nonblank reason |
| EXPIRE_PAYMENT: NEW → CANCELLED | System | Online payment expired and unpaid; verify all attempts, no successful PAID attempt |
| PICK_UP: CONFIRMED → PICKED_UP | Assigned shipper | Current assignment exists; vendor ready_at exists; order not cancelled |
| CANCEL_CONFIRMED: CONFIRMED → CANCELLED | Vendor / Ops | Correct ownership/scope; no actual pickup; nonblank reason; resolve shipment assignment atomically |
| START_SHIPPING: PICKED_UP → SHIPPING | Assigned shipper | Current assignment; start first delivery attempt |
| DELIVER: SHIPPING → DELIVERED | Assigned shipper | Trusted successful delivery; COD collection exactly equals grand_total; update delivered_at |
| CANCEL_FAILED_DELIVERY: SHIPPING → CANCELLED | Ops | Correct scope; shipment failed under the two-attempt rule; goods physically received back by shop |
| REQUEST_RETURN: DELIVERED → RETURN_REQUESTED | Buyer | Owns order; server time within 7 days of delivered_at; no previous return request ever |
| REJECT_RETURN: RETURN_REQUESTED → DELIVERED | Vendor | Owns shop; reject pending return request; nonblank reason |
| RECEIVE_RETURN: RETURN_REQUESTED → RETURNED | Vendor | Owns shop; return request APPROVED; shop physically received goods |
| COMPLETE_REFUND: RETURNED → REFUNDED | Admin | Authorized scope; refund SUCCEEDED; nonblank proof_reference; amount equals authorized refund amount; all linked records belong to same order |

Use the original delivered_at for the return window even after rejection; rejection does
not renew the window. Contract window: `delivered_at <= now <= delivered_at + 7 * 24 hours`
using Instant/UTC. An existing rejected request still prevents a second request, matching
DB-01's unique return/order. Reasons required above must be nonblank after trimming.
Reason length limits await module schema review; never silently truncate history.

CANCELLED is terminal for fulfillment; REFUNDED is terminal. DELIVERED ends fulfillment
unless its one valid return flow starts. Cancellation refunds, if applicable, are a separate
payment concern and do not add a CANCELLED → REFUNDED order edge.

In ORD-01, each transition must validate the command (including nonnegative version),
resolve actor, load and lock/version-check authoritative order and dependent evidence,
check current status and every guard, then atomically apply status/timestamps and append
OrderStatusHistory. Recheck inside the transaction, not only before it. Concurrent
cancellation/pickup/payment callbacks must serialize; a failed guard has no side effects.
History stores actor_id (null for system), from_status, to_status, reason, created_at from
a server Clock. Inventory release must be guarded by inventory_released_at on the locked
order. No public contract takes a mutable entity or promises successful persistence today.

Actions with **no OrderStatus transition**:

- Vendor ready: update ready_at only.
- Assign/reassign shipper: modify Shipment only.
- Vendor approves return: ReturnRequest APPROVED; order stays RETURN_REQUESTED.
- Delivery failure/retry: modify shipment/attempts; order stays SHIPPING.

### ORD-03 vendor implementation

GET `/vendor/orders` and `/vendor/orders/{id}` serve vendor JSON/HTML reads. POST
`/vendor/orders/{id}/confirm`, `/ready`, `/cancel` accept JSON or form commands with
`expectedVersion`. No arbitrary target status, actor, shop, inventory or payment fields
are accepted as authority. JSON unknown fields reject; extra form fields cannot change
the operation. Existing VENDOR route policy, method authorization and CSRF apply.

The persisted principal account must be active. Shop ownership comes from
`VendorShopQueryService`: `shops.owner_id = accountId`, with the existing unique one-shop
per account and APPROVED-shop convention. Reads use shop-scoped database queries,
bounded pagination and `createdAt DESC, id DESC`; detail children are queried only after
ownership succeeds. Foreign and missing orders return RESOURCE_NOT_FOUND. Persisted
timeline/payment facts are projected without inferred entries or payment status.

Vendor CONFIRM allows NEW -> CONFIRMED only. COD requires exactly one matching,
unexpired UNPAID Payment with no paid_at, preserving PAY-01's single COD record.
ONLINE permits multiple ONLINE attempts with exactly one matching, unexpired PAID
attempt with paid_at; earlier unpaid/expired attempts do not prevent confirmation.
Missing facts or mixed COD/ONLINE attempts reject; no payment method is inferred and
no historical payment backfill or gateway is added. Pre-PAY-01 orders with no Payment
remain readable/cancellable, but cannot confirm without authoritative payment evidence.
Ready requires CONFIRMED,
matching version and absent ready_at; it updates ready_at/updated_at and version only.
No READY enum, self-transition, status history row or status-change event is introduced.

Vendor cancellation permits NEW/CONFIRMED -> CANCELLED only. It accepts the controlled
reason codes OUT_OF_STOCK or UNABLE_TO_FULFILL and stores their server-defined public
reason text. Unchecked free text does not enter history/events. Under the order lock,
inventory_released_at must be null; restore uses persisted OrderItem quantities through
InventoryService.restore, then sets inventory_released_at at the cancellation instant.
Stock, status, timestamps and exactly one status-history event commit/rollback together.
Duplicate/stale/late actions conflict with no stock or history change. Cart and Payment
records are unchanged, including an existing PAID record; refund decisions remain deferred.

Write lock order is active vendor account -> owned Order -> either ascending products
(cancellation) or ascending Payment records (confirm) -> owned Shop. Order and Shop
are refreshed under their locks; payment guards also refresh locked attempts. Shop is
locked AFTER inventory to preserve checkout's products-before-shop ordering. Expected
state/version and @Version remain enforced. History timestamps come from the server
Clock at PostgreSQL microsecond precision; status-change events publish after commit.
Optimistic conflicts, including earlier managed snapshots in joined transactions, map
to CONFLICT. Ready never creates a status-change event.

ORD-03 precedes assignment. No Shipment/assignment evidence is fabricated; current
cancellation rejects PICKED_UP and every subsequent state. SHIP-01/SHIP-02 must lock
Order before Shipment, revalidate current assignment/ready_at, and atomically perform
pickup through this lifecycle authority before introducing public pickup operations.
They must integrate action-specific guards and assignment cancellation in that same
transaction; current vendor authorization intentionally denies all shipper actions.
Physical pickup while leaving Order CONFIRMED is outside the present integration contract.

## Shipment integration (QD)

`shipping.dto.ShipmentStatus` is an integration vocabulary for QD review, not persistence.

| Shipment status | Order status / interpretation |
| --- | --- |
| ASSIGNED | CONFIRMED |
| PICKED_UP | PICKED_UP |
| SHIPPING | SHIPPING |
| DELIVERY_FAILED | SHIPPING, including while awaiting physical return |
| DELIVERED | DELIVERED, or subsequent RETURN_REQUESTED / RETURNED / REFUNDED |
| RETURNED_TO_SENDER | CANCELLED after Ops verifies shop receipt |
| CANCELLED | CANCELLED |

These are consistency rules, not an automatic status setter. For example, rejected return
goes back to DELIVERED without a second delivery. Assignment/cancellation/physical-return
operations must call the lifecycle boundary for the relevant order transition in the same
transaction; implementation is deferred to the owners' tasks.

`max_attempts = 2`; `attempt_count` starts at zero and increases when each delivery attempt
starts, not when failure is recorded. Initial PICKED_UP → SHIPPING starts attempt 1.
After DELIVERY_FAILED, retry starts SHIPPING and increments to 2 without an order
transition. Every failure requires a nonblank reason. Retry is forbidden at 2 attempts.
After exhaustion, keep Order SHIPPING until physical return. Only Ops may confirm
RETURNED_TO_SENDER after shop receipt. Shipper failure alone never restores stock.
The exact failure eligibility/evidence for Ops remains SHIP-owner policy; ORD-00 does
not accept a caller-provided `failed=true` or `received=true` as proof.

## Money and checkout

CHK-01 buyer previews use `QuoteRequest` (address and shipping selection only), read
selected quantities from the authenticated user's CART-02 cart, and return a
`CheckoutPreview` containing exactly one `CheckoutQuote` in its `quote` field.
Master Plan / T21: ONE CHECKOUT = ONE SHOP = ONE prospective order. A cart may
contain multiple shops, but selected items across shops are rejected with CONFLICT;
the buyer must select one shop for each checkout. One subtotal, one SHIP-00 shipping
quote and one total are calculated. `CheckoutRequest` is reserved for the later
place-order flow, not bound by
the preview endpoints. Preview commission fields are unresolved (`null`), discounts
are zero, and no promotion/voucher/payment/commission engine is invoked. CHK-02 must
revalidate every fact; a preview is neither a reservation nor an order command.

Currency for this contract is VND. `Money` uses BigDecimal exclusively, rounds calculated
money to whole dong using HALF_UP, then represents it with scale 2 for NUMERIC(19,2).
For example 10.49 → 10.00 and 10.50 → 11.00. Reject null/negative values **before** rounding,
including -0.01, and reject NUMERIC(19,2) overflow both before and after rounding.
This ORD-00 rounding policy fills the decision explicitly delegated by DB-01.

Already quoted/snapshotted amounts must be whole VND; `requireAmount` rejects fractional
dong instead of silently changing a quote/payment. Catalog and shipping owners must
return amounts conforming to this policy. Payment validation compares exact values,
ignoring insignificant BigDecimal scale only; 100.01 cannot pay a 100.00 order.

Calculation contract for the future checkout implementation:

1. Resolve buyer from SEC-01; validate address ownership and checkout key. Reject empty
   items, duplicate product IDs, invalid quantities, unavailable products and multiple
   distinct shop IDs. Never split one checkout into multiple shops implicitly.
2. Load safe server ProductSnapshots from CatalogQueryService. Lock/check stock through
   InventoryService in the submit transaction. Browser input has no price or total fields.
3. Snapshot each product name and unitPrice; validate positive unitPrice. Compute per-unit
   promotion discount at full precision, then `Money.round` once to discountSnapshot.
   Require `0 <= discountSnapshot <= unitPrice`; finalUnitPrice = unitPrice - discountSnapshot;
   lineTotal = finalUnitPrice * quantity. Reject overflow. Do not round again per quantity.
4. Master Plan I7: `originalLineAmount = unitPriceSnapshot * quantity` (the snapshot DTO
   names unitPriceSnapshot `unitPrice`); `productPromotionDiscount = originalLineAmount - lineTotal`.
   `subtotal = SUM(lineTotal)`: merchandise AFTER product-level promotion, never the
   original catalog-price total. `discountTotal` contains only order-level discounts applied
   AFTER subtotal; in the current required scope this is the voucher discount. Product
   promotion discount must NOT be included in discountTotal.
   Voucher eligibility, including `min_subtotal`, uses this canonical subtotal. A percentage
   order-level voucher uses subtotal as its merchandise calculation base, after product
   promotion and before the voucher, excluding shipping. Round the authorized voucher
   discount once with `Money.round`; enforce `0 <= discountTotal <= subtotal`.
   Voucher stacking, other eligibility rules and allocation remain deferred to later
   promotion/voucher contracts; no voucher engine is defined or implemented here.
5. Obtain shippingFee from ShippingQuoteService for the server-resolved destination,
   shop and selected active provider/service. No zero-fee fallback on quote failure.
6. `OrderTotals.calculate`: subtotal - discountTotal + shippingFee = grandTotal.
   All components and paymentAmount are nonnegative. No negative accounting values in this API.
7. Obtain effective commission policy at server checkoutAt via CommissionQueryService;
   snapshot its ID, rate and later calculated commissionAmount. Rate must be 0–100.
   No fallback policy is invented. Commission does not add to the buyer's grandTotal.
8. Create payment attempts only for exactly grandTotal. Recompute/validate all facts at
   submit; CheckoutQuote is informational, not a trusted resubmission or price reservation.

For example, original merchandise of 100,000 VND minus a 20,000 VND product promotion
gives lineTotal and subtotal of 80,000 VND. A 10,000 VND voucher gives discountTotal of
10,000 VND; with shippingFee of 5,000 VND, grandTotal is 75,000 VND. Subtotal of 100,000
and discountTotal of 30,000 are incorrect even though they produce the same grandTotal.
Line promotion calculation belongs to future checkout logic; OrderTotals validates supplied
amounts and arithmetic, not their provenance or aggregation from CheckoutQuote items.

## Commission

The commission base is fixed: `commissionBase = subtotal - discountTotal`.
This is the final merchandise amount after product promotion and order-level discount.
`shippingFee` is excluded. Calculate at full BigDecimal precision, then round once:

```text
commissionAmount = Money.round(commissionBase * ratePercent / 100)
```

Require `0 <= ratePercent <= 100`. Resolve the effective policy at server `checkoutAt`
through CommissionQueryService; never invent a fallback policy or accept a commission
rate/amount from the browser. Snapshot `commissionPolicyId`, `commissionRateSnapshot`
and `commissionAmount` at checkout. Later policy changes must not alter existing orders.
Commission does not add to the buyer's total:
`grandTotal = subtotal - discountTotal + shippingFee`.
Rate storage precision remains a TD/QD review point; the calculation base is not open.
No commission calculation implementation is introduced in ORD-00.

Refund entitlement (shipping/discount inclusion,
full/partial policy) also needs confirmation; the lifecycle must compare trusted authorized
refund amount with successful refund evidence, never invent or accept a browser amount.

## ORD-04 persisted shipping selection

Every newly constructed Order requires a positive `shippingProviderId` and canonical
`shippingServiceCode` matching `[A-Z][A-Z0-9_]{0,31}`. Placement re-quotes inside its
transaction: `ShippingQuote.providerId()` and `ShippingQuote.serviceCode()` become
`CheckoutQuote.shippingProviderId()` and `CheckoutQuote.shippingServiceSnapshot()`.
Order copies those trusted facts and `CheckoutQuote.totals().shippingFee()` exactly;
raw request fields and preview totals are not persistence sources.

`Order.getShippingProviderId()` and `Order.getShippingServiceCode()` expose immutable
scalar checkout facts for future SHIP-01. The nullable database columns support legacy
orders only; no defaults, backfill or live provider foreign key are introduced. Existing
orders remain readable when either fact is null. Future SHIP-01 must detect missing
facts and fail safely, without inferring a selection from fee or current configuration.
Configuration changes do not rewrite these snapshots. Same-hash checkout replay returns
the existing order before re-quoting or any side effect and never fills legacy nulls;
changed-hash replay retains CONFLICT. Shipment creation/assignment and fulfillment remain
outside ORD-04.

## Checkout idempotency

CHK-02 implements this contract in `order.service.OrderPlacementService`, retaining
the existing Order -> Checkout DTO dependency direction. Placement controllers own
`POST /user/checkout/place-order` (JSON) and `/user/checkout/view/place-order` (form).
They accept `CheckoutRequest` selections only. For a new checkout, its product/quantity
set must exactly equal the authenticated buyer's locked, selected cart lines; stale or
partial selections conflict. All prices, availability, address and shipping are checked
again through CHK-01 in the placement transaction.

PAY-01 extends new COD placement with one persisted UNPAID Payment attempt in the
same transaction, using the persisted Order totals. Matching checkout replay returns
before payment initialization; pre-PAY-01 orders without payment remain unchanged.
ONLINE and nonblank vouchers are rejected as INVALID_REQUEST; their engines remain
outside this increment. Product and order discounts remain zero.
No Shipment or later order-management workflow is created. The placement receipt has
order ID/code, current status, grand total, creation time and a replay flag.

Canonical hash v1 uses SHA-256 over UTF-8, length-prefixed fields: item count, ascending
product-ID/quantity pairs, address ID, provider ID, exact validated service code, payment
method enum name, and stripped voucher code (null/blank becomes empty). Duplicate product
IDs are invalid. Key whitespace is stripped; the key itself is not a hash field. Key
length is at most the existing VARCHAR(255). No client hash, totals or identity are accepted.

The active buyer row is locked through AccountIdentityService before the buyer/key
lookup. This serializes same-buyer placement across application instances using the
existing database lock, including retries after cart cleanup. The existing database
UNIQUE constraint remains unchanged. A matching committed hash returns immediately,
without re-reading address/cart/catalog/shipping/policy or changing stock/cart. Access
is reauthorized against the current active account on every call. A different valid
business payload conflicts even if the current cart is empty.

Creation locks the buyer, then cart, then products in ascending ID order through the
existing InventoryService. Cart mutations use the same cart lock. A single REQUIRED
transaction re-quotes, resolves commission, decreases the locked stock, persists NEW
and immutable item snapshots plus initial history through lifecycle authority,
initializes the COD Payment, and removes only the verified cart item IDs. Any failure
rolls all effects back. One
server checkoutAt (UTC, PostgreSQL microsecond precision) is shared by the effective
commission lookup and Order/history timestamps. The calculation and snapshot fields
below are unchanged. Later lifecycle operations retain their fail-closed guards.

`CheckoutRequest.checkoutKey` is always scoped to the authenticated buyer, resolved
server-side through CurrentUserProvider and the identity boundary. The key is not proof
of ownership. Future persistence must enforce `UNIQUE (buyer_id, checkout_key)`.

The server must build a deterministic canonical request representation and `requestHash`
from the business-significant checkout fields: product IDs/quantities, address selection,
shipping provider/service, payment method and optional voucher code. Hash the
server-normalized payload, with deterministic item ordering and consistent normalization
of optional values; never trust a client/browser-supplied requestHash. The same normalized
business request must produce the same hash.

- Same buyer + checkoutKey + requestHash: return the previously created Order / checkout
  result. Do not create a second Order, decrease stock again, consume a voucher again or
  create a duplicate Payment. This also applies to concurrent duplicate submissions.
- Same buyer + checkoutKey with a different normalized payload/requestHash: reject with
  `CONFLICT`; do not overwrite the original result.
- Transaction failure/rollback must not leave a falsely completed idempotency result.
  Persist completion atomically with the checkout effects in the future implementation.
- Retrying the same request after a network timeout is safe: return the committed result,
  or allow checkout to proceed if the original transaction rolled back, without duplicate
  effects. Reauthorize access using the authenticated buyer on every retry.

ORD-00 freezes this contract only; no idempotency entity, table, repository, migration
or checkout persistence implementation is introduced.

## DTOs, boundaries and ownership

All business IDs are Long. Technical times are Instant. Request records use Jakarta
validation; future controllers must use @Valid and services must validate internal commands
as well. Existing JSON unknown-field rejection remains enabled. List snapshots are copied.
Read snapshots live in their owner's dto package and are internal projections, not HTTP write DTOs.

| Contract | Provider → consumer | Requirement |
| --- | --- | --- |
| CatalogQueryService / ProductSnapshot | HP → TD | Server price, shop, name, version; no Product entity; missing/unavailable item rejects batch |
| InventoryService / StockQuantity | HP → TD | Lock + check, decrease, restore in caller transaction; ascending product lock order; all-or-nothing |
| ShippingQuoteService / ShippingQuoteCommand / ShippingQuote | QD → TD | Validate address/method/provider; server fee; capture service and rate version |
| CommissionQueryService / CommissionPolicySnapshot | QD → TD | Effective global policy at checkout time; no persistence implementation |
| OrderLifecycleService / OrderTransitionCommand | TD → QD, Payment, Admin | Sole order status writer; actor resolved internally; expected status/version prevent stale writes |
| OrderEligibilityQueryService | TD → QD | Current authenticated buyer owns item and order DELIVERED; QD enforces review uniqueness |
| CheckoutRequest | Browser → TD | Key, product quantities, address, provider/service, COD/ONLINE method, optional voucher code only |
| CheckoutQuote / ItemSnapshot / AddressSnapshot / OrderTotals | TD → caller | Immutable checkout snapshots; DB-compatible names, no entities |

Review eligibility for RETURNED/REFUNDED orders is not granted by this boundary. Review
visibility after a later return is an engagement policy for QD review. No query is implemented.
Inventory restore is not authorized by a shipping failure. Later cancellation code must
lock the order and guard inventory_released_at; return restocking additionally uses the
trusted ReturnRequest.restockable rule. No inventory business logic is supplied here.

## Event contract

`OrderStatusChangedEvent(eventId, orderId, fromStatus, toStatus, actorId, occurredAt, reason)`
is an immutable internal fact produced by a successful lifecycle transaction. UUID eventId
is a correlation/deduplication key, not a replacement for BIGINT business primary keys.
actorId is resolved from SEC-01 identity and null only for the verified system-expiry path.
The event includes no CurrentUser/authorities, address, payment data, price list or mutable
JPA entity. Its reason must not embed full address or payment data either.

Write history in the transaction; dispatch externally only after commit. A rolled-back
transition emits nothing. Consumers deduplicate by eventId; delivery guarantees/outbox
are deferred, so ORD-00 does not promise exactly-once delivery. Notification DB, WebSocket,
Shipping and audit consume this same minimal fact and obtain recipient/details through
authorized queries. It is not a public WebSocket payload; consumers enforce audience access.
No event for ready/assignment/return approval/failure/retry because OrderStatus did not change.
No publisher, listener, notification service, WebSocket or outbox is implemented.

## DB-01 compatibility and review points

Read: DATABASE_SCHEMA.md, DATABASE_CONVENTIONS.md, DB-01-completion-report.md,
SEED_MANIFEST.md, database-dependencies.json, the sole schema-comment migration and all
three application YAML files. The baseline creates no Order/Payment/Shipment tables or
enum CHECKs. There is therefore **no discovered status conflict** with the supplied state
machine. No migration, seed, schema, database convention or configuration is changed.

| Contract | DB-01 alignment |
| --- | --- |
| Long IDs, Instant times | BIGINT identity/FK and TIMESTAMPTZ UTC |
| OrderStatus / ShipmentStatus | Future VARCHAR + named CHECK, EnumType.STRING; never ordinal |
| discountTotal / grandTotal | orders.discount_total / orders.grand_total (task's discount / total) |
| CheckoutQuote snapshots | orders receiver/address, commission fields; order_items product_name_snapshot, unit_price, discount_snapshot, final_unit_price, quantity, line_total |
| Payment amount | payments.amount; multiple attempts/order; eventual unique successful PAID attempt |
| Version | expectedVersion checks orders.version; later optimistic/pessimistic implementation |
| Event/history | order_status_history order_id/from_status/to_status/actor_id nullable/reason/created_at |
| Return/refund | Unique return/order and refund/order; refund/payment/return same order; preserve E3 unique payment/return references |
| Promotions/vouchers | Server calculation boundary only; no new tables, enums or altered usage/limit rules |
| Shipment | QD fields attempt_count, max_attempts, failure_reason, shipping_service_snapshot, fee_snapshot |

No full PaymentStatus, ReturnRequestStatus or RefundStatus enum is invented here; the
required PAID, APPROVED and SUCCEEDED guards are documented for their owners' later tasks.
The shipping service snapshot format and provider method validation need QD implementation.

**Cần reviewer xác nhận:**

- HP/TD: trusted mapping from CurrentUser.subject to users.id; SEC-01 does not provide it yet.
- HP/TD/QD: Ops role/scope mapping; do not invent OPS or SYSTEM RoleCode.
- TD/QD: commission rate storage precision; refund entitlement amount policy.
- QD: accept ShipmentStatus and quote interface; coordinate commission_policies migration
  before orders (already flagged by DB-01, a scheduling dependency, not a schema conflict).
- HP: accept Inventory/Catalog provider boundaries; implementation remains HP-owned.
- ARCH owner: existing ArchitectureTest only permits cross-module service/dto dependencies,
  while SEC-01 types live directly in security. Future implementations using CurrentUserProvider
  need a narrow reviewed architecture-rule accommodation. ORD-00 interfaces carry no actor
  parameter, so no rule is weakened and no duplicate security mechanism is introduced.

## Deferred work and verification boundary

ORD-01 implements Order/OrderItem/OrderStatusHistory/Payment models, lifecycle persistence,
transactions/concurrency and their tests. Later tasks implement checkout, cart, payment
callbacks, refund/return, promotion, shipping, notification and WebSocket workflows.
No repository, controller, entity, migration, endpoint or UI is added by ORD-00.

Focused tests cover exact vocabularies, every allowed action, every disallowed source/action
pair, terminal states, money arithmetic/rounding/nonnegative/overflow/payment matching,
DTO validation/immutability and absence of float/double in the contract API. Existing
architecture/security/foundation suites must continue passing. These are contract tests,
not proof of ownership enforcement, transaction safety or PostgreSQL persistence.

## PAY-01 COD boundary and Master Plan sequencing

The authoritative Master Plan excerpt supplied with PAY-01 schedules COD after
CHK-02 only. ORD-03 depends on ORD-01, ORD-02 and PAY-01; SHIP-01 depends on ORD-03,
SHIP-00 and ADMIN-02; SHIP-02 depends on SHIP-01 and PAY-01. Shipment assignment is
therefore a later integration, not a PAY-01 prerequisite. TD owns Payment and QD owns
Shipment/assignment/fulfillment. No Shipment persistence or future lifecycle is
introduced here. Earlier completion reports describe their pre-PAY-01 increments.

`payment.service.PaymentService.initializeCodForNewOrder(orderId)` is an internal
checkout boundary invoked only after explicit COD selection and flushed NEW creation.
It requires the existing transaction, locks the persisted order, reads its authoritative
totals, and creates a COD UNPAID record only when there are no attempts. There is no
HTTP initialization, lazy read initialization or automatic historical backfill.

`PaymentService.collectCod(CodCollectionCommand(orderId, collectedAmount))` is an
internal trusted fulfillment boundary, with MANDATORY transaction propagation. There
is no public shipper collection endpoint and no claim of assigned-shipper enforcement.
The caller must verify current assignment under a Shipment row lock. PAY-01 locks the
order before its payment rows, refreshes managed payment evidence under the lock, and
requires SHIPPING plus exactly one unexpired, UNPAID COD record. Both
the stored payment amount and the collected amount must equal persisted grand_total;
whole-VND BigDecimal validation rejects fractions rather than rounding evidence.
Success sets PAID, paid_at and updated_at from the server Clock; no actor/provider
audit data is invented. Repeated collection returns CONFLICT without changing facts.
The one-PAID-per-order partial unique index remains unchanged.

SHIP-02 must use one outer write transaction: lock Order, then Shipment and revalidate
current assignment, then collectCod locks Payment (Order -> Shipment -> Payment).
Collect while Order is SHIPPING, then perform the fulfillment transition through
lifecycle authority. DELIVERED is rejected: persisted delivery already requires full
COD collection, so payment cannot repair unpaid delivery after the fact. Assignment or
amount/state validation failure must roll back every fulfillment/payment effect;
never swallow failure or use a separate transaction for COD. The payment return is
provisional until the outer transaction commits. No production completion operation
exists today, so PAY-01 does not claim a currently guarded DELIVER endpoint.

Buyer `GET /orders/{orderId}/payments` returns existing PaymentRecordView records
after server-side ownership and USER/VENDOR authorization. Strict HTML redirects to
the existing ORD-02 payment table. Foreign/missing orders share the same 404 contract;
no payment attempts, secrets or inferred historical state are created by reads.

See [PAY-01 completion report](PAY-01-completion-report.md) for implementation,
verification evidence and remaining SHIP-02 responsibilities.

Required commands: `.\mvnw.cmd test`, `.\mvnw.cmd package`, `git diff --check`.
