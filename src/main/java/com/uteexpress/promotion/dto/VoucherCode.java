package com.uteexpress.promotion.dto;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import java.util.Locale;

/** Canonical identity shared by lookup and request hashing; empty means no voucher. */
public final class VoucherCode {
    private VoucherCode() { }

    public static String normalize(String input) {
        if (input == null) return "";
        if (input.length() > 128) throw new ApplicationException(ErrorCode.Detail.VOUCHER_INVALID);
        String code = input.strip();
        if (code.isEmpty()) return "";
        if (!code.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")) {
            throw new ApplicationException(ErrorCode.Detail.VOUCHER_INVALID);
        }
        return code.toUpperCase(Locale.ROOT);
    }
}
