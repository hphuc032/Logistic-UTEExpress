package com.uteexpress.checkout.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotBlank;

/** Preview selections only. Items and quantities come exclusively from the authenticated user's cart. */
public record QuoteRequest(@NotNull @Positive Long addressId,
        @NotNull @Positive Long shippingProviderId,
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,31}") String shippingServiceCode) { }
