package com.uteexpress.fulfillment.dto;
import jakarta.validation.constraints.*;
public record DeliveryFailureRequest(@NotNull @PositiveOrZero Long expectedOrderVersion,
        @NotNull @PositiveOrZero Long expectedShipmentVersion,
        @NotBlank @Size(max=1000) String failureReason,
        @NotBlank @Size(max=255) String evidenceReference) { }
