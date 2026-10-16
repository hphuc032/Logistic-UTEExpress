package com.uteexpress.order.dto;

import java.time.Instant;

public record ReturnManagementView(Long orderId, OrderStatus orderStatus, Long expectedVersion,
        Instant deliveredAt, Instant deadline, boolean canRequest, boolean canDecide,
        ReturnRequestView request) { }
