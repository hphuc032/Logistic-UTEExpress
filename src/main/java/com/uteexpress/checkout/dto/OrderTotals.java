package com.uteexpress.checkout.dto;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import java.math.BigDecimal;

/** Names map directly to orders.subtotal/discount_total/shipping_fee/grand_total. */
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
