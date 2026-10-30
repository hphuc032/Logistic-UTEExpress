package com.uteexpress.promotion.service;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.common.money.Money;
import com.uteexpress.promotion.dto.ProductPrice;
import java.math.BigDecimal;

/** Canonical Java calculation, also parity-tested against product_prices_at PostgreSQL projection. */
public final class PromotionPricingService {
    private PromotionPricingService() { }

    public static ProductPrice calculate(BigDecimal basePrice, BigDecimal discountPercent) {
        BigDecimal base = Money.requireAmount(basePrice);
        if (base.signum() <= 0 || discountPercent == null || discountPercent.signum() < 0
                || discountPercent.compareTo(new BigDecimal("100")) > 0) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        }
        BigDecimal discount = Money.round(base.multiply(discountPercent).movePointLeft(2));
        return new ProductPrice(base, discount, base.subtract(discount));
    }
}
