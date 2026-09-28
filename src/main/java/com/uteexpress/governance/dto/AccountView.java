package com.uteexpress.governance.dto;

public record AccountView(Long id, String username, String email, String fullName,
        String phone, String status, Long version) { }
