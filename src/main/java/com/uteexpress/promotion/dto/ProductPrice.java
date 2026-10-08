package com.uteexpress.promotion.dto;

import java.math.BigDecimal;

/** Per-unit server facts; base price remains positive even for a 100% promotion. */
public record ProductPrice(BigDecimal basePrice, BigDecimal discount, BigDecimal effectivePrice) { }
