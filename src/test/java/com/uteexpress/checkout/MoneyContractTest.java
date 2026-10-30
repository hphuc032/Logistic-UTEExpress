package com.uteexpress.checkout;

import com.uteexpress.checkout.dto.Money;
import com.uteexpress.checkout.dto.OrderTotals;
import com.uteexpress.common.exception.ApplicationException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

class MoneyContractTest {
    private static BigDecimal amount(String value) { return new BigDecimal(value); }

    @Test
    void calculatesServerTotalsWithoutBinaryFloatingPoint() {
        OrderTotals totals = OrderTotals.calculate(amount("100000"), amount("15000"), amount("20000"));
        assertThat(totals.grandTotal()).isEqualTo(amount("105000.00"));
        totals.requirePaymentAmount(amount("105000.000"));
    }

    @Test
    void roundsHalfUpToDongAndKeepsDatabaseScale() {
        assertThat(Money.round(amount("10.49"))).isEqualTo(amount("10.00"));
        assertThat(Money.round(amount("10.50"))).isEqualTo(amount("11.00"));
        assertThat(Money.round(amount("0.005"))).isEqualTo(amount("0.00"));
    }

    @Test
    void rejectsNegativeNullFractionalAndOverflowAmounts() {
        for (String value : new String[]{"-0.01", "-1", "100000000000000000.00", "99999999999999999.99"}) {
            assertThatThrownBy(() -> Money.round(amount(value))).isInstanceOf(ApplicationException.class);
        }
        assertThatThrownBy(() -> Money.round(null)).isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> Money.requireAmount(amount("10.01"))).isInstanceOf(ApplicationException.class);
        assertThat(Money.requireAmount(amount("99999999999999999")))
                .isEqualTo(amount("99999999999999999.00"));
    }

    @Test
    void rejectsInvalidComponentsEvenIfTheyCouldCancelEachOtherOut() {
        assertThatThrownBy(() -> OrderTotals.calculate(amount("10"), amount("11"), amount("5")))
                .isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> OrderTotals.calculate(amount("-1"), amount("0"), amount("5")))
                .isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> OrderTotals.calculate(amount("10"), amount("-1"), amount("5")))
                .isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> OrderTotals.calculate(amount("10"), amount("0"), amount("-1")))
                .isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> new OrderTotals(amount("10"), amount("0"), amount("1"), amount("12")))
                .isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> OrderTotals.calculate(amount("99999999999999999"), amount("0"), amount("1")))
                .isInstanceOf(ApplicationException.class);
    }

    @Test
    void zeroTotalIsValidButPaymentMustMatchExactlyWithoutRounding() {
        OrderTotals free = OrderTotals.calculate(amount("100"), amount("100"), amount("0"));
        free.requirePaymentAmount(BigDecimal.ZERO);
        for (String wrong : new String[]{"-1", "0.01", "1"}) {
            assertThatThrownBy(() -> free.requirePaymentAmount(amount(wrong)))
                    .isInstanceOf(ApplicationException.class);
        }
    }
}
