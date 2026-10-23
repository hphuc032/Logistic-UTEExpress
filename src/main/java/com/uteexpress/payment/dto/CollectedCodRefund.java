package com.uteexpress.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Internal locked Payment evidence, produced by the Payment authority only. */
public record CollectedCodRefund(Long paymentId, Long orderId, BigDecimal amount, Instant paidAt) { }
