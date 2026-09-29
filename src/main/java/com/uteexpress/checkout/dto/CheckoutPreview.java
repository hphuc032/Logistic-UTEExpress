package com.uteexpress.checkout.dto;

import java.util.List;

/** One group per prospective order. Informational only: never accepted as place-order evidence. */
public record CheckoutPreview(List<CheckoutQuote> groups, OrderTotals totals) {
    public CheckoutPreview { groups = List.copyOf(groups); }
}
