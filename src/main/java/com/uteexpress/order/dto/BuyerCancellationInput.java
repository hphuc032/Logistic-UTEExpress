package com.uteexpress.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Minimal JSON request body; no client actor, status, quantity or payment facts. */
public record BuyerCancellationInput(@NotNull @PositiveOrZero Long expectedOrderVersion) { }
