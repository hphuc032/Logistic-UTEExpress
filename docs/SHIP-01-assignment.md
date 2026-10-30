# SHIP-01 assignment

This branch depends on the Order shipping snapshot introduced by ORD-04 (PR #39).
Checkout stores the server-validated `shipping_provider_id`, `shipping_service_code`, and
`shipping_fee` on Order. SHIP-01 copies only those persisted facts into a Shipment. A
legacy Order with no shipping selection fails with `CONFLICT`; fee alone is never used
to infer a provider or service.

`POST /admin/orders/{orderId}/assign` and the matching `/manager` route accept
`shipperId` and `expectedOrderVersion`. Reassignment uses `/reassign` with
`expectedShipmentVersion`. Both operations require the corresponding Ops role and CSRF.
The actor comes from the authenticated account. They lock Order before Shipment,
require `CONFIRMED` plus `ready_at`, then lock the SHIPPER role and target account
in governance's standard order before verifying the active SHIPPER grant.
Assignment is unique per Order. Reassignment is allowed only in `ASSIGNED` status.
Each successful mutation writes assignment history and an audit row in the same transaction. Neither path
changes Order status; pickup and later fulfillment belong to SHIP-02 and the Order
lifecycle authority.

The migration adds the Shipment table, foreign keys, unique Order constraint, status
and amount checks, attempt limit, timestamps, and optimistic version. The assignment
service is deliberately narrow: it does not create pickup, delivery, COD collection,
or return transitions.
