package com.uteexpress.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/** Ready records preparation time; it does not change OrderStatus. */
public record OrderReadyCommand(@NotNull @Positive Long orderId,
        @NotNull @PositiveOrZero Long expectedVersion) { }
