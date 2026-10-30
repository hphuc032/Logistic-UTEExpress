package com.uteexpress.checkout.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Compatibility facade for the shared VND contract; existing checkout callers retain their API. */
public final class Money {
    public static final int STORAGE_SCALE = com.uteexpress.common.money.Money.STORAGE_SCALE;
    public static final RoundingMode ROUNDING = com.uteexpress.common.money.Money.ROUNDING;

    private Money() { }

    public static BigDecimal round(BigDecimal raw) {
        return com.uteexpress.common.money.Money.round(raw);
    }

    /** Stored quotes/snapshots/payment evidence must already be whole VND: never hide a mismatch. */
    public static BigDecimal requireAmount(BigDecimal amount) {
        return com.uteexpress.common.money.Money.requireAmount(amount);
    }
}
