package com.uteexpress.payment.dto;

import jakarta.validation.constraints.*;

public record CancelledPaidRefundRequest(
        @NotNull @PositiveOrZero @Max(Long.MAX_VALUE - 1) Long expectedOrderVersion,
        @NotBlank @Size(max = 255) String proofReference) {
    public CancelledPaidRefundRequest { proofReference = proofReference == null ? null : proofReference.strip(); }
}
