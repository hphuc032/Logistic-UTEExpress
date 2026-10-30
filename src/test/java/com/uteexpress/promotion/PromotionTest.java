package com.uteexpress.promotion;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.promotion.dto.VendorPromotionForm;
import com.uteexpress.promotion.entity.Promotion;
import com.uteexpress.promotion.service.PromotionPricingService;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class PromotionTest {
    private static final Instant START = Instant.parse("2026-10-08T00:00:00Z");
    @ParameterizedTest @CsvSource({"3,50,2,1", "101,12.5,13,88", "125000,20,25000,100000", "1,100,1,0", "99999999999999999,0,0,99999999999999999"})
    void roundsDiscountOncePerUnit(String base, String percent, String discount, String finalPrice) {
        var result = PromotionPricingService.calculate(new BigDecimal(base), new BigDecimal(percent));
        assertThat(result.discount()).isEqualByComparingTo(discount);
        assertThat(result.effectivePrice()).isEqualByComparingTo(finalPrice);
        assertThat(result.basePrice()).isEqualByComparingTo(base);
    }
    @ParameterizedTest @ValueSource(strings = {"-1", "100.00001"})
    void invalidPricingPercentIsRejected(String value) {
        assertThatThrownBy(() -> PromotionPricingService.calculate(BigDecimal.TEN, new BigDecimal(value)))
                .isInstanceOf(ApplicationException.class);
    }
    @Test void rejectsFractionalOrOverflowingBasePrice() {
        for (String value : new String[]{"0", "1.5", "100000000000000000"}) {
            assertThatThrownBy(() -> PromotionPricingService.calculate(new BigDecimal(value), BigDecimal.TEN))
                    .isInstanceOf(ApplicationException.class);
        }
    }
    @Test void updatesKeepProductAndCreatorAndPreservePercentPrecision() {
        var p = new Promotion(1L, 2L, " Sale ", new BigDecimal("12.123456789"), START, START.plusSeconds(10), true, START);
        p.update("Changed", BigDecimal.TEN, START.plusSeconds(1), START.plusSeconds(20), false, START);
        assertThat(p.getProductId()).isEqualTo(1L); assertThat(p.getCreatedBy()).isEqualTo(2L);
        assertThat(p.isActive()).isFalse(); assertThat(p.getName()).isEqualTo("Changed");
    }
    @ParameterizedTest @ValueSource(strings = {"0", "-1", "100.00001"})
    void rejectsInvalidDomainPercent(String percent) {
        assertThatThrownBy(() -> new Promotion(1L, 2L, "Sale", new BigDecimal(percent), START, START.plusSeconds(1), true, START))
                .isInstanceOf(ApplicationException.class);
    }
    @Test void subMicrosecondWindowCannotCollapseAtPersistence() {
        assertThatThrownBy(() -> new Promotion(1L, 2L, "Sale", BigDecimal.TEN, START, START.plusNanos(1), true, START))
                .isInstanceOf(ApplicationException.class);
    }
    @Test void formValidatesWindowAndPercentage() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var form = new VendorPromotionForm(); form.setName("Sale"); form.setDiscountPercent(BigDecimal.TEN);
            form.setStartsAt(LocalDateTime.of(2026, 10, 8, 0, 0)); form.setEndsAt(form.getStartsAt());
            assertThat(factory.getValidator().validate(form)).isNotEmpty();
            form.setEndsAt(form.getStartsAt().plusDays(1));
            assertThat(factory.getValidator().validate(form)).isEmpty();
        }
    }
}
