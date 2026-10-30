package com.uteexpress.shop.dto;

public record ShopView(
        Long id,
        String name,
        String slug,
        String description,
        String pickupAddress,
        String status,
        String rejectionReason,
        Long version) {
}
