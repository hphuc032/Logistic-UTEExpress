package com.uteexpress.promotion;

import com.uteexpress.common.exception.*;
import com.uteexpress.promotion.dto.*;
import com.uteexpress.promotion.entity.Voucher;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;

class VoucherTest {
    private static final Instant START = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-11-01T00:00:00Z");

    @ParameterizedTest @CsvSource({"SHOP,5", "PLATFORM,99"})
    void scopesApplyToEligibleCheckout(VoucherScope scope, long target) {
        var application = voucher(scope, VoucherType.FIXED, "100", null, true).apply(target, bd("1000"), START);
        assertThat(application.voucherId()).isEqualTo(1L);
        assertThat(application.code()).isEqualTo("SAVE");
        assertThat(application.scope()).isEqualTo(scope);
        assertThat(application.discountAmount()).isEqualByComparingTo("100");
    }

    @Test void startIsInclusiveAndEndExclusive() {
        var voucher = voucher(VoucherScope.SHOP, VoucherType.FIXED, "10", null, true);
        assertThat(voucher.apply(5L, bd("100"), START).discountAmount()).isEqualByComparingTo("10");
        rejects(() -> voucher.apply(5L, bd("100"), START.minusNanos(1)), ErrorCode.Detail.VOUCHER_FUTURE);
        rejects(() -> voucher.apply(5L, bd("100"), END), ErrorCode.Detail.VOUCHER_EXPIRED);
    }

    @Test void inactiveWrongShopAndMinimumReject() {
        rejects(() -> voucher(VoucherScope.SHOP, VoucherType.FIXED, "10", null, false)
                .apply(5L, bd("100"), START), ErrorCode.Detail.VOUCHER_INACTIVE);
        var voucher = voucher(VoucherScope.SHOP, VoucherType.FIXED, "10", null, true);
        rejects(() -> voucher.apply(6L, bd("100"), START), ErrorCode.Detail.VOUCHER_WRONG_SHOP);
        rejects(() -> voucher.apply(5L, bd("99"), START), ErrorCode.Detail.VOUCHER_MINIMUM);
    }

    @ParameterizedTest @CsvSource({
        "PERCENTAGE,12.5,101,13", "PERCENTAGE,12.5,100,13", "PERCENTAGE,12.49,100,12",
        "PERCENTAGE,100,100,100", "FIXED,1000,100,100", "FIXED,10,100,10"})
    void calculatesWholeVndHalfUpOnceAndCapsAtSubtotal(VoucherType type, String value, String subtotal, String expected) {
        var applied = voucher(VoucherScope.PLATFORM, type, value, null, true).apply(99L, bd(subtotal), START);
        assertThat(applied.discountAmount()).isEqualByComparingTo(expected);
        assertThat(applied.discountAmount().scale()).isEqualTo(2);
        assertThat(bd(subtotal).subtract(applied.discountAmount()).signum()).isGreaterThanOrEqualTo(0);
    }

    @Test void optionalMaximumCapsAuthorizedDiscount() {
        assertThat(voucher(VoucherScope.PLATFORM, VoucherType.PERCENTAGE, "50", "20", true)
                .apply(99L, bd("100"), START).discountAmount()).isEqualByComparingTo("20");
    }

    @ParameterizedTest @ValueSource(strings = {"bad code", "á", "_SAVE", "<script>", "!"})
    void malformedCodeRejected(String code) { rejects(() -> VoucherCode.normalize(code), ErrorCode.Detail.VOUCHER_INVALID); }

    @Test void emptyAndCanonicalInputsAreDeterministic() {
        assertThat(VoucherCode.normalize(null)).isEmpty();
        assertThat(VoucherCode.normalize("  ")).isEmpty();
        assertThat(VoucherCode.normalize(" save_10-1 ")).isEqualTo("SAVE_10-1");
        rejects(() -> VoucherCode.normalize("X".repeat(65)), ErrorCode.Detail.VOUCHER_INVALID);
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "100.01"})
    void percentageDefinitionRejectsOutOfRange(String value) {
        assertThatThrownBy(() -> voucher(VoucherScope.PLATFORM, VoucherType.PERCENTAGE, value, null, true))
                .isInstanceOf(ApplicationException.class);
    }

    @Test void definitionsRejectFractionalFixedMoneyAndInvalidScopeWindowLimits() {
        assertThatThrownBy(() -> voucher(VoucherScope.PLATFORM, VoucherType.FIXED, "1.5", null, true))
                .isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> new Voucher("SAVE", VoucherScope.SHOP, null, VoucherType.FIXED, bd("10"),
                null, bd("0"), START, END, 1, 1, true, 1L, START)).isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> new Voucher("SAVE", VoucherScope.PLATFORM, null, VoucherType.FIXED, bd("10"),
                null, bd("0"), END, START, 1, 1, true, 1L, START)).isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> new Voucher("SAVE", VoucherScope.PLATFORM, null, VoucherType.FIXED, bd("10"),
                null, bd("0"), START, END, 0, 1, true, 1L, START)).isInstanceOf(ApplicationException.class);
    }

    private Voucher voucher(VoucherScope scope, VoucherType type, String value, String cap, boolean active) {
        var voucher = new Voucher(" save ", scope, scope == VoucherScope.SHOP ? 5L : null, type, bd(value),
                cap == null ? null : bd(cap), bd("100"), START, END, 10, 1, active, 1L, START);
        ReflectionTestUtils.setField(voucher, "id", 1L);
        return voucher;
    }
    private static BigDecimal bd(String input) { return new BigDecimal(input); }
    private void rejects(Runnable work, ErrorCode.Detail detail) {
        assertThatThrownBy(work::run).isInstanceOfSatisfying(ApplicationException.class,
                error -> assertThat(error.detail()).isEqualTo(detail));
    }
}
