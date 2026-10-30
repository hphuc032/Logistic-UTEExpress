package com.uteexpress.order.dto;

import jakarta.validation.constraints.*;

/** Ops attests physical receipt. Identity, status, timestamps, quantities and prices are server-owned. */
public record ReturnToSenderCommand(@NotNull @Positive Long orderId,
        @NotNull @PositiveOrZero Long expectedOrderVersion,
        @NotNull @PositiveOrZero Long expectedShipmentVersion,
        @NotBlank @Size(max = 255) String receiptReference) { }
