package com.uteexpress.notification.dto;

import java.time.Instant;

public record NotificationView(Long id, Long orderId, String type, String title,
        String message, Instant readAt, Instant createdAt) { }
