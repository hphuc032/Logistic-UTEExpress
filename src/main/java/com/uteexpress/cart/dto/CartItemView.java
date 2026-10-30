package com.uteexpress.cart.dto;

import java.math.BigDecimal;

public record CartItemView(Long id, Long productId, String productName, int quantity,
        boolean selected, BigDecimal unitPrice, BigDecimal subtotal, int stock, CartItemStatus status,
        BigDecimal originalUnitPrice, BigDecimal discountSnapshot) {
    public CartItemView(Long id, Long productId, String productName, int quantity, boolean selected,
            BigDecimal unitPrice, BigDecimal subtotal, int stock, CartItemStatus status) {
        this(id, productId, productName, quantity, selected, unitPrice, subtotal, stock, status,
                unitPrice, BigDecimal.ZERO);
    }
    public boolean available() { return status == CartItemStatus.AVAILABLE; }
}
