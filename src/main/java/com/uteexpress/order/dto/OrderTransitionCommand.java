package com.uteexpress.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/** No actor, ownership, timestamp, payment evidence or arbitrary target status from callers. */
public record OrderTransitionCommand(
        @NotNull @Positive Long orderId,
        @NotNull OrderStatus expectedStatus,
        @NotNull @PositiveOrZero Long expectedVersion,
        @NotNull OrderAction action,
        String reason) {
}
