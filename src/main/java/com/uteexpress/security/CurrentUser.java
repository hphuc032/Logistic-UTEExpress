package com.uteexpress.security;

import java.util.Set;

/** Immutable authenticated-user snapshot without persistence or JWT concerns. */
public record CurrentUser(String subject, Set<RoleCode> roles, Set<String> authorities) {
    public CurrentUser {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("subject must not be blank");
        }
        roles = Set.copyOf(roles);
        authorities = Set.copyOf(authorities);
    }

    public boolean hasRole(RoleCode role) {
        return roles.contains(role);
    }
}
