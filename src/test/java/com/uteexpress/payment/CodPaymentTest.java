package com.uteexpress.payment;

import com.uteexpress.checkout.dto.CheckoutRequest.PaymentMethod;
import com.uteexpress.checkout.dto.OrderTotals;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.payment.dto.PaymentStatus;
import com.uteexpress.payment.entity.Payment;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class CodPaymentTest {
    private static final Instant CREATED = Instant.parse("2026-10-03T00:00:00Z");
    private static final Instant COLLECTED = CREATED.plusSeconds(60);

    private static Payment payment(PaymentMethod method) {
        return new Payment(1L, method, OrderTotals.calculate(new BigDecimal("100000"),
                new BigDecimal("10000"), new BigDecimal("17000")), "test-attempt", CREATED);
    }

    @Test void initializesUnpaidAndCollectsExactAmountIgnoringInsignificantScale() {
        var payment = payment(PaymentMethod.COD);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(payment.getPaidAt()).isNull();
        payment.collectCod(new BigDecimal("107000.00"), new BigDecimal("107000.000"), COLLECTED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getAmount()).isEqualByComparingTo("107000");
        assertThat(payment.getPaidAt()).isEqualTo(COLLECTED);
        assertThat(payment.getUpdatedAt()).isEqualTo(COLLECTED);
        assertThat(payment.getProviderReference()).isNull();
    }

    @ParameterizedTest @ValueSource(strings = {"106999", "107001", "0", "107000.01", "-1", "100000000000000000"})
    @NullSource
    void rejectsWrongOrInvalidAmountBeforeAnyMutation(String amount) {
        var payment = payment(PaymentMethod.COD);
        assertThatThrownBy(() -> payment.collectCod(new BigDecimal("107000"),
                amount == null ? null : new BigDecimal(amount), COLLECTED)).isInstanceOf(ApplicationException.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(payment.getPaidAt()).isNull();
        assertThat(payment.getUpdatedAt()).isEqualTo(CREATED);
        assertThat(payment.getAmount()).isEqualByComparingTo("107000");
    }

    @Test void rejectsPaymentAmountDriftEvenWhenClaimMatchesDriftedPayment() {
        var payment = payment(PaymentMethod.COD);
        assertThatThrownBy(() -> payment.collectCod(new BigDecimal("108000"), new BigDecimal("107000"), COLLECTED))
                .isInstanceOf(ApplicationException.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.UNPAID);
    }

    @Test void onlineCannotUseCodTransition() {
        var payment = payment(PaymentMethod.ONLINE);
        assertThatThrownBy(() -> payment.collectCod(new BigDecimal("107000"), new BigDecimal("107000"), COLLECTED))
                .isInstanceOf(ApplicationException.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.UNPAID);
    }

    @Test void repeatConflictsWithoutChangingFirstSuccessfulFacts() {
        var payment = payment(PaymentMethod.COD);
        payment.collectCod(new BigDecimal("107000"), new BigDecimal("107000"), COLLECTED);
        assertThatThrownBy(() -> payment.collectCod(new BigDecimal("107000"), new BigDecimal("107000"), COLLECTED.plusSeconds(1)))
                .isInstanceOf(ApplicationException.class)
                .satisfies(e -> assertThat(((ApplicationException) e).errorCode()).isEqualTo(ErrorCode.CONFLICT));
        assertThat(payment.getPaidAt()).isEqualTo(COLLECTED);
        assertThat(payment.getUpdatedAt()).isEqualTo(COLLECTED);
    }

    @Test void invalidServerTimeCannotPartiallyMarkPaid() {
        var payment = payment(PaymentMethod.COD);
        assertThatThrownBy(() -> payment.collectCod(new BigDecimal("107000"), new BigDecimal("107000"), null))
                .isInstanceOf(ApplicationException.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(payment.getPaidAt()).isNull();
    }
}
