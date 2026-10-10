# ADMIN-07 Ops cancellation — QD review, 2026-10-10

Status: proposed integration decisions, not a deployed cancellation API. Reviewed against develop f062d7e and TD's v0.1 attachment. No Order, Identity or cancellation behavior is changed by this document.

## QD-owned integration

Use a separate operations module for the controller/coordinator at POST /admin/orders/{orderId}/cancel and /manager/orders/{orderId}/cancel, with ADMIN/MANAGER and CSRF. Do not add governance -> order imports: Order -> Shipping -> governance already exists. Operations may depend on Order service/DTO contracts and governance read/audit contracts without adding a reverse dependency.

Only original expected versions and a canonical Order-owned reason enum should enter the request. Actor comes from CurrentAccountIdProvider; no owner/role/state/quantity/payment evidence is accepted. The reason vocabulary and canonical history wording must be supplied by TD and checked against the authoritative ADMIN-07 specification. Current read routes are system-wide ADMIN/MANAGER routes; HP must confirm whether cancellation shares that scope and whether restricted shops remain eligible.

Recommend expectedShipmentVersion in addition to expectedOrderVersion. Assignment/reassignment increments Shipment version without necessarily incrementing Order version. Define null as an explicit observation of no Shipment: under the Order lock, reject if a Shipment now exists. A non-null value must match the locked Shipment version. Do not silently interpret missing version as permission to cancel any current assignment.

## Shipping contract proposed by QD

An Order-owned caller invokes cancelAssignedForOpsOrder(orderId, expectedShipmentVersion, reasonCode) in its existing REQUIRED transaction; Shipping uses MANDATORY. Before mutation, lock/reload Order then Shipment, reject NEW with any Shipment, and require CONFIRMED plus clean ASSIGNED evidence (picked_up_at and delivered_at null, attempt_count zero). An absent Shipment is valid only if the command observed absence. Duplicate/stale/incompatible versions conflict. No Vendor shop-owner guard is reused.

Do not import the Order-owned reason enum into Shipping: that would create Shipping -> Order -> Checkout -> Shipping. If the hook carries a reason, use a validated scalar code under the agreed vocabulary; alternatively use a fixed Shipment audit reason OPS_ORDER_CANCELLATION while retaining the business reason in Order history/audit.

HP's eligibility boundary must validate ACTIVE persisted ADMIN/MANAGER membership and retain the account lock until outer commit. A raw method-security authority from an old principal is insufficient. Only after that check may Shipping close ASSIGNED -> CANCELLED and append exactly one Shipment audit. Shipping never writes Order status or changes Payment. The Order owner restores stock/releases vouchers and writes history/Order audit; failures propagate and roll the whole transaction back.

A prepare/complete split or an HP eligibility boundary invoked from the Shipping hook may be needed so the required Order -> Shipment -> Account ordering is actually enforced. Do not authorize from caller-provided booleans or a fabricated actor/evidence DTO.

## Verified lock-order concern

VendorOrderAuthority.lockOwnedOrder currently calls AccountIdentityService.requireActiveAccountForUpdate before orders.findByIdAndShopIdForUpdate. Shipper prepare holds Order -> Shipment before ShipmentFulfillmentService takes the assigned users row FOR SHARE. AccountIdentityService uses a write lock. For a single account holding VENDOR and SHIPPER/Ops roles, a vendor transaction can hold Account and wait for Order while fulfillment holds Order and waits for that Account. This is a concrete incompatible resource order; no claim of a reproduced deadlock is made in this review.

HP/TD need one consistent strategy, including Vendor/ready/cancellation and all Identity/governance writers. Prefer auditing a resource-first Order -> Shipment -> Account strategy: initial scope checks may be nonlocking, but canonical ownership/account eligibility must be revalidated under locks before effects. Do not fix only the new Ops hook or assume roles are exclusive. Do not take a global roles lock after the Account lock: role governance already uses role -> Account. Governance must not acquire Order/Shipment while holding its role/account locks.

Required acceptance tests use real multi-role principals, the real Vendor and Ops/fulfillment boundaries, bounded lock waits and PostgreSQL blocker observations. Cover Ops vs Vendor cancellation, pickup, assignment/reassignment, confirmation and account/role changes. The chosen strategy must avoid the Account/Order cycle and preserve authorization until commit. Order and Identity changes belong to TD/HP.

## Audit compatibility

Current AuditLogService validates a fixed metadata vocabulary. It accepts status/version but does not accept a reasonCode metadata key. Use OPS_ORDER_CANCELLED as the Order action and the canonical cancellation code in AuditEntry.reason; keep before/after metadata to accepted status/version fields. Shipment audit follows the same convention. Do not add arbitrary text or unsupported metadata and then swallow audit errors. TD supplies the canonical history reason; audit/history persistence failure must roll back all prior effects.

## What is still required

- HP confirmation of scope, restricted-shop policy and global lock ordering.
- TD's Order-owned OpsOrderCancellationCommand/reason enum/cancelForOps API and guarded lifecycle effects.
- Agreed absence/version semantics and final Shipping eligibility hook shape.
- PostgreSQL eligibility, exact-once stock/voucher/history/audit, rollback and multi-role concurrency tests.

Until these are present, PR #37 stays Draft/read-only. This review does not approve implementation of the v0.1 proposal or add failed-delivery cancellation/refunds.
