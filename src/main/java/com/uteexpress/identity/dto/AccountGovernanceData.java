package com.uteexpress.identity.dto;

public record AccountGovernanceData(Long id, String username, String email,
        String fullName, String phone, String status, Long version) { }
