package com.uteexpress.payment.service;

import com.uteexpress.checkout.dto.CheckoutRequest.PaymentMethod;
import com.uteexpress.checkout.dto.OrderTotals;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.payment.dto.PaymentStatus;
import com.uteexpress.payment.entity.Payment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CodRefundEligibilityTest {
    private static final Instant CREATED = Instant.parse("2026-10-21T00:00:00Z");
    private static final Instant PAID = CREATED.plusSeconds(60);
    private static final BigDecimal TOTAL = new BigDecimal("107000.00");

    @Test void fullRefundUsesShippingAndAppliedDiscountsAndNeverMutatesPaidPayment() {
        var totals = OrderTotals.calculate(new BigDecimal("100000"), new BigDecimal("10000"), new BigDecimal("17000"));
        var payment = new Payment(1L, PaymentMethod.COD, totals, "cod-order-1", CREATED);
        ReflectionTestUtils.setField(payment, "id", 21L);
        payment.collectCod(totals.grandTotal(), TOTAL, PAID);

        var facts = CodRefundEligibility.requireCollectedCod(List.of(payment), 1L, totals.grandTotal());

        assertThat(facts.paymentId()).isEqualTo(21L);
        assertThat(facts.orderId()).isEqualTo(1L);
        assertThat(facts.amount()).isEqualByComparingTo("107000");
        assertThat(facts.paidAt()).isEqualTo(PAID);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getAmount()).isEqualByComparingTo(TOTAL);
        assertThat(payment.getPaidAt()).isEqualTo(PAID);
        assertThat(payment.getUpdatedAt()).isEqualTo(PAID);
        assertThat(payment.getProviderReference()).isNull(); // COD does not require provider proof.
        assertThat(payment.getExpiredAt()).isNull();
    }

    @Test void rejectsMissingAndAmbiguousPaymentRecordsRatherThanSelectingOnePaidAttempt() {
        Payment valid = valid();
        conflict(() -> CodRefundEligibility.requireCollectedCod(null, 1L, TOTAL));
        conflict(() -> CodRefundEligibility.requireCollectedCod(List.of(), 1L, TOTAL));
        conflict(() -> CodRefundEligibility.requireCollectedCod(Arrays.asList((Payment) null), 1L, TOTAL));
        conflict(() -> CodRefundEligibility.requireCollectedCod(List.of(valid, valid()), 1L, TOTAL));
        conflict(() -> CodRefundEligibility.requireCollectedCod(List.of(valid), null, TOTAL));
        conflict(() -> CodRefundEligibility.requireCollectedCod(List.of(valid), 0L, TOTAL));
        verifyNoInteractions(valid);
    }

    @ParameterizedTest @NullSource
    @ValueSource(strings = {"0", "-1", "107000.01", "100000000000000000", "90000", "106999", "107001"})
    void rejectsZeroPartialFractionalInvalidOrMismatchedOrderGrandTotal(String value) {
        var payment = valid();
        conflict(() -> CodRefundEligibility.requireCollectedCod(List.of(payment), 1L,
                value == null ? null : new BigDecimal(value)));
    }

    @ParameterizedTest @ValueSource(strings = {"id", "zeroId", "foreignOrder", "nullOrder", "online", "nullMethod", "unpaid", "nullStatus", "paidAt", "expired", "amount", "zeroAmount", "negativeAmount", "fractional", "mismatch", "overflow"})
    void rejectsInvalidPersistedCollectionEvidence(String defect) {
        var payment = valid();
        switch (defect) {
            case "id" -> when(payment.getId()).thenReturn(null);
            case "zeroId" -> when(payment.getId()).thenReturn(0L);
            case "foreignOrder" -> when(payment.getOrderId()).thenReturn(2L);
            case "nullOrder" -> when(payment.getOrderId()).thenReturn(null);
            case "online" -> when(payment.getMethod()).thenReturn(PaymentMethod.ONLINE);
            case "nullMethod" -> when(payment.getMethod()).thenReturn(null);
            case "unpaid" -> when(payment.getStatus()).thenReturn(PaymentStatus.UNPAID);
            case "nullStatus" -> when(payment.getStatus()).thenReturn(null);
            case "paidAt" -> when(payment.getPaidAt()).thenReturn(null);
            case "expired" -> when(payment.getExpiredAt()).thenReturn(PAID.plusSeconds(1));
            case "amount" -> when(payment.getAmount()).thenReturn(null);
            case "zeroAmount" -> when(payment.getAmount()).thenReturn(BigDecimal.ZERO);
            case "negativeAmount" -> when(payment.getAmount()).thenReturn(new BigDecimal("-1"));
            case "fractional" -> when(payment.getAmount()).thenReturn(new BigDecimal("107000.01"));
            case "mismatch" -> when(payment.getAmount()).thenReturn(new BigDecimal("106999"));
            case "overflow" -> when(payment.getAmount()).thenReturn(new BigDecimal("100000000000000000"));
            default -> throw new AssertionError(defect);
        }
        conflict(() -> CodRefundEligibility.requireCollectedCod(List.of(payment), 1L, TOTAL));
        verify(payment, never()).collectCod(any(), any(), any());
    }

    @Test void acceptsInsignificantDecimalScaleWithoutRoundingFinancialEvidence() {
        var payment = valid();
        when(payment.getAmount()).thenReturn(new BigDecimal("107000.000"));
        assertThat(CodRefundEligibility.requireCollectedCod(List.of(payment), 1L, new BigDecimal("107000"))
                .amount()).isEqualTo(TOTAL);
    }

    private static Payment valid() {
        var payment = mock(Payment.class);
        when(payment.getId()).thenReturn(21L);
        when(payment.getOrderId()).thenReturn(1L);
        when(payment.getMethod()).thenReturn(PaymentMethod.COD);
        when(payment.getStatus()).thenReturn(PaymentStatus.PAID);
        when(payment.getPaidAt()).thenReturn(PAID);
        when(payment.getAmount()).thenReturn(TOTAL);
        return payment;
    }
    private static void conflict(Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(ApplicationException.class,
                e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CONFLICT));
    }
}
