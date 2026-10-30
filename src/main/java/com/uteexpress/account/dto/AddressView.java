package com.uteexpress.account.dto;

public record AddressView(Long id, String receiverName, String phone, String provinceCode,
        String district, String detail, boolean defaultAddress) {
}
