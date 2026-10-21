# PAY-02 final draft — QD verdict, 2026-10-21

Reviewed develop 425dd6aecf83c2f0b3e3147e1eda68e0b53368b0 and TD's latest supplied message. No PAY-02 draft/implementation branch was available on origin at review time. This verdict covers that message and baseline code, not an unseen complete contract. No production change or test execution is claimed.

Overall: BLOCKED for production implementation until the concrete items below are incorporated and HP approves final interfaces/scope. Architecture direction PASS in principle: COD-only proposal, manual attestation, immutable Payment-owned SUCCEEDED ledger, Order-owned atomic transition, historical Payment PAID and no changes to Return receipt/Inventory/Shipment/Voucher. The message reports HP approval of the business direction; the earlier goods-only versus grand_total discrepancy should be explicitly superseded in the final HP-approved contract. QD does not choose the financial policy.

## 1. Lock order — conditional PASS, protocol must be explicit

Order -> ReturnRequest -> Payment -> existing Refund -> ADMIN Account is compatible in direction with reviewed current callers if the public lifecycle operation rejects outer transactions and opens a fresh READ_COMMITTED transaction. Lock/refresh Order first; lock/refresh the original ReturnRequest and all relevant Payment rows in stable ID order. Reentrant reads of the SAME already-held Order are fine; never lock a second Order after taking Account/Refund locks. Missing Refund rows cannot be row-locked; Order lock plus ledger uniqueness protects same-Order creation.

Account eligibility: take FOR SHARE (or stronger approved lock), then reread persisted ACTIVE + ADMIN under READ_COMMITTED; retain through commit. Existing role changes take Role -> Account FOR UPDATE before modifying membership, so Account locking can serialize freshness without acquiring a Role lock in refund. Never call RoleGovernanceService.change, guardLastAdminBeforeLock or any Role-locking helper after holding Account. No production caller may enter already holding Account/scope locks. Multi-role actors do not bypass any of these requirements.

Separate lock/read phase from write phase. In particular an INSERT into ledger with processed_by FK can implicitly acquire an Account KEY SHARE lock; do not insert the ledger before the explicit eligibility-lock step and then upgrade locks in an inconsistent order. The same applies to FK checks on Payment/Return/Order and any idempotency table actor FK. Take all declared locks and validate before writes; retain them to commit. List explicit AND implicit locks in the final transaction pseudocode.

BLOCKER: ADMIN scope is not implemented or defined on develop. HP must explicitly approve system-wide persisted ACTIVE+ADMIN, or specify the scope rows and all grant/revoke writers. A separate mutable scope cannot be protected by an absent row lock; define a common parent-lock protocol with scope writers. QD cannot claim the proposed ordering is globally deadlock-free without the actual scope implementation and PostgreSQL tests.

## 2. Schema — conditional PASS

Require UNIQUE(order_id), UNIQUE(payment_id), UNIQUE(return_request_id), nonnull linked IDs, exact decimal amount, nonblank bounded proof, server processed_by/completed_at, canonical hash and immutable result. Composite FKs (payment_id,order_id) and (return_request_id,order_id) must target matching composite UNIQUE keys added in a NEW migration if absent. Separate single-column FKs alone are insufficient. Required links must be NOT NULL so uniqueness/FKs cannot be bypassed with NULL.

Ledger is one-shot SUCCEEDED, not a pending external-transfer workflow. CHECK local status, amount according to approved zero policy and nonblank proof; validate cross-table receipt/paid amounts under locks in Payment authority. UPDATE and DELETE trigger protection must preserve successful proof/hash/result/links, including direct SQL. No cascade deletion of financial history. Rollback of uncommitted evidence must remain possible. Proof reference is evidence, not necessarily globally unique across banks/providers; HP must define its namespace before adding a proof UNIQUE.

## 3. Idempotency — BLOCKED until namespace and collision protocol are written

TD lists UNIQUE(idempotency_key), which means a GLOBAL namespace, unlike the earlier QD actor/key proposal. Either can work, but the contract must choose exactly one. If global is chosen: use an operation-specific refund namespace plus bounded high-entropy key; store authenticated actor and Order in the request hash. A collision from a different actor must not disclose another actor's committed response. Return a generic conflict after authorization; do not silently scope the SQL lookup by actor while schema enforces global uniqueness.

