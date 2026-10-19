package com.uteexpress.shipping.dto;

/** Server-read Shipment and relevant failure evidence, not caller-supplied authority. */
public record ShipmentDeliveryFacts(ShipmentFulfillmentFacts shipment, DeliveryFailureEvidence failure) { }
