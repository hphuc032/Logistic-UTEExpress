package com.uteexpress.catalog.dto;

import java.math.BigDecimal;

/** Current display data, including products that are no longer purchasable. */
public record CartProductSnapshot(Long productId, String productName, BigDecimal unitPrice,
        int stock, boolean purchasable, BigDecimal discountSnapshot, BigDecimal finalUnitPrice) {
    public CartProductSnapshot(Long productId, String productName, BigDecimal unitPrice, int stock, boolean purchasable) {
        this(productId, productName, unitPrice, stock, purchasable, BigDecimal.ZERO, unitPrice);
    }
}
