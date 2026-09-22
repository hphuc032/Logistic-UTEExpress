package com.uteexpress.security;

import com.uteexpress.security.authentication.UteExpressPrincipal;
import com.uteexpress.security.jwt.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {
    private static final String TEST_SECRET =
            "VVRFRXhwcmVzcy10ZXN0LW9ubHktc2VjcmV0LWtleS0zMi1ieXRlcyE=";
    private static final Instant NOW = Instant.parse("2026-09-22T00:00:00Z");

    private JwtProperties properties;
    private JwtEncoder encoder;
    private JwtDecoder decoder;
    private JwtTokenService tokens;

    @BeforeEach
    void setUp() {
        properties = new JwtProperties("uteexpress", "uteexpress-web", Duration.ofMinutes(30),
                Duration.ofSeconds(60), "UTEEXPRESS_AUTH", false, TEST_SECRET);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        JwtConfiguration configuration = new JwtConfiguration();
        encoder = configuration.jwtEncoder(properties);
        decoder = configuration.jwtDecoder(properties, clock);
        tokens = new JwtTokenService(encoder, decoder, properties, clock);
    }

    @Test
    void issuedTokenContainsOnlyRequiredIdentityClaims() {
        UteExpressPrincipal principal = new UteExpressPrincipal(42L, "Phuc03", null, 7L,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")), true);

        String value = tokens.issue(principal);
        var jwt = decoder.decode(value);

        assertThat(jwt.getSubject()).isEqualTo("uteexpress:user:42");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("uteexpress");
        assertThat(jwt.getAudience()).containsExactly("uteexpress-web");
        assertThat(jwt.getIssuedAt()).isEqualTo(NOW);
        assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
        assertThat((Number) jwt.getClaim("tokenVersion")).isEqualTo(7L);
        assertThat(jwt.getClaims()).doesNotContainKeys(
                "password", "passwordHash", "email", "roles", "authorities");
        assertThat(tokens.decode(value).userId()).isEqualTo(42L);
    }

    @Test
    void rejectsExpiredToken() {
        assertRejected(claims("uteexpress:user:42", NOW.minusSeconds(120), NOW.minusSeconds(61),
                "uteexpress", List.of("uteexpress-web"), 0L));
    }

    @Test
    void rejectsWrongIssuer() {
        assertRejected(claims("uteexpress:user:42", NOW, NOW.plusSeconds(600),
                "other", List.of("uteexpress-web"), 0L));
    }

    @Test
    void rejectsWrongAudience() {
        assertRejected(claims("uteexpress:user:42", NOW, NOW.plusSeconds(600),
                "uteexpress", List.of("other"), 0L));
    }

    @Test
    void rejectsMissingOrInvalidIdentityClaims() {
        assertRejected(claims(null, NOW, NOW.plusSeconds(600),
                "uteexpress", List.of("uteexpress-web"), 0L));
        assertRejected(claims("42", NOW, NOW.plusSeconds(600),
                "uteexpress", List.of("uteexpress-web"), 0L));
        assertRejected(claims("uteexpress:user:42", NOW, NOW.plusSeconds(600),
                "uteexpress", List.of("uteexpress-web"), null));
        JwtClaimsSet noExpiration = JwtClaimsSet.builder()
                .subject("uteexpress:user:42")
                .issuedAt(NOW)
                .issuer("uteexpress")
                .audience(List.of("uteexpress-web"))
                .claim("tokenVersion", 0L)
                .build();
        assertRejected(noExpiration);
    }

    @Test
    void rejectsIssuedAtBeyondClockSkew() {
        assertRejected(claims("uteexpress:user:42", NOW.plusSeconds(61), NOW.plusSeconds(600),
                "uteexpress", List.of("uteexpress-web"), 0L));
    }

    @Test
    void rejectsTamperedSignature() {
        UteExpressPrincipal principal = new UteExpressPrincipal(42L, "Phuc03", null, 0L,
                List.of(), true);
        String token = tokens.issue(principal);
        char replacement = token.endsWith("A") ? 'B' : 'A';
        String tampered = token.substring(0, token.length() - 1) + replacement;

        assertThatThrownBy(() -> tokens.decode(tampered)).isInstanceOf(JwtException.class);
    }

    private JwtClaimsSet claims(String subject, Instant issuedAt, Instant expiresAt,
            String issuer, List<String> audience, Long tokenVersion) {
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .issuer(issuer)
                .audience(audience);
        if (subject != null) {
            builder.subject(subject);
        }
        if (tokenVersion != null) {
            builder.claim("tokenVersion", tokenVersion);
        }
        return builder.build();
    }

    private void assertRejected(JwtClaimsSet claims) {
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        assertThatThrownBy(() -> tokens.decode(token)).isInstanceOf(JwtException.class);
    }
}
