package com.uteexpress.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Version is a stale-form guard. No identity, quantity, payment or target status fields. */
public record VendorOrderMutation(@NotNull @PositiveOrZero Long expectedVersion,
        @Size(max = 1000) String reason) { }
