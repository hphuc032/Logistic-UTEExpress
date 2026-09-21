package com.uteexpress.catalog.dto;

import java.math.BigDecimal;

/** HP-owned safe server projection, with positive whole-VND unitPrice and no mutable entity. */
public record ProductSnapshot(Long productId, Long shopId, String productName, BigDecimal unitPrice,
        Long version) { }
