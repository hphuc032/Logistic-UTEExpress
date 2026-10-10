# SHIP-02 / ORD-05 integration proposal

QD's Shipping contract is extracted to `feature/ship-02-ord05-foundation`, based
directly on develop. This prerequisite contains no assigned-list controller/UI,
fulfillment HTTP coordinator, Order/Payment implementation or new migration.
TD owns the Order prepare/complete APIs. ORD-05 implements the agreed signatures,
the Payment delivery guard and Vendor cancellation hook below. QD's production
coordinator/endpoints and complete HTTP integration remain pending.

## 1. Transactional coordinator

Use a separate `com.uteexpress.fulfillment.service` application coordinator, owned
by QD. It may import Order, Shipping and Payment **service/DTO contracts**. Shipping
must not import Order or Payment, because existing Order -> Checkout -> Shipping
and Payment -> Checkout -> Shipping dependencies would form cycles. Order may call
Shipping's service contract. ArchitectureTest must continue to pass without exceptions.

The coordinator starts one REQUIRED transaction and requires SHIPPER. Order prepare,
Shipment mutations, COD collection and Order complete all join it; internal boundaries
use MANDATORY propagation. Any exception must escape and roll back the entire transaction.
No prepare/complete or Shipment mutation API is exposed directly as an HTTP endpoint.

## 2. Shipping contract supplied by QD

`shipping.service.ShipmentFulfillmentService` provides:

- `lockAssignedForTransition(orderId, expectedShipmentVersion, expectedStatus)`:
  locks Order first (read-only Order facts), then Shipment; resolves shipper from
  CurrentAccountIdProvider, verifies current assignment plus active account/SHIPPER,
  then validates expected Shipment state/version. Foreign/reassigned actors get 404.
- `recordPickedUp(orderId, expectedShipmentVersion)`: ASSIGNED -> PICKED_UP while
  Order is CONFIRMED and ready_at is present; server Clock supplies picked_up_at.
- `recordShipping(orderId, expectedShipmentVersion)`: PICKED_UP -> SHIPPING while
  Order is PICKED_UP; first attempt increments from 0 to 1.
- `recordDelivered(orderId, expectedShipmentVersion)`: SHIPPING -> DELIVERED while
  Order is SHIPPING; server Clock supplies delivered_at. This evidence is provisional
  until Payment and Order completion succeed in the caller's transaction.
- `requireFulfillmentEvidence(orderId, expectedShipmentVersion, expectedStatus)`:
  reloads locked DB evidence, including timestamp/attempt invariants. It never accepts
  a browser-provided Shipment facts object.

All methods require the existing write transaction and recheck the assignment.
Return `ShipmentFulfillmentFacts` containing persisted shipmentId/orderId/shipperId,
status/version, attemptCount/maxAttempts, pickedUpAt/deliveredAt. Mutations increment
Shipment version once and append AuditLog in the same transaction. No new migration
is needed. Shipping never writes orders.status.

## 3. TD prepare/complete boundary (ORD-05)

QD agrees to `prepareShipperTransition(orderId, expectedOrderVersion,
expectedShipmentVersion, action)` and `completeShipperTransition(orderId,
expectedOrderVersion, expectedShipmentVersion, action)`.
Both accept the **original source versions** from the request: prepare locks and
validates without incrementing Order version; after one Shipment mutation, complete
requires Shipment version to equal original + 1 and verifies its target evidence.
Order version must still equal the original value before complete, then increments
exactly once through the standard lifecycle. Do not pass an incremented Shipment
version into complete or derive identity/action evidence from browser facts.

Prepare accepts orderId, expectedOrderVersion, expectedShipmentVersion and one of
PICK_UP/START_SHIPPING/DELIVER. Resolve actor server-side; lock and refresh Order,
then call lockAssignedForTransition. Validate Order source/version, ready_at and the
state pair. Complete revalidates the same locked Order, expected Order version,
current identity/assignment and *persisted* Shipment evidence at version + 1.
Only then use the standard Order lifecycle to update timestamps/version/history and
publish its existing after-commit event. Do not treat a caller-created DTO/token as
authorization or bypass standard lifecycle guards.

