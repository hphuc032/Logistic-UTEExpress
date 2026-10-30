package com.uteexpress.security.web;

import com.uteexpress.identity.service.IdentityAuthenticationService;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

@Component
public class TokenVersionLogoutHandler implements LogoutHandler {
    private final IdentityAuthenticationService identities;

    public TokenVersionLogoutHandler(IdentityAuthenticationService identities) {
        this.identities = identities;
    }

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) {
        if (authentication != null
                && authentication.getPrincipal() instanceof UteExpressPrincipal principal) {
            identities.invalidateTokens(principal.userId());
        }
    }
}
