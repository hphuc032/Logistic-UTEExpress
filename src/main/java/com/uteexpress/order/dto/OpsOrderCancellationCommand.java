package com.uteexpress.order.dto;

/** System-wide intent only. Identity, eligibility and effects are resolved by the server. */
public record OpsOrderCancellationCommand(Long orderId, OrderStatus expectedOrderStatus,
        Long expectedOrderVersion, OpsShipmentExpectation expectedShipment,
        Long expectedShipmentVersion, OpsCancellationReason reasonCode) { }
