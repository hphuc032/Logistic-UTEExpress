package com.uteexpress.order.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Minimal placement receipt; no order-management endpoint or entity serialization. */
public record PlaceOrderResult(Long orderId, String orderCode, OrderStatus status,
        BigDecimal grandTotal, Instant createdAt, boolean replayed) { }
