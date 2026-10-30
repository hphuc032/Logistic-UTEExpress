package com.uteexpress.order.dto;

/** Public order vocabulary; delivery failure belongs exclusively to ShipmentStatus. */
public enum OrderStatus {
    NEW, CONFIRMED, PICKED_UP, SHIPPING, DELIVERED, CANCELLED,
    RETURN_REQUESTED, RETURNED, REFUNDED
}
