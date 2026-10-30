package com.uteexpress.security.dto;

import com.uteexpress.security.RoleCode;

import java.util.Set;

/** Internal login result. The token must only be written to the HttpOnly cookie. */
public record LoginOutcome(String token, Set<RoleCode> roles) {
    public LoginOutcome {
        roles = Set.copyOf(roles);
    }
}