Canonical hash must include operation/schema version, server actor, Order ID, BOTH original expected versions and canonical proof reference. Define key/proof normalization and length; do not hash browser amount/status/owner as authority. Store the immutable committed response including resulting Order version; do not rebuild replay from mutable current entities.

Authorized same-key/same-hash replay must be checked before first-execution stale/state guards: Order is already REFUNDED and version incremented after success. Recheck CURRENT persisted ADMIN scope/eligibility even on replay. Same key/different payload and different-key duplicate per Order conflict. Do not substitute refreshed versions into the original command. On initial execution original Order/Return versions must match; on exact committed replay they remain part of the recorded hash instead of being revalidated against changed state.

Parent Order locks do not serialize a global key across different Orders. Specify unique-insert conflict handling: no querying in an aborted PostgreSQL transaction after a caught uniqueness error. Use an explicit savepoint protocol or a bounded whole-operation retry with fresh transaction and original versions. Never acquire the winner's different Order lock while holding the loser's Order/Account locks. Failed DB work must not leave a poisoned key or orphan ledger/history/result. This must be covered by cross-Order key-collision tests, not just same-Order races.

## 4. Original versions / evidence / atomicity — conditional PASS

Validate ORIGINAL expectedOrderVersion and expectedReturnRequestVersion, refresh after locks, require RETURNED + APPROVED same-Order immutable received_by/received_at/reference and successful unexpired COD collection amount matching authoritative Order grand_total. Payment authority validates evidence; Order lifecycle alone writes REFUNDED/history and publishes after commit. Payment remains PAID; ReturnRequest/receipt/version, Shipment, Inventory and Voucher remain unchanged. Server computes refund amount after HP confirms formula. Legacy/missing/ambiguous evidence fails closed; zero-value eligibility must be explicit. No generic ADMIN/MANAGER guard may grant MANAGER refund access. HTTP and service role checks, CSRF and DTO allowlists remain required.

## 5. Migration coordination — PASS on append-only strategy, version not reserved

Current maximum applied version is 20261018110000; Receipt is 20261018090000. Use a distinct new timestamp greater than the deployed maximum and recheck remote immediately before creating it. QD has no SHIP-04 production migration or reserved version. SHIP-04 scope is still being reconciled: Master Task Board says Shipper dashboard; earlier team proposal says return-to-sender. A read-only dashboard would need no schema migration. Do not modify existing Receipt/Shipping files. Extend current exact-pending-version upgrade assertions when adding refund migration; retain historical rows/checksums/receipt immutability validation.

## 6. Required PostgreSQL verification — acceptance, not results

- Fresh and deployed Phase-1/Receipt/Shipping baselines -> latest, both existing receipts and failure evidence preserved, checksum validation and repeat no-op.
- Cross-Order Payment/Return FK rejection; duplicate Order/payment/return/key; required null/blank/amount constraints; successful UPDATE/DELETE blocked.
- Same key/payload simultaneous and replay after success, same key/different payload, different keys/actors same Order; global key across different Orders in both schedules.
- Stale original versions, preloaded stale persistence context, invalid/unpaid/expired/ambiguous/legacy evidence; no hidden replacement tokens.
- ADMIN lock/revocation/scope changes versus refund both execution orders; multi-role ADMIN+VENDOR+SHIPPER versus existing cancellation/delivery/receipt paths. Observe backend blocking with bounded timeouts; fail on 40P01 or lock timeout.
- Failure at ledger/idempotency/audit/history/lifecycle boundary -> complete rollback, no event before commit, retry can succeed afterward. No Payment/Return/Shipment/stock/voucher side effects.
- HTTP/service ADMIN-only, deny MANAGER and all other roles, forged actor/amount/status/link fields, missing CSRF, invalid proof/key and outer transaction rejected.

When TD supplies the actual final draft with scope, namespace/replay ordering, FK-aware lock pseudocode and collision protocol, QD can issue a revised contract verdict. Production implementation and full verification still belong to TD after HP final interface approval. No tests from previous SHIP-03 runs are reused as PAY-02 evidence.
