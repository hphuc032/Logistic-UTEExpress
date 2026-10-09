package com.uteexpress.fulfillment.dto;

public record FulfillmentResult(Long orderId, String status, Long orderVersion, Long shipmentVersion) { }
