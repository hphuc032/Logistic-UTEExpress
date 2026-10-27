package com.uteexpress.order.dto;

/** Resource/version selections only; Buyer identity comes from the authenticated principal. */
public record BuyerCancellationCommand(Long orderId, Long expectedOrderVersion) { }
