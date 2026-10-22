# PAY-02 — HP-approved interfaces and QD implementation handoff

2026-10-22, baseline develop 425dd6a. Source: final HP contract supplied by the user. This supersedes unresolved business/interface choices in the earlier QD design reviews; historical reviews remain as records, not competing current policy. TD is authorized by HP to implement on its own branch. No production/migration code is added by this document.

## Final approved contract

POST /admin/orders/{orderId}/refunds; ManualRefundRequest(expectedOrderVersion, expectedReturnRequestVersion, proofReference). Idempotency-Key required, nonblank, max 255; proofReference required, nonblank, max 255. No amount, status, actor or paymentId from client. Optional read-only ADMIN endpoint only returns needed versions/IDs and follows the same authorization.

RefundResult: refund ID, Order ID, server-calculated amount, SUCCEEDED, proof reference, server timestamp and replay flag. Persist immutable committed business result; replay changes only replay flag, not amount/proof/time/IDs. HP's list does not require exposing any extra internal committed-result fields from earlier QD proposals.

COD successful collection only; Order RETURNED, ReturnRequest APPROVED with valid persisted receipt. Amount is full positive persisted grand_total INCLUDING shipping, no extra voucher refund. Zero-value and missing/legacy proof fail closed. This explicitly supersedes the earlier goods-only proposal. No ONLINE, partial/cancellation refund, restock/Inventory/Shipping effects. Payment stays PAID; ReturnRequest and receipt unchanged.

Persisted ACTIVE ADMIN is the COMPLETE current scope, with no Shop scope table. ADMIN attestation documents an already-completed manual COD refund with traceable proof. No payment gateway and no implication that DB rollback can undo a real external transfer. Recheck DB eligibility and hold Account lock through commit; MANAGER and other roles denied.

Namespace PAY02_MANUAL_COD_REFUND_V1; globally unique (operation,idempotency_key), not Admin-scoped. Approved canonical SHA-256 payload contains schema version, operation, Order ID, ORIGINAL Order/Return versions and normalized proof, with deterministic encoding/field order. Store server ADMIN separately and compare it on replay; do NOT add/remove public DTO fields or silently change the approved payload to accept paymentId. Same key with other payload/Order/Admin conflicts. Same-key exact committed replay only for same still-authorized Admin, checked before first-execution stale lifecycle guards. No extra ledger/history/event.

Transaction advisory key -> Order -> ReturnRequest -> Payment -> existing Refund -> ADMIN Account -> writes. Public coordinator rejects outer transaction and opens fresh READ_COMMITTED (no REQUIRES_NEW hiding preheld locks). Hooks MANDATORY. Use pg_advisory_xact_lock over operation + canonical key, excluding actor/Order/payload hash. No session lock or lock upgrade after premature FK insert. Stable hash mapping/encoding must be specified; hash collisions may serialize unrelated keys, never identify them as the same request. Refresh persisted evidence after locks; no cached pre-lock replay result. Current governance uses Role -> Account before membership writes; refund must never acquire a Role lock after Account.

Payment owns immutable refund ledger and collection proof. Dedicated OrderLifecycleService.completeRefund exclusively writes RETURNED -> REFUNDED/history; ledger, idempotency result, audit as required and lifecycle commit atomically, event after commit only. No generic transition bypass. All FK/unique/immutability protections remain mandatory alongside advisory serialization.

## QD response to TD's migration questions

1. Exact PAY-02 filename: **V20261022090000__td_pay02_refund_ledger.sql** (reserved in MIGRATION_RESERVATIONS.md).
2. SHIP-04: **no reserved version and no accessible migration**. Do not treat a claim that it is in progress as evidence of an existing schema dependency. Any later QD schema version must be distinct and after PAY-02/latest maximum.
3. Apply Receipt -> Shipping evidence -> PAY-02. No need to wait for SHIP-04 migration.
4. Required ledger schema remains the approved composite FK/unique/append-only contract; no extra speculative fields or workflows requested by QD. Positive amount CHECK (>0) plus NOT NULL. Linked IDs/server actor/time/proof required; no cascade financial deletion.

## Verification boundary and next review

TD reports 597 unit and 307 existing PostgreSQL regression tests for an independent implementation slice, but no PAY-02 branch/SHA or migration/API/lifecycle implementation is available on fetched origin at this review. These are AUTHOR-REPORTED results, not QD independently verified production acceptance. TD should push the slice with commit SHA and later ledger implementation for review; QD must inspect actual migration/composite FK/advisory/replay/rollback code and PostgreSQL evidence before issuing implementation approval.

Required tests: exact replay after commit, key/payload/actor/Order mismatches, same/different keys/actors concurrent same Order, global same-key cross-Order serialization, first-execution stale original versions, eligibility restriction/revocation both schedules and multi-role actors, zero/invalid/missing evidence, direct FK/unique/immutability rejection, rollback at ledger/audit/history/lifecycle with no early event or poisoned key, deployed upgrade preserving Receipt and Shipping history, repeat migrate no-op. No unrelated Payment/Return/Inventory/Voucher/Shipment changes. Full clean PostgreSQL verify and exact-head CI required before HP final acceptance; no self-merge.

QD current next Shipping scope is still awaiting explicit clarification of Master Plan dashboard versus prior return-to-sender proposal; reservation of PAY-02 is independent and does not block TD.
