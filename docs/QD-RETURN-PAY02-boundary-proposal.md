# RETURN-01 Phase 2 / PAY-02 — QD boundary proposal

Date: 2026-10-17. Audited develop: fdbf6a2 (#37, #36 and RETURN-01 Phase 1 #49 merged). Status: QD proposal for HP/TD approval, NOT an approved policy or implemented receipt/refund feature. This document answers TD's five open questions without changing production behavior.

## 1. Ownership and sequence

Existing order-contracts.md assigns RECEIVE_RETURN (RETURN_REQUESTED -> RETURNED) to the Vendor through the Order lifecycle. TD should implement the trusted buyer-return receipt authority, ReturnRequest changes and lifecycle operation in RETURN-01 Phase 2. QD reviews migration/evidence/locking and supplies a Shipping adapter only if an approved logistics-evidence boundary is needed. QD must not write OrderStatus or ReturnRequest directly.

QD SHIP-04 remains failed-delivery return-to-sender after exhausted attempts. That flow keeps Order SHIPPING until trusted receipt plus Ops confirmation, and uses a dedicated cancellation authority. It is distinct from buyer return of a previously DELIVERED Order. Neither flow may use pre-pickup cancelForOps as a receipt shortcut. HP/TD should confirm this division before implementation.

Sequence: agree receipt/refund policy -> TD RETURN-01 Phase 2 authority and migration -> QD DB/concurrency review -> merged trusted receipt -> PAY-02 completion. PAY-02A may prepare schema/domain validation earlier but must not enable refunds, create fake receipt evidence or accept Vendor approval as receipt.

## 2. Who confirms receipt and what constitutes evidence

Proposed buyer-return receipt actor: persisted ACTIVE VENDOR owning the approved Shop, matching the current lifecycle contract. ADMIN/MANAGER exception receipt is not granted implicitly; it requires a separate approved scope/authority. Buyer/SHIPPER assertions and browser received=true are insufficient. Actor comes from CurrentAccountIdProvider; Order, Shop and ReturnRequest linkage comes from locked DB records.

Proposed minimum persisted receipt: original Order and APPROVED ReturnRequest IDs, receiving actor, server timestamp, a bounded nonblank receipt/proof reference, and explicit inspected restockability with approved inspection policy. The reference is an audit attestation, not automatic proof that goods physically arrived. HP/TD must approve acceptable evidence (for example a receiving note or controlled document identifier), access/retention, and whether it must be verified independently. Do not fetch arbitrary client URLs or treat arbitrary strings as independently verified evidence.

Only RETURN_REQUESTED with matching APPROVED evidence and fresh expected Order/request versions may receive. Receipt time cannot precede approval and must not come from the browser. Approval alone never populates receipt, restores stock, marks RETURNED or starts a refund. Never infer restockable=true from receipt. Damaged/partial/missing-item and disputed receipt handling remain unsupported until specified; no automatic stock restoration. Any restocking must use HP InventoryService and a TD-owned once-only guard in the same agreed transaction.

## 3. Initial refund scope

QD proposes COD, persisted PAID payment, whole-Order return and at most one successful full refund per Order. No partial/item-level refunds, online-payment fallback, cancellation refunds or multiple refund attempts are implied. HP/TD must approve before TD makes these production restrictions.

A refund requires Order RETURNED, trusted receipt for that same Order/ReturnRequest, valid Payment evidence and approved authorized amount. Exactly one canonical linked Refund must be protected by DB uniqueness and transaction/version checks. A new refund cannot silently replace rejected/failed evidence; attempt/retry semantics must be explicitly designed if required. Payment, receipt and Refund associations must match the same Order through trusted reads and appropriate DB constraints, not independent client IDs.

## 4. Completion method

Proposal: manual completion recorded by persisted ACTIVE ADMIN, matching current COMPLETE_REFUND contract, with nonblank bounded proof_reference, processing actor and server completed_at. MANAGER refund permission requires HP approval; Ops cancellation permission does not grant refund permission. Gateway integration is outside this proposed phase.

Manual database evidence does not execute a bank transfer. Operators verify the actual external payment before recording success. Do not retry an external transfer automatically after a DB conflict/rollback, and do not hold DB row locks across network calls. A duplicate completion must not record another refund or publish another terminal event. External-transfer idempotency/reconciliation and proof-verification responsibility must be agreed; never claim atomic rollback of money already sent outside PostgreSQL.

## 5. Amount, shipping, discounts and voucher policy

Two possible full-return entitlements need explicit HP/TD choice:
- Goods-only: original subtotal - original discountTotal; original shippingFee excluded.
- Full paid total: original grandTotal, including original shippingFee.

QD recommends goods-only for the first manual full-return scope, subject to HP/TD/product approval; this is NOT the current agreed business rule. Do not code either formula until selected. Compute from immutable authorized Order/payment snapshots in TD's authority, never current Catalog/pricing/commission or browser amount. The browser may display entitlement but cannot authorize it. Compare completion evidence to the approved exact amount and ensure it does not exceed the valid collected amount. Currency/rounding remains consistent with existing VND contract.

Discounts are already reflected in the original snapshot; never refund pre-discount subtotal plus a discount or recalculate today's promotion. Proposal: retain voucher usage/quota and original commission/Order snapshots; no automatic voucher restoration, replacement coupon or commission settlement adjustment. Any exception requires TD business approval and its own boundary. Receipt and refund completion must not alter checkout totals.

## Locking and migration review requirements

Current buyer-return writes use Order -> ReturnRequest -> Account -> Shop (Vendor). Do not silently append Payment/Refund/Product locks to that path: TD must publish one acquisition order for receipt, restocking and refund and audit all overlapping Order/Shipping/Payment/governance paths. Existing Order must be locked before Account; no global Role acquisition after Account. Recheck eligibility and evidence under locks, reject accidental outer transaction according to the authority contract, and retain persisted eligibility until commit.

If receipt does not touch Shipment, do not introduce a Shipment lock solely for symmetry. If logistics evidence is used, explicitly place that lock relative to ReturnRequest/Account and prove no reverse edge. Canonical lock sequence is a contract decision pending TD/HP audit, not established by this proposal.

Phase 1 migration V20261013090000 is merged and must not be edited. It enforces received_at/restockable NULL and decision timestamps; receipt needs a NEW migration that updates the named checks deliberately while preserving existing rows. New migration version must exceed latest develop. Plan paired presence/time/actor/proof constraints, legal states, same-Order references, uniqueness and version bounds. QD will review the actual proposed schema; this document does not preapprove a missing migration.

## PostgreSQL / security acceptance checklist

These are future test specifications, not executed tests:
1. Vendor approval leaves receipt/refund absent and Order RETURN_REQUESTED.
2. Valid own-Shop receipt transitions once to RETURNED with authoritative actor/time/evidence; no refund completion side effect.
3. Pending/rejected/missing/foreign return, missing evidence, stale versions or unauthorized actor fail with zero writes.
4. Inactive/role-revoked Vendor and changed/restricted Shop follow the approved persisted eligibility policy; test both concurrency orders.
5. Duplicate/concurrent receipt produces one committed receipt/history/event and no double restock; audit/inventory failure rolls back every DB effect.
6. ADMIN completion succeeds only for linked RETURNED + valid receipt + eligible PAID COD + exact authorized amount/proof.
7. Wrong payment/return linkage, absent receipt, wrong amount, online/unpaid payment and stale/duplicate completion reject without DB effects.
8. Refund/audit/history failure rolls back Refund and Order together; after-commit event appears only on success. External transfer remains outside DB rollback guarantees.
9. Receipt/refund races and Account/Shop restriction races have bounded deterministic PostgreSQL tests, no 40P01/lock timeout, and no stale authorization acceptance.
10. JSON/form require CSRF, strict DTO fields, server actor and owner-scoped reads; deny unsupported roles. Proof/reference must not leak payment credentials or PII in notification history.
11. Flyway upgrade from deployed Phase 1 preserves rows, validates constraints, rejects inconsistent links/evidence and is a no-op on repeat.
12. Amount tests use original discounted Order snapshots with nonzero shipping to distinguish the chosen entitlement formula; current prices/voucher changes cannot change entitlement.

## Requested decisions / next QD work

HP/TD confirm ownership, receipt evidence/restock policy, initial COD/full/single scope, ADMIN/manual completion, entitlement formula and lock protocol. TD can then publish RETURN-01 Phase 2 / PAY-02A diff. QD will review migration/DB/locking and implement approved Shipping adapters as needed. Actual SHIP-03 failure/retry still awaits its own guard/evidence/endpoint contract; attempt-2 compatibility alone does not complete it.

Validation for this change: documentation only, checked against merged code/contracts and git diff --check. No migration, test or production code changed; no new full-test PASS claim.
