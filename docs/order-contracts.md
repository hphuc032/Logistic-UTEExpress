# ORD-00 — Checkout / Order / Shipping contracts

Owner: Tiến Đạt. Reviewer: Quốc Đạt. Sprint S0, P0. Dependencies: ARCH-01, SEC-01.
DB-01 is the compatibility baseline. This is a contract handoff, not ORD-01 implementation.
The Master Plan is the source of truth; its I7 subtotal semantics are recorded below
from the finalized team decision. The original Master Plan DOCX is not present in this
checkout. DB-01 transcribes its E2/E3/E4 inventory.

## Authority and security

`OrderLifecycleService` is the only authority allowed to change an order status.
Controllers, Shipping, Payment and Admin must call it; never expose `setStatus` to them.
The initial creation of NEW and its initial history are deferred to ORD-01 and must remain
under the lifecycle authority. `OrderTransitionPolicy` only checks graph edges: it neither
authorizes an actor nor writes data. There is deliberately no lifecycle Spring bean yet.

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

## Checkout idempotency

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

Required commands: `.\mvnw.cmd test`, `.\mvnw.cmd package`, `git diff --check`.
