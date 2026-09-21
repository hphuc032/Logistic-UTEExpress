package com.uteexpress.security.web;

import com.uteexpress.security.JwtProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class JwtCookieService {
    private final JwtProperties properties;

    public JwtCookieService(JwtProperties properties) {
        this.properties = properties;
    }

    public void addAuthenticationCookie(HttpServletResponse response, String token) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(token, properties.ttl()).toString());
    }

    public void clearAuthenticationCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", java.time.Duration.ZERO).toString());
    }

    private ResponseCookie cookie(String value, java.time.Duration maxAge) {
        return ResponseCookie.from(properties.cookieName(), value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}
