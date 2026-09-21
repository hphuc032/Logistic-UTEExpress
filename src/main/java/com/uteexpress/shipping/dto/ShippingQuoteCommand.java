package com.uteexpress.shipping.dto;

/** Internal server-resolved address, never raw client ownership or price claims. */
public record ShippingQuoteCommand(Long shopId, Long providerId, String serviceCode,
        String provinceCode, String district, String detail) { }
