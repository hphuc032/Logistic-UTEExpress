package com.uteexpress.payment.dto;

/** Route, approved request fields and header only; never accepts payment/amount/actor facts. */
public record ManualRefundCommand(Long orderId, Long expectedOrderVersion,
        Long expectedReturnRequestVersion, String proofReference, String idempotencyKey) { }
