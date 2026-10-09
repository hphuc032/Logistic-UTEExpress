# SHIP-02 progress

This branch starts from SHIP-01 and exposes read-only `GET /shipper/shipments` and
`GET /shipper/shipments/{shipmentId}`. Both require SHIPPER and derive the account
from the authenticated principal. The queries constrain `assigned_shipper_id` in
SQL, so a prior assignee loses access immediately after reassignment; an unknown or
foreign shipment returns the same 404.

Internal Shipment fulfillment/closure contracts are also prepared, but no public
mutation route is active. Pickup, shipping, delivery, and COD orchestration still
need the Order lifecycle authority owned by the Order module. See
[the ORD-05 boundary proposal](SHIP-02-ORD-05-boundary.md) for concrete APIs, module
placement, cancellation and transaction responsibilities. Keep this PR Draft until
the complete Order/Shipment/Payment integration passes PostgreSQL tests.

## Shipper read pages

HTML pages are available at GET /shipper/shipments/view and GET /shipper/shipments/{shipmentId}/view. They reuse the same server-side assignment read boundary as the JSON endpoints, escape displayed values and show an empty assignment state. Only SHIPPER is allowed; there are no mutation controls until ORD-05 is integrated. Existing JSON routes are preserved.
