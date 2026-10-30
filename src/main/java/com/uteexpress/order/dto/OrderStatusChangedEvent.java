package com.uteexpress.order.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Internal after-commit event; actorId is null only for a verified system action. */
public record OrderStatusChangedEvent(UUID eventId, Long orderId, OrderStatus fromStatus,
        OrderStatus toStatus, Long actorId, Instant occurredAt, String reason) {
    public OrderStatusChangedEvent {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(orderId);
        Objects.requireNonNull(fromStatus);
        Objects.requireNonNull(toStatus);
        Objects.requireNonNull(occurredAt);
    }
}
