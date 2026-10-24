package com.uteexpress.order.dto;

/** Server-derived facts valid for the duration of the Order-owned write callback. */
public record ReviewableOrderItem(Long orderItemId, Long orderId, Long buyerId, Long productId) { }
