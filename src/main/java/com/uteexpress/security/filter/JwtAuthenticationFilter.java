package com.uteexpress.security.filter;

import com.uteexpress.identity.dto.AuthAccountSnapshot;
import com.uteexpress.identity.service.IdentityAuthenticationService;
import com.uteexpress.security.JwtProperties;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import com.uteexpress.security.authentication.UteExpressPrincipalFactory;
import com.uteexpress.security.jwt.JwtIdentity;
import com.uteexpress.security.jwt.JwtTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenService tokens;
    private final IdentityAuthenticationService identities;
    private final UteExpressPrincipalFactory principalFactory;
    private final JwtProperties properties;

    public JwtAuthenticationFilter(JwtTokenService tokens,
            IdentityAuthenticationService identities,
            UteExpressPrincipalFactory principalFactory,
            JwtProperties properties) {
        this.tokens = tokens;
        this.identities = identities;
        this.principalFactory = principalFactory;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            cookieValue(request).ifPresent(token -> authenticate(token, request));
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(String token, HttpServletRequest request) {
        try {
            JwtIdentity identity = tokens.decode(token);
            AuthAccountSnapshot account = identities.findByUserId(identity.userId()).orElse(null);
            if (account == null || !account.active()
                    || account.tokenVersion() != identity.tokenVersion()) {
                SecurityContextHolder.clearContext();
                return;
            }

            UteExpressPrincipal principal = principalFactory.create(account, false);
            UsernamePasswordAuthenticationToken authentication =
                    UsernamePasswordAuthenticationToken.authenticated(
                            principal, null, principal.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException | IllegalStateException exception) {
            SecurityContextHolder.clearContext();
        }
    }

    private Optional<String> cookieValue(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> properties.cookieName().equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> !value.isBlank())
                .findFirst();
    }
}
