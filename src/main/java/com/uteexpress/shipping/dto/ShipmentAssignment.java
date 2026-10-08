package com.uteexpress.shipping.dto;

import java.math.BigDecimal;

public record ShipmentAssignment(Long id, Long orderId, Long providerId, String serviceCode,
        BigDecimal fee, Long shipperId, ShipmentStatus status, Long version) { }
