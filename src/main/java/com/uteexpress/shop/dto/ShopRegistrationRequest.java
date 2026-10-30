package com.uteexpress.shop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record ShopRegistrationRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 120)
        @Pattern(regexp = "[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*") String slug,
        @Size(max = 2000) String description,
        @NotBlank @Size(max = 500) String pickupAddress) {
    public ShopRegistrationRequest {
        name = trim(name);
        slug = slug == null ? null : slug.trim().toLowerCase(Locale.ROOT);
        description = trim(description);
        pickupAddress = trim(pickupAddress);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
