package com.uteexpress.checkout;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.uteexpress.checkout.dto.CheckoutRequest;
import com.uteexpress.checkout.dto.CheckoutQuote;
import com.uteexpress.checkout.dto.Money;
import com.uteexpress.checkout.dto.OrderTotals;
import com.uteexpress.order.dto.OrderTransitionCommand;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.math.BigDecimal;
import java.util.List;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.*;

class CheckoutContractTest {
    @Test
    void subtotalIsAfterProductPromotionAndDiscountTotalContainsOnlyVoucher() {
        // Contract fixture only: production line/promotion calculation is deferred.
        var item = new CheckoutQuote.ItemSnapshot(1L, "Product", new BigDecimal("100000"),
                new BigDecimal("20000"), new BigDecimal("80000"), 1, new BigDecimal("80000"));
        BigDecimal originalLineAmount = item.unitPrice().multiply(BigDecimal.valueOf(item.quantity()));
        BigDecimal productPromotionDiscount = originalLineAmount.subtract(item.lineTotal());
        assertThat(originalLineAmount).isEqualByComparingTo("100000");
        assertThat(productPromotionDiscount).isEqualByComparingTo("20000");
        assertThat(item.finalUnitPrice()).isEqualByComparingTo(item.unitPrice().subtract(item.discountSnapshot()));
        assertThat(item.lineTotal()).isEqualByComparingTo(
                item.finalUnitPrice().multiply(BigDecimal.valueOf(item.quantity())));

        var items = List.of(item);
        BigDecimal subtotal = Money.requireAmount(items.stream()
                .map(CheckoutQuote.ItemSnapshot::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal voucherDiscount = Money.requireAmount(new BigDecimal("10000"));
        OrderTotals totals = OrderTotals.calculate(subtotal, voucherDiscount, new BigDecimal("5000"));

        assertThat(totals.subtotal()).isEqualByComparingTo("80000").isNotEqualByComparingTo("100000");
        assertThat(totals.discountTotal()).isEqualByComparingTo("10000").isNotEqualByComparingTo("30000");
        assertThat(totals.grandTotal()).isEqualByComparingTo("75000");
        assertThat(totals.subtotal().subtract(totals.discountTotal())).isEqualByComparingTo("70000");
    }

    @Test
    void checkoutSelectionsAreValidatedAndDefensivelyCopied() {
        var items = new ArrayList<>(List.of(new CheckoutRequest.Item(1L, 2)));
        var request = new CheckoutRequest("checkout-key", items, 2L, 3L, "STANDARD",
                CheckoutRequest.PaymentMethod.COD, null);
        items.clear();
        assertThat(request.items()).hasSize(1);
        assertThatThrownBy(() -> request.items().clear()).isInstanceOf(UnsupportedOperationException.class);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(request)).isEmpty();
            var invalid = new CheckoutRequest(" ", List.of(new CheckoutRequest.Item(-1L, 0)),
                    null, -1L, "", null, null);
            assertThat(validator.validate(invalid)).extracting(v -> v.getPropertyPath().toString())
                    .contains("checkoutKey", "items[0].productId", "items[0].quantity",
                            "addressId", "shippingProviderId", "shippingServiceCode", "paymentMethod");
            assertThat(validator.validate(new OrderTransitionCommand(-1L, null, -1L, null, null)))
                    .hasSize(4);
        }
    }

    @Test
    void contractPackagesCannotIntroduceFloatOrDoubleMoney() {
        var contracts = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.uteexpress.checkout", "com.uteexpress.order",
                        "com.uteexpress.catalog", "com.uteexpress.shipping", "com.uteexpress.governance");
        noClasses().should().dependOnClassesThat()
                .haveNameMatching("float|double|java\\.lang\\.(Float|Double)").check(contracts);
    }
}
