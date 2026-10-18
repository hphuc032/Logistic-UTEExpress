package com.uteexpress.shipping.dto;

/** QD-owned Shipment-only intents; neither operation is an Order status transition. */
public enum DeliveryAttemptOperation {
    REPORT_DELIVERY_FAILURE,
    RETRY_DELIVERY
}
