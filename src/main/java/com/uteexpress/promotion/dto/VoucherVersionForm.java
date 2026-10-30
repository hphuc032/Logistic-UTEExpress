package com.uteexpress.promotion.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class VoucherVersionForm {
    @NotNull @Min(0) private Long version;
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
