package com.uteexpress.cart.dto;

import java.math.BigDecimal;
import java.util.List;

/** Basic subtotal of all lines at current catalog prices; not a checkout quote. */
public record CartView(Long id, List<CartItemView> items, BigDecimal subtotal) {
    public CartView { items = List.copyOf(items); }
}
