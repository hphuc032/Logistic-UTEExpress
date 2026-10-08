package com.uteexpress.promotion.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

/** SHOP identity and creator are deliberately absent from browser input. */
public class VendorVoucherForm {
    @NotBlank(message = "Vui lòng nhập mã voucher.")
    @Size(max = 64)
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{0,63}", message = "Mã chỉ gồm chữ, số, dấu gạch ngang hoặc gạch dưới.")
    private String code;
    @NotNull private VoucherType type;
    @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("99999999999999999")
    private BigDecimal value;
    @DecimalMin("0") @Digits(integer = 17, fraction = 2)
    private BigDecimal maxDiscount;
    @NotNull @DecimalMin("0") @Digits(integer = 17, fraction = 2)
    private BigDecimal minSubtotal = BigDecimal.ZERO;
    @NotNull @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime startsAt;
    @NotNull @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime endsAt;
    @NotNull @Positive private Long totalLimit;
    @NotNull @Positive private Long perUserLimit;
    private boolean active = true;

    @AssertTrue(message = "Thời gian kết thúc phải sau thời gian bắt đầu.")
    public boolean isValidWindow() { return startsAt == null || endsAt == null || startsAt.isBefore(endsAt); }
    @AssertTrue(message = "Phần trăm phải lớn hơn 0 và không vượt quá 100; mức giảm cố định phải là VND nguyên.")
    public boolean isValidDiscount() {
        return type == null || value == null || (type == VoucherType.PERCENTAGE
                ? value.compareTo(new BigDecimal("100")) <= 0 : value.stripTrailingZeros().scale() <= 0);
    }
    @AssertTrue(message = "Giảm tối đa và tiền sản phẩm tối thiểu phải là VND nguyên.")
    public boolean isWholeDongAmounts() {
        return (maxDiscount == null || maxDiscount.stripTrailingZeros().scale() <= 0)
                && (minSubtotal == null || minSubtotal.stripTrailingZeros().scale() <= 0);
    }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code == null ? null : code.strip(); }
    public VoucherType getType() { return type; }
    public void setType(VoucherType type) { this.type = type; }
    public BigDecimal getValue() { return value; }
    public void setValue(BigDecimal value) { this.value = value; }
    public BigDecimal getMaxDiscount() { return maxDiscount; }
    public void setMaxDiscount(BigDecimal maxDiscount) { this.maxDiscount = maxDiscount; }
    public BigDecimal getMinSubtotal() { return minSubtotal; }
    public void setMinSubtotal(BigDecimal minSubtotal) { this.minSubtotal = minSubtotal; }
    public LocalDateTime getStartsAt() { return startsAt; }
    public void setStartsAt(LocalDateTime startsAt) { this.startsAt = startsAt; }
    public LocalDateTime getEndsAt() { return endsAt; }
    public void setEndsAt(LocalDateTime endsAt) { this.endsAt = endsAt; }
    public Long getTotalLimit() { return totalLimit; }
    public void setTotalLimit(Long totalLimit) { this.totalLimit = totalLimit; }
    public Long getPerUserLimit() { return perUserLimit; }
    public void setPerUserLimit(Long perUserLimit) { this.perUserLimit = perUserLimit; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
