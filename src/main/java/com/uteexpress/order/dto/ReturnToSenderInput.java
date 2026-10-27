package com.uteexpress.order.dto;

import jakarta.validation.constraints.*;

public record ReturnToSenderInput(@NotNull @PositiveOrZero Long expectedOrderVersion,
        @NotNull @PositiveOrZero Long expectedShipmentVersion,
        @NotBlank @Size(max = 255) String receiptReference) { }
