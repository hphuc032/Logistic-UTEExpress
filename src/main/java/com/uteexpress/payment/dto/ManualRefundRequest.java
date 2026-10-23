package com.uteexpress.payment.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Approved JSON body. Order identity and idempotency key come from route/header. */
public record ManualRefundRequest(
        @NotNull @PositiveOrZero @Max(Long.MAX_VALUE - 1) Long expectedOrderVersion,
        @NotNull @PositiveOrZero Long expectedReturnRequestVersion,
        @NotBlank @Size(max = 255) String proofReference) {
    public ManualRefundRequest {
        proofReference = proofReference == null ? null : proofReference.strip();
    }
}
