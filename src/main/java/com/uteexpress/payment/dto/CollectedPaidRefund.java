package com.uteexpress.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Locked, validated persisted payment; not evidence of a gateway invocation by this feature. */
public record CollectedPaidRefund(Long paymentId, Long orderId, BigDecimal amount, Instant paidAt) { }
