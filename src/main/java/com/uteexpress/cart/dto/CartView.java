package com.uteexpress.cart.dto;

import java.math.BigDecimal;
import java.util.List;

/** Current valid-line subtotals; invalid lines contribute zero. Not a checkout quote. */
public record CartView(Long id, List<CartItemView> items, BigDecimal subtotal) {
    public CartView { items = List.copyOf(items); }
    @com.fasterxml.jackson.annotation.JsonProperty("selectedSubtotal")
    public BigDecimal selectedSubtotal() {
        return items.stream().filter(CartItemView::selected).map(CartItemView::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
