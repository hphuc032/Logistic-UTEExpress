package com.uteexpress.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Approved response. Successful retries retain all original fields except replay. */
public record RefundResult(Long refundId, Long orderId, BigDecimal refundAmount, RefundStatus status,
        String proofReference, Instant serverTimestamp, boolean replay) { }
