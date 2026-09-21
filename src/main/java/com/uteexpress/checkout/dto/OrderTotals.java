package com.uteexpress.checkout.dto;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import java.math.BigDecimal;

/**
 * Names map directly to orders.subtotal/discount_total/shipping_fee/grand_total.
 * Subtotal is SUM(item lineTotal) after product promotion; discountTotal contains only
 * subsequent order-level discounts (currently vouchers), never product promotion discounts.
 * Grand total is subtotal - discountTotal + shippingFee. Callers supply the line aggregation;
 * this value contract validates amounts and arithmetic, not line calculation/provenance.
 */
public record OrderTotals(BigDecimal subtotal, BigDecimal discountTotal,
        BigDecimal shippingFee, BigDecimal grandTotal) {
    public OrderTotals {
        subtotal = Money.requireAmount(subtotal);
        discountTotal = Money.requireAmount(discountTotal);
        shippingFee = Money.requireAmount(shippingFee);
        grandTotal = Money.requireAmount(grandTotal);
        if (discountTotal.compareTo(subtotal) > 0
                || subtotal.subtract(discountTotal).add(shippingFee).compareTo(grandTotal) != 0) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        }
    }

    public static OrderTotals calculate(BigDecimal subtotal, BigDecimal discountTotal, BigDecimal shippingFee) {
        BigDecimal s = Money.requireAmount(subtotal);
        BigDecimal d = Money.requireAmount(discountTotal);
        BigDecimal f = Money.requireAmount(shippingFee);
        return new OrderTotals(s, d, f, s.subtract(d).add(f));
    }

    public void requirePaymentAmount(BigDecimal paymentAmount) {
        if (Money.requireAmount(paymentAmount).compareTo(grandTotal) != 0) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
    }
}
