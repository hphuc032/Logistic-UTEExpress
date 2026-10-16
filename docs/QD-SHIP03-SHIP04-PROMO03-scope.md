# QD / TD follow-up scope proposal — 2026-10-15

Status: proposed contract for HP/TD review, not an approved implementation API. The wave table assigns SHIP-03/04 and PROMO-03 to QD but does not provide their full acceptance specification. The constraints below follow current order-contracts.md and TD's supplied boundary; open decisions are explicit. No Order/Payment/Return business mutation is implemented by this document.

## SHIP-03 — failed delivery and retry

QD scope: assigned-shipper HTTP/UI, CSRF/role/ownership validation, Shipment failure/retry evidence and audit, and coordination with trusted Order/Payment boundaries. A failure changes Shipment SHIPPING to DELIVERY_FAILED while Order remains SHIPPING. A retry changes Shipment DELIVERY_FAILED to SHIPPING and increments the attempt counter; first attempt starts at PICKED_UP to SHIPPING, maximum two attempts. Failure requires a trimmed nonblank reason; exact reason enum/length and failure evidence require approval. No failure/retry action collects COD, cancels Order, restores stock/vouchers or creates a ReturnRequest.

Proposed transaction contract: reject stale Order/Shipment versions and reassigned/foreign/inactive shippers; lock Order then Shipment then persisted actor eligibility, preserving current global ordering. TD must provide/approve an Order-owned guard for a Shipping-only mutation while Order stays SHIPPING. Do not force failure/retry through the existing three-action prepare/complete API without extending its contract. No notification OrderStatusChangedEvent should be fabricated when Order state does not change.

Acceptance to agree: first failure and one retry succeed; retry after two attempts conflicts; duplicate/stale requests conflict; pickup/delivered/cancelled states reject failure; wrong assignee cannot act; audit/eligibility/evidence failures roll back Shipment changes. PostgreSQL races must cover retry versus delivery/reassignment/cancellation where permitted, and account revocation versus execution. QD supplies Shipping tests; TD supplies trusted Order guard tests.

## SHIP-04 — return to sender after exhausted delivery

Proposed scope: failed-delivery return logistics and trusted Shop receipt evidence, followed by Ops confirmation. This is distinct from the buyer-requested RETURN-01/ORD-04 workflow; the wave table alone does not establish that SHIP-04 implements buyer RECEIVE_RETURN. HP/TD must confirm this interpretation before implementation.

Keep Order SHIPPING while delivery is exhausted and goods are in transit back. A shipper's failure or request boolean is not receipt proof. Persist server-validated receipt evidence bound to the original Order/Shipment and authorized Shop receiver; evidence fields, receiving actor, partial/missing/damaged goods handling and whether early return is ever allowed remain open decisions. ADMIN/MANAGER confirmation must consume validated persisted evidence, not accept received=true from the browser.

Proposed completion: trusted Order lifecycle owns transition to CANCELLED and once-only stock/voucher effects; Shipping owns RETURNED_TO_SENDER and its audit/evidence. Both commit atomically in the agreed lock order. Do not reuse pre-pickup cancelForOps, which only accepts NEW/CONFIRMED before pickup. Do not write Payment or ReturnRequest; exceptional collected-payment handling requires TD's explicit policy.

Acceptance to agree: exhausted attempts plus valid receipt plus Ops confirmation succeed once; absent/foreign/stale evidence rejects; premature confirmation rejects; duplicate confirmations do not restore stock twice; failure in lifecycle, audit, stock or voucher rolls back all changes; concurrent delivery/receipt/confirmation cannot produce inconsistent terminal states. TD must own any new lifecycle operation and define its Payment guard before QD wires it.

## PROMO-03 — proposed Admin promotion operations

QD scope proposal: Admin-facing list/detail and approved create/update/disable operations via TD-owned promotion services; no direct promotion repository writes or replacement pricing logic. First confirm whether Master Plan includes platform vouchers, platform product promotions, or both, and whether MANAGER has management access. Current vendor-scoped APIs do not imply platform authorization.

TD owns eligibility, formula, priority/stacking, effective dates, limits, quota/usage and checkout behavior. Preserve subtotal, discountTotal and grandTotal; existing Order snapshots remain immutable. Disabling a promotion must not rewrite past Orders or release usage outside TD's boundary. QD supplies HTTP validation, trusted actor derivation, CSRF, authorized UI and audit integration only after TD exposes the approved management API.

Acceptance to agree: allowed roles/scope only, cross-shop/platform scope cannot be forged, invalid dates/limits/version rejected by authority, duplicate/stale management conflicts, audit failure has agreed rollback behavior, existing checkout and historical totals unchanged. Any pricing/checkout effect needs TD review and regression tests before merge.

## Sequence and handoff

1. Finish HP review of ADMIN-07 #37 and WS-01 #36; no self-merge.
2. Recheck WS-01 Flyway ordering if RETURN-01 #49 merges first.
3. HP/TD confirm this scope, reason/evidence policy and lock strategy; TD implements missing lifecycle/management authority on independent branches.
4. QD implements Shipping/Admin adapters against merged contracts with real PostgreSQL rollback/concurrency tests; no placeholder authority or direct status writes.
5. WS-02 follows stable WS-01 and SEC-02 transport-security requirements.
