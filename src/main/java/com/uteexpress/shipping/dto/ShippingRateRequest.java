package com.uteexpress.shipping.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record ShippingRateRequest(@NotNull @Positive Long providerId,
        @NotBlank @Pattern(regexp="[A-Z][A-Z0-9_]{0,31}") String serviceCode,
        @NotBlank @Pattern(regexp="[A-Z0-9][A-Z0-9_-]{0,19}") String destinationRegion,
        @NotNull @DecimalMin("0") @Digits(integer=17,fraction=2) BigDecimal fee,
        boolean active, @PositiveOrZero Long version) {
    public ShippingRateRequest {
        serviceCode=serviceCode == null ? null : serviceCode.strip();
        destinationRegion=destinationRegion == null ? null : destinationRegion.strip();
    }
    @AssertTrue(message="Phí phải là số nguyên VND.")
    public boolean isWholeDong() { return fee == null || fee.stripTrailingZeros().scale() <= 0; }
}
