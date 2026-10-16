package com.uteexpress.order.dto;

import jakarta.validation.constraints.*;

public record ReturnDecisionInput(@NotNull @PositiveOrZero Long expectedVersion,
        @NotNull @PositiveOrZero Long expectedRequestVersion,
        @Size(max = 1000) String reason) { }
