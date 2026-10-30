package com.uteexpress.checkout.dto;

import java.util.Objects;

/** Exactly one shop and one prospective order. Informational only, never place-order evidence. */
public record CheckoutPreview(CheckoutQuote quote) {
    public CheckoutPreview { Objects.requireNonNull(quote); }
}
