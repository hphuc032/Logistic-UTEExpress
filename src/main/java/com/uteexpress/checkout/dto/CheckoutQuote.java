package com.uteexpress.checkout.dto;

import java.math.BigDecimal;
import java.util.List;

/** Server-produced immutable checkout facts; recompute/validate at submit, never trust a returned quote. */
public record CheckoutQuote(Long shopId, List<ItemSnapshot> items, AddressSnapshot address,
        OrderTotals totals, Long shippingProviderId, String shippingServiceSnapshot,
        Long commissionPolicyId, BigDecimal commissionRateSnapshot, BigDecimal commissionAmount) {
    public CheckoutQuote { items = List.copyOf(items); }

    public record ItemSnapshot(Long productId, String productNameSnapshot, BigDecimal unitPrice,
            BigDecimal discountSnapshot, BigDecimal finalUnitPrice, int quantity, BigDecimal lineTotal) { }

    public record AddressSnapshot(String receiverName, String phone, String provinceCode,
            String district, String detail) { }
}
