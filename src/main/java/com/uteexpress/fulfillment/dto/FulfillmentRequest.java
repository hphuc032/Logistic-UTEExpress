package com.uteexpress.fulfillment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

public record FulfillmentRequest(@NotNull @Min(0) Long expectedOrderVersion,
        @NotNull @Min(0) Long expectedShipmentVersion,
        @Digits(integer = 17, fraction = 2) @DecimalMin("0.00") BigDecimal collectedAmount) { }
