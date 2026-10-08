package com.uteexpress.promotion.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

/** Product identity and creator are absent: the owned URL and principal determine them. */
public class VendorPromotionForm {
    @NotBlank @Size(max = 200) private String name;
    @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("100")
    private BigDecimal discountPercent;
    @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) private LocalDateTime startsAt;
    @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) private LocalDateTime endsAt;
    private boolean active = true;
    @AssertTrue(message = "Thời gian kết thúc phải sau thời gian bắt đầu.")
    public boolean isValidWindow() { return startsAt == null || endsAt == null || startsAt.isBefore(endsAt); }
    public String getName() { return name; }
    public void setName(String name) { this.name = name == null ? null : name.strip(); }
    public BigDecimal getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(BigDecimal value) { discountPercent = value; }
    public LocalDateTime getStartsAt() { return startsAt; }
    public void setStartsAt(LocalDateTime value) { startsAt = value; }
    public LocalDateTime getEndsAt() { return endsAt; }
    public void setEndsAt(LocalDateTime value) { endsAt = value; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
