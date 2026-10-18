package com.uteexpress.order.dto;

/** Submitting receiptReference is the vendor's attestation of physical receipt. */
public record ReceiveReturnCommand(Long orderId, Long expectedVersion, Long expectedRequestVersion,
        String receiptReference) { }
