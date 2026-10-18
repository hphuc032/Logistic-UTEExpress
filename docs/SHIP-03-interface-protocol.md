# SHIP-03 — QD interface and transaction protocol proposal

Date: 2026-10-18. Base: develop 85352d9. HP-approved business contract: shipper attestation, immutable per-attempt failure evidence, first failure/retry/second failure, Order remains SHIPPING without version/history/event changes, no Payment/inventory/refund/buyer-return effects. TD has not yet published the dedicated Order guards. Names and signatures below are the concrete QD integration proposal for TD/HP confirmation, not APIs already implemented or a bypass of the missing guard.

## Operation ownership and module direction

QD agrees with two separate operations REPORT_DELIVERY_FAILURE and RETRY_DELIVERY. Do NOT add OrderAction self-transitions. Proposed enum: shipping.dto.DeliveryAttemptOperation, owned by QD and imported by TD. This keeps the existing Order -> Shipping dependency direction. Do not put the shared enum in fulfillment, because fulfillment -> Order already exists and Order -> fulfillment would create a cycle.

TD-owned OrderLifecycleService methods (both return void; no lifecycle event):

```java
void prepareShipmentOnlyOperation(Long orderId, Long expectedOrderVersion,
    Long expectedShipmentVersion, DeliveryAttemptOperation operation);
void verifyShipmentOnlyOperation(Long orderId, Long expectedOrderVersion,
    Long expectedShipmentVersion, DeliveryAttemptOperation operation);
```

Both are internal MANDATORY + SHIPPER boundaries. Both receive the ORIGINAL expected versions, not incremented values. Reject null/negative/overflow versions and unknown operation. TD locks/refreshes Order, validates SHIPPING, expected Order version and lifecycle eligibility, then calls QD's persisted source/result hook below. No Order save, version increment, history row, after-commit event or Payment call in either method. The verify method must recheck Order facts under the same retained lock.

QD-owned ShipmentAttemptService hooks (MANDATORY + SHIPPER):

```java
ShipmentDeliveryFacts lockForOperation(Long orderId, Long expectedShipmentVersion,
    DeliveryAttemptOperation operation);
ShipmentDeliveryFacts requireOperationEvidence(Long orderId, Long originalShipmentVersion,
    DeliveryAttemptOperation operation);
void reportFailure(Long orderId, Long expectedShipmentVersion,
    String failureReason, String evidenceReference);
void retryDelivery(Long orderId, Long expectedShipmentVersion);
```

TD uses only the first two read/lock hooks. QD's coordinator uses mutations after prepare and before verify. Final hook/class names may be aligned once before parallel implementation; do not implement similarly named duplicate APIs on separate branches.

Proposed immutable server DTOs in shipping.dto:
- DeliveryFailureEvidence: id, shipmentId, attemptNumber, sourceShipmentVersion, shipperId, failureReason, evidenceReference, recordedAt.
- ShipmentDeliveryFacts: existing ShipmentFulfillmentFacts plus optional DeliveryFailureEvidence for the relevant attempt.

These DTOs are snapshots, never authorization capabilities or HTTP input. Coordinator does NOT pass a constructed facts DTO to Order verify. TD verify invokes QD's hook, which reloads actual persisted Shipment/evidence. Do not extend the widely used ShipmentFulfillmentFacts constructor just to add nullable failure data; use the new wrapper to preserve existing callers.

## Transaction ownership and global locks

QD proposes a dedicated coordinator in the fulfillment module. Its public method rejects a pre-existing transaction before doing any DB work, then opens one TransactionTemplate transaction. Do not annotate that wrapper @Transactional: it would make a transaction appear active before the outer-transaction rejection check. This strengthens the Order-first invariant against joined callers already holding Account locks. Confirm this composition rule with TD/HP before implementation.

Protocol in that transaction:
1. Validate DTO shape and both expected versions; identity comes from CurrentAccountIdProvider.
2. TD prepare locks/refreshed Order first, then QD source hook locks Shipment by Order and persisted eligibility Account, in that order.
3. QD mutation reuses the retained locks, validates source/version/assignment again, writes evidence/state/audit, increments Shipment version exactly once.
4. TD verify rechecks the SAME Order version/status and reloads target Shipment/evidence with original Shipment version + 1. Failure aborts the entire transaction.
5. Commit. No Order event. Return actual Order/Shipment versions and state from server facts, not oldOrderVersion + 1.

Internal Shipping hooks may re-lock the same Order before Shipment for defense, but must never first acquire Account or introduce Account -> Order. Actor Account and role/status rechecks must remain held through commit. Keep the existing persisted ACTIVE/SHIPPER checks and Account locking protocol used by governance; no global Role row lock after Account. Any future role-revocation writer must participate in that Account protocol. Test both race schedules, including multi-role actors.

