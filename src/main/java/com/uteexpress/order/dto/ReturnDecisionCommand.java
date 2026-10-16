package com.uteexpress.order.dto;

import jakarta.validation.constraints.*;

public record ReturnDecisionCommand(@NotNull @Positive Long orderId,
        @NotNull @PositiveOrZero Long expectedVersion,
        @NotNull @PositiveOrZero Long expectedRequestVersion,
        @Size(max = 1000) String reason) { }
