package com.uteexpress.order.dto;

import java.time.Instant;

/** Vendor projection reuses safe persisted facts, with lifecycle concurrency/preparation fields. */
public record VendorOrderDetail(BuyerOrderDetail facts, Long version, Instant readyAt) { }
