package com.uteexpress.fulfillment.dto;
import com.uteexpress.shipping.dto.ShipmentStatus;
public record DeliveryAttemptResult(Long orderId, String orderStatus, Long orderVersion,
        ShipmentStatus shipmentStatus, Long shipmentVersion, int attemptCount) { }
