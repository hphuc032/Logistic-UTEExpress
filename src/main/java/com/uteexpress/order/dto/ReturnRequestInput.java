package com.uteexpress.order.dto;

import jakarta.validation.constraints.*;

/** HTTP selections only; resource identity is exclusively the route. */
public record ReturnRequestInput(@NotNull @PositiveOrZero Long expectedVersion,
        @NotBlank @Size(max = 1000) String reason) { }
