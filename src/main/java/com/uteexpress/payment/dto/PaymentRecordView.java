package com.uteexpress.payment.dto;

import com.uteexpress.checkout.dto.CheckoutRequest.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;

/** Persisted attempt facts only; no provider references, keys or inferred order payment state. */
public record PaymentRecordView(PaymentMethod method, PaymentStatus status,
        BigDecimal amount, Instant createdAt) { }
