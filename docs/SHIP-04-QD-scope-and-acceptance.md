# QD next Shipping work — scope reconciliation and acceptance

Reviewed 2026-10-20 against develop 425dd6a. SHIP-03 #54 and RECEIVE_RETURN #53 are merged; no open PR was returned by GitHub at review time. This document prepares QD implementation without silently choosing between contradictory SHIP-04 labels. No production mutation or new migration is added.

## Scope discrepancy requiring explicit confirmation

UTEExpress_Master_Project_Plan.docx, extracted Master Task Board row SHIP-04, says: **Shipper dashboard**, assigned/shipping/delivered/failed counts and success-rate query, QD owner, TD reviewer, dependency SHIP-03, branch feature/ship-04-dashboard. Master routes include GET /shipper/dashboard. The same plan separately lists Ops return-to-sender POST /{ops}/shipments/{id}/return-to-sender.

The earlier docs/QD-SHIP03-SHIP04-PROMO03-scope.md was explicitly a proposal and used SHIP-04 for exhausted-delivery return-to-sender. Subsequent team discussion has retained that label but has not finalized physical-receipt actors/evidence or a new Order/Payment boundary. The wave table alone cannot resolve the mismatch. Ask HP whether SHIP-04 retains the Master Plan dashboard definition or is explicitly re-scoped; if both are required, assign distinct tasks and PRs. Do not mark either complete by delivering the other.

## If Master Plan dashboard is confirmed

Implement a QD-only read service/DTO, GET /shipper/dashboard and Thymeleaf view. Identity comes only from CurrentAccountIdProvider; SHIPPER method/HTTP authorization remains mandatory. No actor/shipper query parameter. Query shipments with assigned_shipper_id = server actor in SQL, aggregate in one statement for a consistent snapshot. No Order/Payment writes, lifecycle calls, receipt or migration needed for first implementation. Reuse existing Shipping page conventions; do not change LoginController/SecurityConfig unless needed after checking current route protection.

Proposed metrics to confirm: current ASSIGNED, PICKED_UP, SHIPPING, DELIVERY_FAILED and DELIVERED Shipment counts; count each Shipment once. Failure attempts are a separate evidence metric, not the same as failed Shipments. Return-requested/returned/refunded Orders with a DELIVERED Shipment must not retroactively remove successful delivery. Current-assignment aggregation is not historical shipper productivity after reassignment. Label the basis explicitly and avoid adding unapproved time filters.

Proposed success rate: delivered / (delivered + current DELIVERY_FAILED), with no-data displayed when denominator is zero; this is a current outcome ratio and changes after retry, NOT per-attempt or lifetime success rate. HP must approve the denominator/label. If lifetime, period or per-attempt performance is needed, agree immutable attribution and event/time sources first; don't invent historical assignment from current assigned_shipper_id.

Acceptance: empty dataset/no division by zero; every Shipment status; another shipper excluded; mixed-role actor scoped to own shipments; anonymous/non-SHIPPER blocked; no sensitive buyer/payment fields; retry changes counts correctly; two failures do not double-count one failed Shipment; Order buyer-return/refund does not rewrite delivery facts; one aggregate avoids internally inconsistent counts under concurrent mutation; GET causes no DB mutations. Add a meaningful PostgreSQL fixture for grouped counts/ownership and HTTP role/view coverage once scope is confirmed.

## If return-to-sender scope is confirmed

Keep independent from buyer RECEIVE_RETURN and PAY-02. Current order-contracts.md defines exhausted Shipment DELIVERY_FAILED with Order SHIPPING until physical shop receipt; RETURNED_TO_SENDER corresponds to CANCELLED after Ops verification. Existing cancelForOps only accepts NEW/CONFIRMED before pickup and MUST NOT be reused or broadened for this flow.

Open business decisions: who attests Shop receipt (Vendor owner, Ops or independent verifier); approved evidence reference and immutable identity/time; complete versus partial/missing/damaged goods; restriction/ownership transfer handling; which Ops roles may confirm; treatment of paid/legacy/ONLINE orders; whether inventory/voucher release is allowed and exactly when. Shipper failure alone is not receipt evidence and never authorizes stock release. The buyer receipt-only policy does not automatically apply to exhausted-delivery return logistics.

Proposed QD boundary after approval: MANDATORY Shipping lock/read, append-only return-to-sender receipt evidence and versioned Shipping mutation consumed by TD-owned trusted Order lifecycle operation. Server derives Order/Shipment/Shop/actor links. State guards require exhausted attempts, both immutable failure records and original expected versions; no browser received=true authority. Do not copy the generic lockAssignedForTransition hook if it locks a Shipper Account before required Return/Payment facts; design exact lock order jointly with TD/HP first.

Order owns CANCELLED, inventory/voucher idempotent release, history and after-commit event. Payment owns the uncollected/paid guard; cancellation refund remains separate. All Shipping evidence/status, audit and permitted Order-side effects must share one fresh Order-first transaction; reject outer transactions. Do not directly update orders.status, payments or buyer return_requests. Scope/Account/Shop locks must coordinate with governance without Account-before-Order or Role-after-Account inversions.

| Case | Required outcome after approved implementation |
| --- | --- |
| Exhausted failure + valid immutable Shop receipt + authorized Ops + original versions | Atomic agreed terminal Shipping/Order transition once |
| Attempt 1 failed / retry eligible / SHIPPING / DELIVERED | Reject return completion |
| Missing/foreign evidence, forged receiver/order/received flag, stale tokens | Reject without side effects |
| Shipper/Buyer/Vendor tries Ops confirmation | Deny unless explicitly approved actor in the finalized contract |
| Two Ops confirmations, same/different actor | At most one transition/release/history/event |
| Evidence/audit/lifecycle/inventory/voucher failure | Whole transaction rollback |
| Concurrent role/account/shop changes, receipt and terminal confirmation | Serialized eligibility; no deadlock; no stale authorization |
| Collected/ambiguous Payment | Fail closed until explicit Payment-owned policy; never manual Payment rewrite |
| Buyer ReturnRequest/receipt and refund ledger | Untouched by failed-delivery workflow |
| Fresh/deployed migration upgrades | Preserve Receipt and Shipping failure history/checksums; no old migration edit |

These are proposed acceptance cases, not executed tests. A specific migration version is not reserved while scope is unresolved; coordinate a distinct new version with PAY-02 immediately before implementation.

## Handoff for HP/TD

1. Confirm SHIP-04 task definition and metric/evidence policy.
2. If dashboard: QD implements isolated read-only feature; TD reviews metric semantics, HP authorization.
3. If return-to-sender: TD/HP approve lifecycle/Payment authority and exact lock protocol before QD production hooks; implement separate branches with real persisted evidence.
4. PAY-02 schema/locking/idempotency review is in docs/PAY-02-QD-technical-review.md; amount, scope and proof decisions remain HP-owned.
