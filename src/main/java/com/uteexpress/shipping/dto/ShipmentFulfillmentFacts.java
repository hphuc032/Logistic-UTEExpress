package com.uteexpress.shipping.dto;

import java.time.Instant;

/** Server-owned persisted evidence for Order prepare/complete; never bind from HTTP. */
public record ShipmentFulfillmentFacts(Long shipmentId, Long orderId, Long shipperId,
        ShipmentStatus status, long version, int attemptCount, int maxAttempts,
        Instant pickedUpAt, Instant deliveredAt) { }
