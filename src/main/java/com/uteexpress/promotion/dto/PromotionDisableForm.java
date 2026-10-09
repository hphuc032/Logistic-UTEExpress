package com.uteexpress.promotion.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public class PromotionDisableForm {
    @NotNull @PositiveOrZero private Long version;
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
