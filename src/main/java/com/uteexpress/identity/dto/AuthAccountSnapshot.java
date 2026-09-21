package com.uteexpress.identity.dto;

import java.util.Set;

/** Internal authentication projection; never expose this DTO to an HTTP response. */
public record AuthAccountSnapshot(
        Long userId,
        String displayUsername,
        String passwordHash,
        boolean active,
        long tokenVersion,
        Set<String> roleCodes) {

    public AuthAccountSnapshot {
        roleCodes = Set.copyOf(roleCodes);
    }
}
