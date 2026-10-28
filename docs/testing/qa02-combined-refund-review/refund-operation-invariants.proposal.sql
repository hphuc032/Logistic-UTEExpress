SELECT count(*) FROM uteexpress.refunds r
 JOIN uteexpress.payments p ON p.id=r.payment_id JOIN uteexpress.orders o ON o.id=r.order_id
 LEFT JOIN uteexpress.return_requests t ON t.id=r.return_request_id
 WHERE p.order_id<>o.id OR p.status<>'PAID' OR p.expired_at IS NOT NULL
 OR r.status<>'SUCCEEDED' OR r.amount<>p.amount OR r.amount<>o.grand_total OR r.amount<=0
 OR r.amount<>trunc(r.amount) OR btrim(r.proof_reference)='' OR r.payment_paid_at<>p.paid_at
 OR NOT COALESCE(((r.operation='PAY02_MANUAL_COD_REFUND_V1' AND o.status='REFUNDED' AND p.method='COD'
   AND t.order_id=o.id AND t.status='APPROVED' AND t.received_at=r.return_received_at
   AND r.order_cancelled_at IS NULL AND r.result_order_version=r.expected_order_version+1)
 OR (r.operation='PAY02_CANCELLED_PAID_REFUND_V1' AND o.status='CANCELLED'
   AND o.delivered_at IS NULL AND o.inventory_released_at=o.cancelled_at
   AND r.order_cancelled_at=o.cancelled_at AND p.paid_at BETWEEN p.created_at AND o.cancelled_at
   AND r.return_request_id IS NULL AND r.expected_return_request_version IS NULL
   AND r.return_received_at IS NULL AND r.result_order_version=r.expected_order_version
   AND NOT EXISTS(SELECT 1 FROM uteexpress.return_requests x WHERE x.order_id=o.id)
   AND NOT EXISTS(SELECT 1 FROM uteexpress.order_status_history h WHERE h.order_id=o.id AND h.to_status='DELIVERED'))),false)
 OR (p.method='ONLINE' AND NOT COALESCE(p.provider_reference ~ '[^[:space:]]',false));
