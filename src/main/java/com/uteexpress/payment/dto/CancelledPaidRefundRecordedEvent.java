package com.uteexpress.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Financial completion only. Never an OrderStatus transition or a second cancellation notice. */
public record CancelledPaidRefundRecordedEvent(Long refundId, Long orderId, Long processedBy,
        BigDecimal amount, Instant at) { }
