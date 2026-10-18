package com.uteexpress.fulfillment.dto;
import jakarta.validation.constraints.*;
public record RetryDeliveryRequest(@NotNull @PositiveOrZero Long expectedOrderVersion,
        @NotNull @PositiveOrZero Long expectedShipmentVersion) { }
