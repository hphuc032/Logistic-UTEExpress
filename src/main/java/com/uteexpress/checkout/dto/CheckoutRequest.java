package com.uteexpress.checkout.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

/** Selections only. Server resolves shop, buyer, address ownership and every price. */
public record CheckoutRequest(@NotBlank String checkoutKey, @NotEmpty List<@NotNull @Valid Item> items,
        @NotNull @Positive Long addressId, @NotNull @Positive Long shippingProviderId,
        @NotBlank String shippingServiceCode, @NotNull PaymentMethod paymentMethod, String voucherCode) {
    public CheckoutRequest {
        if (items != null) { items = List.copyOf(items); }
    }

    public record Item(@NotNull @Positive Long productId, @Positive int quantity) { }

    public enum PaymentMethod { COD, ONLINE }
}
