package com.uteexpress.shipping.dto;

import java.time.Instant;

/** Immutable server evidence. Exposed to the Order coordinator only, never bound from HTTP. */
public record ReturnToSenderReceipt(Long orderId, Long shipmentId, Long actorId,
        long originalOrderVersion, long originalShipmentVersion, String reference, Instant receivedAt) { }
