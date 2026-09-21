package com.uteexpress.shipping.dto;

/** QD-owned integration vocabulary; no Shipment entity or workflow is implemented here. */
public enum ShipmentStatus {
    ASSIGNED, PICKED_UP, SHIPPING, DELIVERY_FAILED, DELIVERED, RETURNED_TO_SENDER, CANCELLED
}
