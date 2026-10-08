package com.uteexpress.promotion.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record VendorPromotionView(Long id, Long productId, String name, BigDecimal discountPercent,
        LocalDateTime startsAt, LocalDateTime endsAt, boolean active, Long version, String status) { }
