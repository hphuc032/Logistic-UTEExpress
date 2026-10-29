package com.uteexpress.payment.dto;

/** Contains persisted payment status only after the signed callback has been matched to stored facts. */
public record VnpayReturnView(boolean valid, Long orderId, String state) { }
