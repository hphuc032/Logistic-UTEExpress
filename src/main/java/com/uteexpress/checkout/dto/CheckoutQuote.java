package com.uteexpress.checkout.dto;

import java.math.BigDecimal;
import java.util.List;
import com.uteexpress.promotion.dto.VoucherApplication;

/**
 * Server-produced immutable checkout facts for one shop; recompute/validate at submit.
 * CHK-01 previews leave commission fields null (unresolved); this is not a persistable order command.
 */
public record CheckoutQuote(Long shopId, List<ItemSnapshot> items, AddressSnapshot address,
        OrderTotals totals, Long shippingProviderId, String shippingServiceSnapshot,
        Long commissionPolicyId, BigDecimal commissionRateSnapshot, BigDecimal commissionAmount,
        VoucherApplication voucher) {
    public CheckoutQuote { items = List.copyOf(items); }

    public CheckoutQuote(Long shopId, List<ItemSnapshot> items, AddressSnapshot address,
            OrderTotals totals, Long shippingProviderId, String shippingServiceSnapshot,
            Long commissionPolicyId, BigDecimal commissionRateSnapshot, BigDecimal commissionAmount) {
        this(shopId, items, address, totals, shippingProviderId, shippingServiceSnapshot,
                commissionPolicyId, commissionRateSnapshot, commissionAmount, null);
    }

    /**
     * unitPrice is the original unit-price snapshot; discountSnapshot is per-unit product promotion.
     * finalUnitPrice = unitPrice - discountSnapshot; lineTotal = finalUnitPrice * quantity.
     * Order subtotal sums these post-promotion line totals; vouchers apply at order level.
     */
    public record ItemSnapshot(Long productId, String productNameSnapshot, BigDecimal unitPrice,
            BigDecimal discountSnapshot, BigDecimal finalUnitPrice, int quantity, BigDecimal lineTotal) { }

    public record AddressSnapshot(String receiverName, String phone, String provinceCode,
            String district, String detail) { }
}
