package com.uteexpress.account.dto;

public record ProfileView(String username, String email, String fullName, String phone,
                          boolean avatarAvailable) {
}
