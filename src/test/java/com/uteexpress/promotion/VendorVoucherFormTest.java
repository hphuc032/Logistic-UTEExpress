package com.uteexpress.promotion;

import com.uteexpress.promotion.dto.*;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;

class VendorVoucherFormTest {
    private static ValidatorFactory factory;
    private static Validator validator;
    @BeforeAll static void validators() { factory = Validation.buildDefaultValidatorFactory(); validator = factory.getValidator(); }
    @AfterAll static void close() { factory.close(); }

    @ParameterizedTest @ValueSource(strings = {"10", "0.125", "0.000001", "100"})
    void acceptsDomainPercentagePrecision(String value) {
        var form = form();
        form.setValue(new BigDecimal(value));
        assertThat(validator.validate(form)).isEmpty();
    }
    @Test void fixedDiscountRejectsFractionalDong() {
        var form = form();
        form.setType(VoucherType.FIXED);
        form.setValue(new BigDecimal("10.5"));
        assertThat(validator.validate(form)).extracting(violation -> violation.getPropertyPath().toString()).contains("validDiscount");
    }
    @Test void updateRequiresVersionEvenForOtherwiseValidInput() {
        var form = form();
        assertThat(validator.validate(form)).isEmpty();
        form.setVersion(null);
        assertThat(validator.validate(form)).extracting(violation -> violation.getPropertyPath().toString()).containsExactly("version");
    }
    @ParameterizedTest @CsvSource({"false,0.00,500.00", "true,0.00,500.00",
            "false,500.00,500.00", "true,500.00,500.00",
            "false,99999999999999999.00,99999999999999999.00",
            "true,99999999999999999.00,99999999999999999.00"})
    void createAndUpdateAcceptScaleTwoWholeDongAmounts(boolean update, String minimum, String maximum) {
        var form = form(update);
        form.setMinSubtotal(new BigDecimal(minimum));
        form.setMaxDiscount(new BigDecimal(maximum));
        assertThat(validator.validate(form)).isEmpty();
        assertThat(form.getMinSubtotal()).isEqualTo(new BigDecimal(minimum));
        assertThat(form.getMaxDiscount()).isEqualTo(new BigDecimal(maximum));
    }
    @ParameterizedTest @CsvSource({"false,minSubtotal,500.50", "true,minSubtotal,500.50",
            "false,maxDiscount,500.50", "true,maxDiscount,500.50",
            "false,minSubtotal,500.001", "true,minSubtotal,500.001",
            "false,maxDiscount,500.001", "true,maxDiscount,500.001"})
    void createAndUpdateRejectFractionalMonetaryAmounts(boolean update, String field, String amount) {
        var form = form(update);
        setMoney(form, field, amount);
        assertThat(validator.validate(form)).extracting(violation -> violation.getPropertyPath().toString())
                .contains("wholeDongAmounts");
    }
    @ParameterizedTest @CsvSource({"false,minSubtotal,-1.00", "true,minSubtotal,-1.00",
            "false,maxDiscount,-1.00", "true,maxDiscount,-1.00",
            "false,minSubtotal,100000000000000000.00", "true,minSubtotal,100000000000000000.00",
            "false,maxDiscount,100000000000000000.00", "true,maxDiscount,100000000000000000.00"})
    void createAndUpdateRetainMoneyBounds(boolean update, String field, String amount) {
        var form = form(update);
        setMoney(form, field, amount);
        assertThat(validator.validate(form)).extracting(violation -> violation.getPropertyPath().toString()).contains(field);
    }
    private static void setMoney(VendorVoucherForm form, String field, String amount) {
        if (field.equals("minSubtotal")) form.setMinSubtotal(new BigDecimal(amount));
        else form.setMaxDiscount(new BigDecimal(amount));
    }
    private VendorVoucherUpdateForm form() {
        return (VendorVoucherUpdateForm) form(true);
    }
    private VendorVoucherForm form(boolean update) {
        var form = update ? new VendorVoucherUpdateForm() : new VendorVoucherForm();
        form.setCode("SAVE"); form.setType(VoucherType.PERCENTAGE); form.setValue(new BigDecimal("10"));
        form.setStartsAt(LocalDateTime.of(2026, 10, 1, 0, 0)); form.setEndsAt(LocalDateTime.of(2026, 11, 1, 0, 0));
        form.setTotalLimit(10L); form.setPerUserLimit(1L);
        if (form instanceof VendorVoucherUpdateForm edit) edit.setVersion(0L);
        return form;
    }
}
