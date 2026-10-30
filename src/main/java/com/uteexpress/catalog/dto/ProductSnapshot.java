package com.uteexpress.catalog.dto;

import java.math.BigDecimal;

/** HP-owned safe server projection, with positive whole-VND unitPrice and no mutable entity. */
public record ProductSnapshot(Long productId, Long shopId, String productName, BigDecimal unitPrice,
        Long version, BigDecimal discountSnapshot, BigDecimal finalUnitPrice) {
    public ProductSnapshot(Long productId, Long shopId, String productName, BigDecimal unitPrice, Long version) {
        this(productId, shopId, productName, unitPrice, version, BigDecimal.ZERO, unitPrice);
    }
}
