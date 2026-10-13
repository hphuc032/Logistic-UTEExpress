package com.uteexpress.order.dto;

import jakarta.validation.constraints.*;

public record RequestReturnCommand(@NotNull @Positive Long orderId,
        @NotNull @PositiveOrZero Long expectedVersion,
        @NotBlank @Size(max = 1000) String reason) { }
