package com.uteexpress.order.dto;

public record ReturnToSenderResult(Long orderId, Long shipmentId, OrderStatus status,
        long orderVersion, long shipmentVersion, boolean replayed) { }
