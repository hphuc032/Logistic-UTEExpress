package com.uteexpress.promotion.dto;

import com.uteexpress.common.money.Money;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import java.math.BigDecimal;

/** Immutable applied facts only; historical orders never resolve live voucher rules. */
public record VoucherApplication(Long voucherId, String code, VoucherScope scope, BigDecimal discountAmount) {
    public VoucherApplication {
        if (voucherId == null || voucherId <= 0 || scope == null || code == null || code.isEmpty()
                || !code.equals(VoucherCode.normalize(code))) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        }
        discountAmount = Money.requireAmount(discountAmount);
    }
}
