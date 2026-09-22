package com.uteexpress.security;

import com.uteexpress.security.web.JwtCookieService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtSecurityConfigurationTest {
    private static final String TEST_SECRET =
            "VVRFRXhwcmVzcy10ZXN0LW9ubHktc2VjcmV0LWtleS0zMi1ieXRlcyE=";

    @Test
    void productionStyleCookieIsSecureHttpOnlyLaxAndTtlBounded() {
        JwtProperties properties = properties(true, TEST_SECRET);
        MockHttpServletResponse response = new MockHttpServletResponse();

        new JwtCookieService(properties).addAuthenticationCookie(response, "signed-token");

        String header = response.getHeader("Set-Cookie");
        assertThat(header)
                .contains("UTEEXPRESS_AUTH=signed-token")
                .contains("Path=/")
                .contains("Max-Age=1800")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .doesNotContain("Domain=");
    }

    @Test
    void invalidOrShortSigningSecretsFailClosed() {
        assertThatThrownBy(() -> properties(true, "not-base64!").signingKey())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("valid Base64");
        assertThatThrownBy(() -> properties(true, "dG9vLXNob3J0").signingKey())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32");
    }

    private JwtProperties properties(boolean secure, String secret) {
        return new JwtProperties("uteexpress", "uteexpress-web", Duration.ofMinutes(30),
                Duration.ofSeconds(60), "UTEEXPRESS_AUTH", secure, secret);
    }
}
