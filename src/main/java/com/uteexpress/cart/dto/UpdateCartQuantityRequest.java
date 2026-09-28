package com.uteexpress.cart.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record UpdateCartQuantityRequest(@NotNull @Positive Integer quantity) { }
