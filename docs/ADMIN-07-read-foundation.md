# ADMIN-07 operations order read foundation

ADMIN and MANAGER can search persisted orders by literal order-code text and status at
`GET /admin/orders` and `GET /manager/orders`, with pages of at most 100 results.
The corresponding `/{id}` route shows an Order's persisted address/amount snapshots,
item snapshots, status history and payment attempts. HTML and JSON representations
use the same service authorization. USER/VENDOR/SHIPPER have no Ops access. No query
includes checkout keys, request hashes or payment provider references.

This slice does not change Order, Payment, or SecurityConfig files. Its read model is
local to governance so the existing module dependency direction stays acyclic.
Search/detail do not infer missing history or payment facts.

The Master Plan also asks for an operations exception command. That write path is
not exposed yet: ORD-03's shared lifecycle currently authorizes only vendor-owned
confirm/cancel operations. An Ops command must be added at the Order lifecycle
authority with Order locking, version/state checks and transactional audit/shipment
resolution. A governance repository update of `orders.status` would bypass those
guards, so this PR must remain draft until the Order owner reviews the shared
mutation contract.
