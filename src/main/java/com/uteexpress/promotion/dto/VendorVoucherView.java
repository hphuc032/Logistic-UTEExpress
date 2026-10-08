package com.uteexpress.promotion.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record VendorVoucherView(Long id, String code, VoucherType type, BigDecimal value,
        BigDecimal maxDiscount, BigDecimal minSubtotal, LocalDateTime startsAt, LocalDateTime endsAt,
        long totalLimit, long perUserLimit, long used, boolean hasUsage, boolean active, Long version) { }
