package com.uteexpress.security.filter;

import com.uteexpress.identity.dto.AuthAccountSnapshot;
import com.uteexpress.identity.service.IdentityAuthenticationService;
import com.uteexpress.security.JwtProperties;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import com.uteexpress.security.authentication.UteExpressPrincipalFactory;
import com.uteexpress.security.jwt.JwtIdentity;
import com.uteexpress.security.jwt.JwtTokenService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.BadJwtException;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {
    @Mock JwtTokenService tokens;
    @Mock IdentityAuthenticationService identities;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties("uteexpress", "uteexpress-web",
                Duration.ofMinutes(30), Duration.ofSeconds(60), "UTEEXPRESS_AUTH", false,
                "VVRFRXhwcmVzcy10ZXN0LW9ubHktc2VjcmV0LWtleS0zMi1ieXRlcyE=");
        filter = new JwtAuthenticationFilter(tokens, identities,
                new UteExpressPrincipalFactory(), properties);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validCookieUsesCurrentDatabaseAccountAndRoles() throws Exception {
        given(tokens.decode("valid")).willReturn(new JwtIdentity(42L, 3L));
        given(identities.findByUserId(42L)).willReturn(Optional.of(account(true, 3L, "ADMIN")));

        filter.doFilter(requestWithCookie("valid"), new MockHttpServletResponse(), new MockFilterChain());

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getName()).isEqualTo("uteexpress:user:42");
        assertThat(authentication.getPrincipal()).isInstanceOf(UteExpressPrincipal.class);
        assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
    }

    @Test
    void missingCookieContinuesAnonymous() throws Exception {
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(tokens, identities);
    }

    @Test
    void malformedCookieContinuesAnonymousWithoutFailure() throws Exception {
        given(tokens.decode("bad")).willThrow(new BadJwtException("invalid"));

        filter.doFilter(requestWithCookie("bad"), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void expiredCookieContinuesAnonymousWithoutFailure() throws Exception {
        given(tokens.decode("expired")).willThrow(new BadJwtException("expired"));

        filter.doFilter(requestWithCookie("expired"), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void unknownAccountAndUnknownRoleFailClosed() throws Exception {
        given(tokens.decode("unknown-user")).willReturn(new JwtIdentity(42L, 0L));
        given(identities.findByUserId(42L)).willReturn(Optional.empty());
        filter.doFilter(requestWithCookie("unknown-user"), new MockHttpServletResponse(), new MockFilterChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        given(tokens.decode("unknown-role")).willReturn(new JwtIdentity(42L, 0L));
        given(identities.findByUserId(42L))
                .willReturn(Optional.of(account(true, 0L, "ROOT")));
        filter.doFilter(requestWithCookie("unknown-role"), new MockHttpServletResponse(), new MockFilterChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void versionMismatchAndInactiveAccountBothFailClosed() throws Exception {
        given(tokens.decode("stale")).willReturn(new JwtIdentity(42L, 2L));
        given(identities.findByUserId(42L)).willReturn(Optional.of(account(true, 3L, "USER")));

        filter.doFilter(requestWithCookie("stale"), new MockHttpServletResponse(), new MockFilterChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        given(tokens.decode("inactive")).willReturn(new JwtIdentity(42L, 3L));
        given(identities.findByUserId(42L)).willReturn(Optional.of(account(false, 3L, "USER")));
        filter.doFilter(requestWithCookie("inactive"), new MockHttpServletResponse(), new MockFilterChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void sameJwtUsesRoleReloadedFromDatabaseOnNextRequest() throws Exception {
        given(tokens.decode("same-token")).willReturn(new JwtIdentity(42L, 0L));
        given(identities.findByUserId(42L))
                .willReturn(Optional.of(account(true, 0L, "USER")))
                .willReturn(Optional.of(account(true, 0L, "ADMIN")));

        filter.doFilter(requestWithCookie("same-token"), new MockHttpServletResponse(), new MockFilterChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority").containsExactly("ROLE_USER");

        SecurityContextHolder.clearContext();
        filter.doFilter(requestWithCookie("same-token"), new MockHttpServletResponse(), new MockFilterChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority").containsExactly("ROLE_ADMIN");
    }

    private static MockHttpServletRequest requestWithCookie(String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("UTEEXPRESS_AUTH", value));
        return request;
    }

    private static AuthAccountSnapshot account(boolean active, long tokenVersion, String role) {
        return new AuthAccountSnapshot(42L, "Phuc03", "hash", active, tokenVersion, Set.of(role));
    }
}
