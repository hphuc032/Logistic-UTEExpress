package com.uteexpress.order.service;

import com.uteexpress.checkout.dto.CheckoutRequest;
import com.uteexpress.common.exception.ApplicationException;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class CheckoutRequestHashTest {
    @Test void orderingOptionalWhitespaceAndKeyDoNotChangeBusinessHash() {
        var a = request("one", List.of(new CheckoutRequest.Item(1L, 2), new CheckoutRequest.Item(3L, 4)), null);
        var b = request("other", List.of(new CheckoutRequest.Item(3L, 4), new CheckoutRequest.Item(1L, 2)), "  ");
        assertThat(CheckoutRequestHash.calculate(a)).hasSize(64).isEqualTo(CheckoutRequestHash.calculate(b));
        assertThat(CheckoutRequestHash.calculate(request("one", List.of(new CheckoutRequest.Item(1L, 3)), null)))
                .isNotEqualTo(CheckoutRequestHash.calculate(a));
    }

    @Test void everyBusinessSelectionAffectsHash() {
        var base = request("one", List.of(new CheckoutRequest.Item(1L, 2)), null);
        var variants = List.of(
                new CheckoutRequest("one", base.items(), 9L, 3L, "STANDARD", base.paymentMethod(), null),
                new CheckoutRequest("one", base.items(), 2L, 9L, "STANDARD", base.paymentMethod(), null),
                new CheckoutRequest("one", base.items(), 2L, 3L, "EXPRESS", base.paymentMethod(), null),
                new CheckoutRequest("one", base.items(), 2L, 3L, "STANDARD", CheckoutRequest.PaymentMethod.ONLINE, null),
                request("one", base.items(), "SAVE"),
                request("one", List.of(new CheckoutRequest.Item(2L, 2)), null));
        for (var variant : variants) assertThat(CheckoutRequestHash.calculate(variant))
                .isNotEqualTo(CheckoutRequestHash.calculate(base));
    }

    @Test void malformedAndDuplicateProductsRejected() {
        assertThatThrownBy(() -> CheckoutRequestHash.calculate(null)).isInstanceOf(ApplicationException.class);
        for (var items : List.of(List.<CheckoutRequest.Item>of(), List.of(new CheckoutRequest.Item(1L, 0)),
                List.of(new CheckoutRequest.Item(1L, 1), new CheckoutRequest.Item(1L, 1)))) {
            assertThatThrownBy(() -> CheckoutRequestHash.calculate(request("one", items, null)))
                    .isInstanceOf(ApplicationException.class);
        }
    }

    private CheckoutRequest request(String key, List<CheckoutRequest.Item> items, String voucher) {
        return new CheckoutRequest(key, items, 2L, 3L, "STANDARD", CheckoutRequest.PaymentMethod.COD, voucher);
    }
}
