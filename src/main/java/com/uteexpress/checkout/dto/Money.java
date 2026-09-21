package com.uteexpress.checkout.dto;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** VND: whole dong HALF_UP at calculation boundaries, represented at DB scale 2. */
public final class Money {
    public static final int STORAGE_SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal MAX = new BigDecimal("99999999999999999.99");

    private Money() { }

    public static BigDecimal round(BigDecimal raw) {
        if (raw == null || raw.signum() < 0 || raw.compareTo(MAX) > 0) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        }
        BigDecimal result = raw.setScale(0, ROUNDING).setScale(STORAGE_SCALE);
        if (result.compareTo(MAX) > 0) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        }
        return result;
    }

    /** Stored quotes/snapshots/payment evidence must already be whole VND: never hide a mismatch. */
    public static BigDecimal requireAmount(BigDecimal amount) {
        BigDecimal normalized = round(amount);
        if (amount.compareTo(normalized) != 0) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        }
        return normalized;
    }
}
