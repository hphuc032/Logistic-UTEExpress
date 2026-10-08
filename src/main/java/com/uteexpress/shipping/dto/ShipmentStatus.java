package com.uteexpress.shipping.dto;

/** Persisted Shipment vocabulary; fulfillment transitions remain in SHIP-02. */
public enum ShipmentStatus {
    ASSIGNED, PICKED_UP, SHIPPING, DELIVERY_FAILED, DELIVERED, RETURNED_TO_SENDER, CANCELLED
}
