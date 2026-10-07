# SHIP-02 progress

This branch starts from SHIP-01 and exposes read-only `GET /shipper/shipments` and
`GET /shipper/shipments/{shipmentId}`. Both require SHIPPER and derive the account
from the authenticated principal. The queries constrain `assigned_shipper_id` in
SQL, so a prior assignee loses access immediately after reassignment; an unknown or
foreign shipment returns the same 404.

This increment does not change Order or Shipment status. Pickup, shipping,
delivery, and COD collection still need the Order lifecycle authority owned by
the Order module. That boundary must serialize cancellation with physical pickup,
and the SHIP-02 write transaction must lock Order, then Shipment, then Payment as
specified by `docs/order-contracts.md`. Do not infer shipping selection from fees
or add a direct `orders.status` update here.
