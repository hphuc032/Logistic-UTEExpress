package com.uteexpress.cart.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddCartProductRequest(@NotNull @Positive Long productId,
        @NotNull @Positive Integer quantity) { }
