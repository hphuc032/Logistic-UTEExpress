package com.uteexpress.identity.dto;

public record AccountProfileData(
        Long userId,
        String username,
        String email,
        String fullName,
        String phone,
        String avatarKey) {
}
