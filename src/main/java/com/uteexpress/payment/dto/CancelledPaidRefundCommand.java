package com.uteexpress.payment.dto;

/** Resource/version selections and proof only; collection/amount/actor are server facts. */
public record CancelledPaidRefundCommand(Long orderId, Long expectedOrderVersion,
        String proofReference, String idempotencyKey) { }