Source guards:
- REPORT_DELIVERY_FAILURE: Order SHIPPING; Shipment SHIPPING; attempts 1 or 2; maxAttempts exactly 2; pickup timestamp present, no delivered timestamp; current assigned ACTIVE SHIPPER. No failure evidence already exists for this Shipment/attempt.
- RETRY_DELIVERY: Order SHIPPING; Shipment DELIVERY_FAILED; attempt 1, maxAttempts 2; pickup present, no delivery; valid persisted failure evidence for attempt 1 and current assignment. No cooldown. Attempt 2 cannot retry.

Target guards:
- Failure: DELIVERY_FAILED, unchanged attempt 1/2, Shipment version original + 1; append-only evidence linked to actual Shipment/current attempt/current actor, sourceShipmentVersion=original, server timestamp, validated reason/reference.
- Retry: SHIPPING, attempt 2, Shipment version original + 1; retained immutable attempt-1 failure proves prior failure, no attempt-2 failure yet. Attempt-1 evidence source version must match the preceding failure, not a browser token invented as evidence.

The expected Shipment token makes retries and duplicate mutation requests CONFLICT. Never accept stale completion by returning success for a new request, or increment version twice. Repeated reads/verify themselves must not mutate.

## Evidence persistence — QD migration design

Proposed append-only shipment_delivery_failures table, not a mutable last_failure field:
- identity PK; FK shipment_id; attempt_number constrained to 1/2; UNIQUE(shipment_id, attempt_number).
- source_shipment_version >= 0; shipper_id FK Users; server recorded_at TIMESTAMPTZ.
- failure_reason and evidence_reference required/nonblank; proposed limits 1000 and 255 using current project conventions, subject to TD/HP agreement.
- Resolve Order through locked Shipment; if a redundant order_id is included, enforce same-Order association through a composite FK rather than independent FKs.
- Prevent UPDATE and DELETE of successful evidence using a named trigger/approved DB policy; no public edit/delete endpoint. Do not attach ON DELETE CASCADE that silently destroys attestation history. Retention/purge needs a separate approved procedure.
- Evidence is an attestation, not independent carrier verification; never fetch arbitrary request URLs. It must bind Shipment/attempt/shipper/server time. Keep full reason/reference out of general Order events, which are not emitted here.

Failure evidence insert, Shipment mutation and audit append occur in the same transaction. Duplicate unique evidence causes CONFLICT and rolls back all effects. Initial Order state/version/history, Payment, inventory, vouchers, ReturnRequest and refund remain unchanged. New migration only, version after latest develop at merge time; no edits to applied migrations. Actual schema constraints and immutable trigger tests will accompany the implementation, not just this design.

## HTTP adapters — proposal for route confirmation

JSON and URL-encoded POSTs:
- /shipper/orders/{orderId}/delivery-failed: expectedOrderVersion, expectedShipmentVersion, failureReason, evidenceReference.
- /shipper/orders/{orderId}/retry: expectedOrderVersion, expectedShipmentVersion only.

Method + persisted SHIPPER/current-assignment authorization, CSRF for both representations, strict JSON and form/query allowlists, no duplicate fields. No actorId/shipperId/attempt/status/timestamp/amount/receiptFlag accepted. Render failure/reference with escaped text. Retry UI shown only for eligible attempt-1 failure; server guard is authoritative. Do not overload initial /shipping route or reuse FulfillmentResult's incremented Order-version assumption.

Expected response facts: orderId, Order SHIPPING with unchanged version, actual Shipment status/version/attempt. Foreign/missing assignment must preserve current non-disclosure behavior; stale/duplicate operations CONFLICT. Validation bounds and error mapping need the same agreement before parallel implementation.

## Verification and handoff

QD will implement evidence migration/constraints, Shipping hooks/mutations, coordinator, JSON/form/UI and F01-F17 PostgreSQL regressions after interface confirmation; TD implements prepare/verify only. Required real integration includes second-attempt COD delivery and wrong-amount rollback using the merged attempt-2 guards, plus duplicate/stale/evidence rollback, both actor-revocation schedules and multi-role deadlock tests. Use persisted DB snapshots to prove no Order/Payment/inventory/voucher/Return effects. No mock authority in final acceptance or fake Order event.

Concrete confirmations needed from TD/HP: accept the names/signatures and Shipping DTO ownership above; fresh-transaction wrapper with outer-transaction rejection; exact evidence linkage/limits; proposed separate routes and error mapping. QD agrees business points 1-6 in TD's message. This document supplies the previously missing technical interface for TD to implement against; source mutation remains blocked until that interface is accepted and available for real integration.

Validation: read-only comparison with existing OrderLifecycleService, ShipmentFulfillmentService, architecture module rules and approved HP contract; git diff --check. Documentation only; no new production/migration/test PASS claim, no external teammate message or merge performed.
