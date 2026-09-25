package com.uteexpress.cart.dto;

import java.math.BigDecimal;

public record CartItemView(Long id, Long productId, String productName, int quantity,
        boolean selected, BigDecimal unitPrice, BigDecimal subtotal) { }
