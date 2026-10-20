# PAY-02 — QD technical review, 2026-10-20

Baseline: develop 425dd6aecf83c2f0b3e3147e1eda68e0b53368b0 (#54 merged). Read-only review of current code and TD's supplied proposal; not approval of an unimplemented refund service. No production/migration changes, no new verification run or test-count claim.

## Decisions HP still owns

The latest message approves full refund but explicitly leaves the amount formula for approval. TD proposes grand_total including shipping; the earlier goods-only instruction excluded shipping. Neither formula may be silently treated as final. HP must explicitly supersede or reaffirm that earlier policy. Also unresolved: ADMIN scope definition and revocation writers, proof attestation versus independent verifier, COD-only versus ONLINE, zero-value orders and legacy payment/receipt evidence. Missing evidence must fail closed; do not backfill proof or infer a successful collection from a status alone.

Master Plan lists GET /admin/refunds and POST /admin/refunds/{id}/complete. TD's proposed POST /admin/orders/{orderId}/refunds is a new one-shot route. Confirm the approved route/operation rather than silently replacing the master contract. No amount, actor, scope, status, payment ID or return ID is client authoritative.

## Observed baseline

- PaymentStatus contains UNPAID and PAID only. Payment is TD-owned, COD collection records paid_at and exact authoritative amount. PaymentRepository locks Order facts and then Payment rows ordered by ID. PaymentReadService.requireCollectedCodForDelivery is a delivery-specific MANDATORY guard, not a refund authority.
- ReturnOrderAuthority holds Order -> ReturnRequest -> Account -> Shop and refreshes locked entities. RECEIVE_RETURN writes immutable server receipt; Order becomes RETURNED and ReturnRequest stays APPROVED. No stock/refund side effects.
- RoleGovernanceService.change holds Role -> Account for role mutation. Last-admin account locking also uses the ADMIN Role guard before Account. Shipping holds Order -> Shipment -> Account; eligibility stays locked until commit. Do not call a role-locking helper after holding Account.
- OrderLifecycleService is the sole lifecycle authority. Existing lifecycle coordinators reject an outer transaction. Refund orchestration must preserve that Order-first property, including on replay and service-to-service callers.

## Proposed schema review conditions

Payment owns refund ledger and idempotency storage; QD does not create them on Shipping's branch. One-shot SUCCEEDED is reasonable only if HP approves recording completed manual refunds rather than starting an external operation. Never imply a DB transaction can roll back a bank transfer already performed.

Required ledger facts: stable ID; order_id, payment_id, return_request_id; authorized amount/currency or explicit single-currency invariant; SUCCEEDED status; nonblank bounded proof reference; server processed_by/completed_at; canonical request hash/idempotency link and immutable committed result. Server derives record links and amount. Fixed decimal compatible with Order/Payment; no float arithmetic. Amount must satisfy the approved entitlement and not exceed persisted collected amount. Zero-value policy remains open.

Use UNIQUE(order_id), UNIQUE(payment_id), UNIQUE(return_request_id), NOT NULL on required links, actor FK, and composite FKs (payment_id, order_id) and (return_request_id, order_id) to the same Order. Add matching composite UNIQUE targets in a NEW migration if absent; do not edit old migrations. Simple separate FKs alone allow cross-order evidence. No cascade deletes of financial/receipt history. CHECK validates local status, bounded nonblank proof, nonnegative/positive amount per approved policy and server timestamps; cross-table payment/eligibility totals require the locked authority, not an impossible cross-table CHECK.

Successful ledger, request hash, committed result and proof must resist overwrite/removal. If an immutable trigger is chosen, test both UPDATE and DELETE, normal insert and audit-failure rollback. Do not let an immutability trigger prevent rollback of an uncommitted insertion. Proof uniqueness must match the approved external proof namespace; do not invent global uniqueness for provider references without business confirmation.

## Locking and freshness proposal

TD's Order -> ReturnRequest -> Payment -> existing Refund -> ADMIN Account/scope order is conditionally acceptable, NOT a demonstrated globally deadlock-free implementation. Lock and refresh Order first, then ReturnRequest, then all relevant Payment rows in stable ID order, then existing refund/idempotency rows in one documented order. A missing Refund row is not lockable: the parent Order lock plus database uniqueness serializes competing creation for that Order.

Read CurrentAccountIdProvider for identity without taking an Account lock before Order. Retain persisted ACTIVE + ADMIN and approved scope freshness locks through commit. Do not use an existing ADMIN/MANAGER helper as refund authorization: MANAGER must remain denied. Account lock + reread role can coordinate with current role mutation writers, which take the same Account lock; a naked role read or JWT check cannot. If scope is a separate mutable row, define grant/revoke writers and a common ordering; row absence is not protected by FOR UPDATE. Parent Account locking works only if ALL scope writers also use it. Do not acquire Role after Account, or add scope writers that take scope then Account while refund takes Account then scope.

No refund caller may arrive holding Account/Role/scope/Payment locks ahead of Order. Reject outer transactions at the public orchestrator. Internal authority hooks remain MANDATORY. New calls must not invoke unrelated Shipping/Inventory/Voucher or buyer-return mutations. Read/lock Order without retaining stale managed snapshots; target checks consume persisted successful ledger facts before Order -> REFUNDED. Keep the original expected Order/Return versions: validate them on FIRST execution, not newly reloaded versions substituted into the request. Do not change ReturnRequest version/receipt merely to record refund.

## Idempotency and replay

Proposed scope: UNIQUE(actor_id, idempotency_key) across refund Orders, with order_id inside the canonical hash, so the same Admin/key cannot silently target another Order. This scope is a proposal for TD/HP approval; a global key or order-scoped key has different semantics and must be explicit. Another Admin using the same textual key does not gain access to a previous Admin's committed result. UNIQUE(order_id) independently prevents two successful refunds under different actors/keys.

Canonical payload includes operation, Order ID, both ORIGINAL expected versions, canonical proof reference and an explicit canonicalization version. Actor is server-derived; amount is not client payload. Bound key/proof lengths and define trim/case/Unicode rules before hashing; store no raw sensitive request in logs.

After current persisted authorization, same key/hash replays the immutable committed response even though the Order is now REFUNDED and its version advanced. Therefore first-execution stale/state guards must not run before the authorized replay path. Same key/different hash conflicts; different key on an already-refunded Order conflicts. Recheck current ADMIN scope on replay to avoid exposing results after revocation. Request/in-progress records and ledger/history/result must commit atomically; failed attempts must not leave poisoned keys. Across different Orders sharing one actor/key, parent Order locks do not serialize the key; use the unique key and an explicit safe conflict/retry protocol. Do not catch a PostgreSQL uniqueness violation and keep querying in the same aborted transaction; use a documented savepoint/retry/fresh-transaction strategy and keep global lock order.

## Acceptance required after implementation

| Case | Expected result |
| --- | --- |
| Eligible RETURNED + APPROVED immutable receipt + PAID evidence + approved ADMIN scope | One immutable SUCCEEDED refund, one lifecycle/history change, event after commit only |
| Amount/IDs/actor/status/scope forged, MANAGER/BUYER/VENDOR/SHIPPER, missing CSRF | Denied, no effects |
| Missing/wrong/legacy receipt, uncollected/expired/multiple or mismatched payment evidence | Fail closed under explicit policy |
| Same key/same original payload after success | Same committed result, no extra history/event |
| Same key/different proof, versions or Order | CONFLICT, no extra ledger |
| Different key or actor, same Order; simultaneous same key on different Orders | At most one successful refund per Order; documented key behavior |
| Both original expected versions stale; entity preloaded before lock | CONFLICT on first execution, refresh guards valid |
| Lock/revoke ADMIN/scope commits first vs refund locks first | Correct serialized authorization in both schedules |
| Audit/ledger/lifecycle/history failure | Entire DB transaction rollback; no early event or poisoned key |
| Direct SQL cross-order FK/duplicate/proof modification/removal | DB constraints reject |
| Fresh + deployed pre-Receipt + Receipt + SHIP-03 upgrade, repeat migrate | Historical data/checksums preserved, exact migration order, no-op repeat |
| Successful refund | Payment historical PAID, Return APPROVED/receipt unchanged, Shipment/Inventory/Voucher untouched |

Use bounded PostgreSQL concurrency tests, backend blocking evidence where needed and both execution schedules; assert no 40P01/deadlock/lock timeout. No test in this matrix is claimed executed: production implementation is pending approval.

## Migration coordination

Latest applied migration is V20261018110000 (Shipping evidence); Receipt is V20261018090000. PAY-02 and any eventual SHIP-04 migration must have distinct NEW versions greater than the deployed maximum, agreed just before implementation. No fixed reservation is made in this document. Do not insert a retroactive migration before deployed Receipt/Shipping. Current ReturnReceiptMigrationIT explicitly lists pending migrations for historical baselines; extend its expected list when adding an approved refund migration, rather than weakening the receipt/history assertions.
