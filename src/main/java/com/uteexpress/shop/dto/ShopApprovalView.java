package com.uteexpress.shop.dto;

import java.time.Instant;

public record ShopApprovalView(Long id, Long ownerId, String name, String slug,
        String description, String pickupAddress, String status, String rejectionReason,
        Instant createdAt, Long version) { }
