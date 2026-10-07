package com.uteexpress.shipping.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record ReassignShipmentRequest(@NotNull @Positive Long shipperId,
        @NotNull @PositiveOrZero Long expectedShipmentVersion) { }
