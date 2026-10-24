package com.uteexpress.security.dto;

import java.security.Principal;
import java.time.Instant;
import java.util.Set;

/** Server-derived socket authority. Never serialize this record to a client or log its claims. */
public record SocketIdentity(Long accountId, long tokenVersion, Set<String> roles,
        Instant expiresAt) implements Principal {
    public SocketIdentity { roles = Set.copyOf(roles); }
    @Override public String getName() { return "uteexpress:user:" + accountId; }
}
