# SHIP-02 delivery integration

Read-only JSON routes /shipper/shipments and /shipper/shipments/{shipmentId} remain available. HTML list/detail are /shipper/shipments/view and /shipper/shipments/{shipmentId}/view. Both derive ownership from the authenticated account and constrain the assigned shipper in persisted queries. Foreign/missing/reassigned shipments are not exposed.

## Fulfillment writes

POST /shipper/orders/{orderId}/pickup, /shipping and /delivered accept either validated JSON or HTML form data. Request fields are expectedOrderVersion, expectedShipmentVersion and (delivery only) collectedAmount. No actor, owner, role, target state, provider or fee supplied by the browser authorizes a transition. Forms include CSRF and the original persisted versions. Only SHIPPER can access these routes; the trusted service also verifies active persisted account/role and current assignment.

ShipperFulfillmentService in the separate fulfillment module owns one REQUIRED transaction. Order prepare locks Order -> Shipment -> assigned Account; Payment is locked afterward. COD is collected while Order is SHIPPING, before Shipment delivery and Order completion. Order/Shipment updates, Payment, audit and history commit together or roll back. Each prepare/complete call uses the original versions; duplicates/stale requests conflict. Shipping never writes orders.status.

The delivered command currently uses the COD-only lifecycle contract supplied by ORD-05. This task does not add online payment delivery support, failed-delivery/return flows, promotion business logic or Ops cancellation.

## Dependency and review

This branch incorporates ORD-05 PR #46 for integration testing; #46 remains independently owned/reviewed by TD. Do not merge #42 before #46 is merged into develop and the final synchronized SHIP-02 head passes full PostgreSQL verification and CI. QD has not merged either PR. Existing Order lifecycle regressions cover account eligibility restriction before/after lock, reassignment, cancellation/stock/voucher rollback, history persistence failure and concurrent delivery. The new coordinator integration tests exercise HTTP/form authorization/CSRF, real pickup/shipping/delivery, wrong COD rollback and concurrent duplicate delivery.
