package com.uteexpress.account.dto;

public record AddressData(Long id, String receiverName, String phone, String provinceCode,
        String district, String detail, boolean defaultAddress) {
}
