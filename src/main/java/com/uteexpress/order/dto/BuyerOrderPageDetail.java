package com.uteexpress.order.dto;

/** HTML-only advisory action data; the cancellation command rechecks every guard under lock. */
public record BuyerOrderPageDetail(BuyerOrderDetail order, Long cancellationVersion) { }
