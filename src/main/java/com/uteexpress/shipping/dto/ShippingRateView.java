package com.uteexpress.shipping.dto;
import java.math.BigDecimal;
public record ShippingRateView(Long id, Long providerId, String providerName, String serviceCode,
        String destinationRegion, BigDecimal fee, boolean active, Long version) { }
