package com.uteexpress.shipping.dto;

import jakarta.validation.constraints.*;

public record ShippingProviderRequest(
        @NotBlank @Pattern(regexp="[A-Z][A-Z0-9_]{0,31}") String code,
        @NotBlank @Size(max=120) String name, boolean active, @PositiveOrZero Long version) {
    public ShippingProviderRequest {
        name = name == null ? null : name.strip();
        code = code == null ? null : code.strip();
    }
}
