package com.uteexpress.cart.dto;

import jakarta.validation.constraints.NotNull;

public record SelectCartItemRequest(@NotNull Boolean selected) { }