Successful pairs:

| Action | Before Order / Shipment | After Order / Shipment |
| --- | --- | --- |
| PICK_UP | CONFIRMED / ASSIGNED | PICKED_UP / PICKED_UP |
| START_SHIPPING | PICKED_UP / PICKED_UP | SHIPPING / SHIPPING |
| DELIVER | SHIPPING / SHIPPING | DELIVERED / DELIVERED |

## 4. COD

After prepare has locked Order -> Shipment, call the existing
`PaymentService.collectCod(CodCollectionCommand(orderId, collectedAmount))` while
Order remains SHIPPING. It locks Payment, requires the single eligible UNPAID COD
record and exact persisted grand_total, and joins the transaction. Then record
Shipment DELIVERED and complete Order DELIVERED. TD's complete must verify persisted
PAID COD evidence and exact amount through PaymentReadService. Its current
`requireConfirmablePayment` requires UNPAID COD and must not be reused for delivery.
TD supplies a dedicated MANDATORY `requireCollectedCodForDelivery(orderId, total)`
guard: lock/refresh Payment rows, require exactly one COD PAID record, matching
grand_total, non-null paid_at and null expired_at. Duplicate collection,
wrong amount, stale version, audit failure or Order completion failure rolls back
Payment + Shipment + Order. The present system implements COD; do not invent an
online-payment fallback or accept client payment method/status.

## 5. Vendor cancellation of ASSIGNED Shipment

QD supplies `cancelAssignedForVendorOrder(orderId)`, MANDATORY and VENDOR-only,
with persisted Shop owner validation. TD invokes it after authorizing cancellation
and locking Order, **before inventory restore**. No Shipment is a no-op. ASSIGNED
becomes CANCELLED with version + 1 and AuditLog; any picked-up/later Shipment conflicts.
Order cancellation/history, stock/voucher release and Shipment closure must commit
or roll back together. ORD-05 wires this hook in VendorOrderAuthority before inventory
restoration. Ops cancellation remains a separate ADMIN-07 Order boundary.

Concurrent cancellation and pickup serialize on Order before Shipment. The loser
must conflict with zero partial effects. In production pickup must also complete
Order PICKED_UP before committing, so cancellation cannot observe physical pickup
with Order still CONFIRMED.

## Remaining verification after ORD-05

Add public coordinator routes with SHIPPER + CSRF, expected Order/Shipment versions,
and only action-specific input (COD amount for delivery). Run PostgreSQL tests for
all synchronized transitions, stale/duplicate requests, reassignment, wrong COD
rollback and cancellation-versus-pickup concurrency using real Order/Payment APIs.
Shipping's existing boundary tests use test-only Order state fixtures. ORD-05 adds
PostgreSQL tests using real Order/Shipment/Payment APIs, original versions, persisted
evidence, after-commit events, rollback and cancellation/pickup lock contention.
Public-route integration still needs QD verification. Keep PR #42 Draft until those gates pass.

## Merge sequence

Review and merge this small Shipping foundation PR first. TD then starts ORD-05 from
updated develop and implements prepare/complete, the Payment guard and cancellation
wiring on its own branch. QD syncs SHIP-02 #42 with develop after those prerequisites
merge and connects the coordinator/endpoints. No need to merge the incomplete #42 or
pull its read/UI changes into ORD-05. No force-push is required.

## Account eligibility locking

Fulfillment locks Order, Shipment, then the assigned users row with FOR SHARE before checking ACTIVE and the persisted SHIPPER role. Keep this lock until the outer transaction commits or rolls back; acquire Payment afterward. Account status updates conflict with FOR SHARE. Role governance locks the account FOR UPDATE before changing user_roles, so role revocation also waits. Shipping must not acquire the global roles row lock after locking the account: governance acquires role then account, which would reverse the ordering. Admin account/role operations must never acquire Order/Shipment/Payment while holding account locks. Shared account locks allow concurrent deliveries of different orders by one shipper without an account lock upgrade. A committed account lock before eligibility acquisition causes ACCESS_DENIED; a later lock waits until fulfillment ends.
