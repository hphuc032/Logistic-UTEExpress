package com.uteexpress.notification.dto;

/** Server-only wake-up hint. Inbox remains the durable, owner-scoped source of truth. */
public record NotificationCommitted(Long notificationId, Long recipientId) {}
