package com.uteexpress.security.filter;

import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.security.SecurityErrorResponseWriter;
import com.uteexpress.security.service.OtpRequestRateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** CSRF runs first. Budget depends on transport peer, never account existence or client proxy headers. */
@Component
public class OtpRequestLimitFilter extends OncePerRequestFilter {
    private static final Set<String> ROUTES=Set.of("/register","/verify-otp","/verify-otp/resend","/forgot-password","/reset-password");
    private final OtpRequestRateLimiter limiter;
    private final SecurityErrorResponseWriter errors;
    public OtpRequestLimitFilter(OtpRequestRateLimiter limiter,SecurityErrorResponseWriter errors){this.limiter=limiter;this.errors=errors;}

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path=request.getRequestURI().substring(request.getContextPath().length());
        return !"POST".equals(request.getMethod()) || !ROUTES.contains(path);
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        var decision=limiter.acquire(request.getRemoteAddr());
        if(!decision.allowed()) {
            response.setHeader("Retry-After",Long.toString(decision.retryAfterSeconds()));
            errors.write(request,response,ErrorCode.TOO_MANY_REQUESTS);
            return;
        }
        chain.doFilter(request,response);
    }
}
