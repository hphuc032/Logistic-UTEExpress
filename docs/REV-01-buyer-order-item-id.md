# Buyer Order Detail item IDs for Review integration

`GET /orders/{orderId}` with `Accept: application/json` uses the existing
authenticated Buyer Order Detail endpoint (USER/VENDOR buyer policy). It now adds
`items[].orderItemId`, taken directly from each persisted `OrderItem.id` after the
server checks that the current buyer owns the Order. The item ordering remains
ascending persisted item ID. All existing response fields remain unchanged.

Illustrative item fragment from the detail response:

```json
{
  "id": 120,
  "items": [
    {
      "productId": 902,
      "productNameSnapshot": "Product snapshot",
      "unitPrice": 100000.00,
      "discountSnapshot": 20000.00,
      "finalUnitPrice": 80000.00,
      "quantity": 2,
      "lineTotal": 160000.00,
      "orderItemId": 501
    }
  ]
}
```

QD/frontend should take the selected line's `orderItemId` and pass it as `{id}`
to the Review-owned `GET /order-items/{id}/review` contract. Do not substitute
`productId`, the enclosing Order `id`, or the line's array index. That Review
endpoint and its writer/validation are outside this change; availability depends
on QD's integration. The returned ID is an identifier, not Review eligibility or
authorization for a later write. No eligibility/status field is added here.

Foreign and missing Orders retain the existing `404 RESOURCE_NOT_FOUND` response
without item data. Existing authentication and role rules remain in effect.
The Vendor Order Detail projection retains its existing JSON fields despite
sharing the item DTO. No additional endpoint, query, schema, transaction or lock
change is required to expose the already loaded persisted item ID.
