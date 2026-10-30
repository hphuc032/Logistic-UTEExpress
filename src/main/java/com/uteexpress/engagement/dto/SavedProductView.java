package com.uteexpress.engagement.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record SavedProductView(Long productId, String name, BigDecimal price,
        boolean available, Instant savedAt) { }
