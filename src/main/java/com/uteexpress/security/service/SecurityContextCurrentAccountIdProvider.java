package com.uteexpress.security.service;

import com.uteexpress.security.authentication.UteExpressPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class SecurityContextCurrentAccountIdProvider implements CurrentAccountIdProvider {
    @Override
    public Optional<Long> currentAccountId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UteExpressPrincipal principal)) {
            return Optional.empty();
        }
        return Optional.of(principal.userId());
    }
}
