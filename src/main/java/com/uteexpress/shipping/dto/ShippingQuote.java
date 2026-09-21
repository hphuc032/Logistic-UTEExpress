package com.uteexpress.shipping.dto;

import java.math.BigDecimal;

/** Fee is nonnegative whole VND at scale 2; rateVersion permits stale-quote checks. */
public record ShippingQuote(Long providerId, String serviceCode, BigDecimal shippingFee,
        Long rateVersion) { }
