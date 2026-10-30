package com.uteexpress.security.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class JwtLogoutSuccessHandler implements LogoutSuccessHandler {
    private final JwtCookieService cookies;

    public JwtLogoutSuccessHandler(JwtCookieService cookies) {
        this.cookies = cookies;
    }

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {
        cookies.clearAuthenticationCookie(response);
        response.sendRedirect("/login?logout=true");
    }
}
