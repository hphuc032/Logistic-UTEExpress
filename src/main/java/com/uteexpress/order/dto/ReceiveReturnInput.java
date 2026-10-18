package com.uteexpress.order.dto;

import jakarta.validation.constraints.*;

public record ReceiveReturnInput(@NotNull @PositiveOrZero Long expectedVersion,
        @NotNull @PositiveOrZero Long expectedRequestVersion,
        @NotBlank @Size(max = 255) String receiptReference) { }
