package com.uteexpress.governance.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record OpsOrderSummary(Long id, String orderCode, Long buyerId, Long shopId,
        String status, BigDecimal grandTotal, Instant createdAt, Long version) { }
