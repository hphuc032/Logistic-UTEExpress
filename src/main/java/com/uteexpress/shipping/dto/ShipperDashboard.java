package com.uteexpress.shipping.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** All recorded terminal outcomes for the current assignee; never delivery-attempt productivity. */
public record ShipperDashboard(long total, long assigned, long pickedUp, long shipping,
        long delivered, long failed, long returnedToSender, long cancelled) {
    public BigDecimal successRate() {
        long outcomes = delivered + returnedToSender;
        return outcomes == 0 ? null : BigDecimal.valueOf(delivered).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(outcomes), 1, RoundingMode.HALF_UP);
    }
}
