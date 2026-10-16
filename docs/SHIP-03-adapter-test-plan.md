# SHIP-03 adapter design and executable-test specification

Prepared 2026-10-15 against develop fc78b41. Status: design/test preparation only; no new HTTP endpoint, authority stub or production mutation is enabled. TD accepted failed-delivery/retry scope; the Order guard API and failure evidence policy still require agreement.

## Existing integration points and required compatibility

The fulfillment module already owns the outer transaction in ShipperFulfillmentService. Keep the failure/retry coordinator here so Shipping does not depend back on Order. Existing routes are /shipper/orders/{orderId}/pickup, /shipping and /delivered; do not overload /shipping for retry because its current source is PICKED_UP. Proposed separate /delivery-failed and /retry endpoints require route approval. Add JSON and form adapters using the same validation/CSRF rules, with one shared service operation per action.

IMPORTANT: current ShipmentFulfillmentService.transition, requireFulfillmentEvidence and Order ShipperOrderAuthority.requireTransition require attemptCount == 1 for delivery. A successful retry must permit delivery at attempt 2 through all three guards without weakening source-state, evidence, assignment or version checks. Merely implementing DELIVERY_FAILED -> SHIPPING is insufficient. QD updates Shipping guards, TD updates Order guards, and the full second-attempt COD delivery regression gates integration.

## Proposed request and response facts

Request contains only expectedOrderVersion, expectedShipmentVersion, plus validated failure reason/evidence fields once approved. Retry has no failure reason or collectedAmount. Never accept assignedShipperId, actorId, attemptCount, status, maxAttempts, recipient, order totals or received=true as authoritative. Identity comes from CurrentAccountIdProvider; assignment and attempt evidence come from locked DB rows. Unknown/duplicate form fields and JSON sensitive fields must be rejected consistently with existing application conventions.

Response reports actual persisted Order status/version and Shipment status/version/attempt count. Failure and retry leave Order SHIPPING and its version unchanged unless TD explicitly specifies a different concurrency mechanism. Do not reuse FulfillmentResult's current ov+1 assumption for these Shipping-only actions. Form success redirects to the assigned-shipment view; rendering must escape any reason text.

## Proposed transaction protocol — API names intentionally not invented

1. Validate request shape before business writes. Reject accidental outer transaction if the final contract requires a fresh transaction; TD/QD must settle this composition rule together.
2. TD's trusted guard locks/reloads Order, validates expected version, ready evidence and SHIPPING state, then obtains Shipment through QD's trusted hook. No Account lock may precede Order.
3. Shipping locks Shipment, validates current assignee and expected version, then holds persisted ACTIVE/SHIPPER eligibility until commit. Existing multi-role lock strategy remains in force.
4. Failure accepts only SHIPPING with attempt 1 or 2, pickup evidence and no delivered evidence. Persist DELIVERY_FAILED and approved reason/evidence; do not increment attempts on failure.
5. Retry accepts only DELIVERY_FAILED at attempt 1 with valid prior failure evidence; persist SHIPPING and increment attempt to 2 exactly once. Never retry at maxAttempts 2.
6. Append audit in the same transaction and perform any TD completion validation required by the agreed API. No Payment call, stock/voucher effect, Order status update, OrderStatusHistory row or fabricated Order status event for failure/retry.
7. On any exception, roll back all Shipment/evidence/audit changes. Re-read guards under locks; browser and pre-lock snapshots are not evidence.

No migration is proposed until actual failure/evidence columns and audit retention needs are agreed. Never edit a merged migration; any needed new migration must sort after latest develop at merge time.

## PostgreSQL and HTTP acceptance matrix

These are specifications for future tests against real production authorities, NOT executed tests or disabled placeholder test methods. Extend ShipmentFulfillmentIT and ShipperFulfillmentIT; TD owns ShipperOrderLifecycleIT changes.

| ID | Setup / action | Required result |
| --- | --- | --- |
| F01 | Assigned active SHIPPER, Order SHIPPING, Shipment SHIPPING attempt 1; valid failure | Shipment DELIVERY_FAILED, attempt remains 1, version +1, one audit; Order/history/payment/stock/voucher unchanged |
| F02 | F01 then retry with fresh versions | Shipment SHIPPING, attempt 2, version +1, one retry audit; Order version/status unchanged |
| F03 | F02 then exact COD delivery through real coordinator | Shipment and Order DELIVERED atomically, attempt 2, COD collected exactly once, normal history/event |
| F04 | Failure during attempt 2 then retry | Failure succeeds preserving attempt 2; retry conflicts with no writes |
| F05 | Duplicate failure/retry or stale Order/Shipment version | Conflict; no second increment/audit/payment or history effect |
| F06 | Missing/foreign/reassigned Shipment | Existing non-disclosure/authorization behavior; no state leak or write |
| F07 | Wrong source state, missing pickup, existing delivered evidence, malformed attempt/max | Reject; no partial changes |
| F08 | Blank/oversized/invalid reason or forged sensitive request fields | Validation rejects per agreed DTO contract; no business writes |
| F09 | USER/VENDOR/ADMIN/MANAGER without SHIPPER, anonymous, missing/invalid CSRF | HTTP/method security rejects; include multi-role eligible SHIPPER success control |
| F10 | Audit insert fails after mutation | Entire Shipment/evidence transaction rolls back; Order/payment unchanged |
| F11 | Wrong COD amount on second-attempt delivery | Shipment remains SHIPPING attempt 2; Order SHIPPING; payment/history/audit all roll back |
| F12 | Two retry transactions start with same versions | Exactly one succeeds, one conflicts; attempt remains 2 |
| F13 | Failure races delivery from same source version | Exactly one valid terminal outcome; no DELIVERY_FAILED with Order DELIVERED or collected COD |
| F14 | Account deactivation / SHIPPER role revocation races action, both lock acquisition orders | Persisted eligibility serialized correctly; no stale ACTIVE acceptance after revocation commits first |
| F15 | Multi-role Vendor flow versus failure/retry, both execution orders | No 40P01/lock timeout; valid business outcome and preserved eligibility |
| F16 | After exhausted failure, forged Ops pre-pickup cancellation or receipt flag | No CANCELLED/stock restoration; wait for dedicated SHIP-04 authority |
| F17 | Form and JSON valid requests; escaped reason in UI | Same business outcome and version facts, CSRF enforced, no script execution |

Concurrency tests must coordinate real transactions with barriers/latches and bounded waits, surface worker exceptions, assert committed DB state, and use lock evidence rather than sleeps alone. Each failure-path assertion includes Shipment/evidence/audit plus Order/history/Payment/stock/voucher snapshots. Do not claim coverage from mocked Order authority.

## Handoff decisions needed from TD / HP

- Trusted Order guard API, composition rule, post-write validation and error mapping for Shipping-only actions.
- Delivery guard compatibility for attempt 2 across Order and Shipping (F03/F11).
- Approved failure reason/evidence fields, persistence/retention, endpoint names and UI contract.
- Role revocation lock participation and multi-role concurrency regression ownership.

Once these are approved and the boundary is available, QD can implement actual adapters, hooks and the above tests. Full Java 21 PostgreSQL clean verify and CI on the final pushed head remain required; this document does not close SHIP-03.
