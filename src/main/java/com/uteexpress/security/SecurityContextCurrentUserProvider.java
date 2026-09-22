package com.uteexpress.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import com.uteexpress.security.authentication.UteExpressPrincipal;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SecurityContextCurrentUserProvider implements CurrentUserProvider {
    @Override
    public boolean isAuthenticated() {
        return authenticated(SecurityContextHolder.getContext().getAuthentication());
    }

    @Override
    public Optional<CurrentUser> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!authenticated(authentication)) {
            return Optional.empty();
        }

        Set<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toUnmodifiableSet());
        Set<RoleCode> roles = authorities.stream()
                .map(RoleCode::fromAuthority)
                .flatMap(Optional::stream)
                .collect(Collectors.toUnmodifiableSet());
        String subject = authentication.getPrincipal() instanceof UteExpressPrincipal principal
                ? principal.subject()
                : authentication.getName();
        return Optional.of(new CurrentUser(subject, roles, authorities));
    }

    private static boolean authenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
