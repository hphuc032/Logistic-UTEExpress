package com.uteexpress.security;

import java.util.Arrays;
import java.util.Optional;

/** Stable application role contract. Guest means no authenticated principal. */
public enum RoleCode {
    USER,
    VENDOR,
    MANAGER,
    ADMIN,
    SHIPPER;

    public static final String AUTHORITY_PREFIX = "ROLE_";

    public String authority() {
        return AUTHORITY_PREFIX + name();
    }

    public static Optional<RoleCode> fromAuthority(String authority) {
        if (authority == null || !authority.startsWith(AUTHORITY_PREFIX)) {
            return Optional.empty();
        }
        String candidate = authority.substring(AUTHORITY_PREFIX.length());
        return Arrays.stream(values()).filter(role -> role.name().equals(candidate)).findFirst();
    }
}
