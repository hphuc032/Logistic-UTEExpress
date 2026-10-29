package com.uteexpress.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** VNPAY requires these exact case-sensitive JSON keys. */
public record VnpayIpnResponse(@JsonProperty("RspCode") String code, @JsonProperty("Message") String message) {
    public static VnpayIpnResponse of(String code, String message) { return new VnpayIpnResponse(code, message); }
}
