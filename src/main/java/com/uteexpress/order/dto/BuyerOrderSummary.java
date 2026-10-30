package com.uteexpress.order.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Buyer-safe persisted facts. Shop is a reference, not a historical name snapshot. */
public record BuyerOrderSummary(Long id, String orderCode, Long shopId, OrderStatus status,
        BigDecimal grandTotal, Instant createdAt) { }
