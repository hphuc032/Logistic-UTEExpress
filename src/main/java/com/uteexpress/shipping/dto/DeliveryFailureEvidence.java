package com.uteexpress.shipping.dto;

import java.time.Instant;

/** Persisted attestation; never bind from HTTP or accept as a guard capability. */
public record DeliveryFailureEvidence(Long id, Long shipmentId, int attemptNumber,
        long sourceShipmentVersion, Long shipperId, String failureReason,
        String evidenceReference, Instant recordedAt) { }
