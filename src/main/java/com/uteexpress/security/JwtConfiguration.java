package com.uteexpress.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfiguration {
    private static final OAuth2Error INVALID_TOKEN =
            new OAuth2Error("invalid_token", "JWT claims are invalid", null);

    @Bean
    Clock jwtClock() {
        return Clock.systemUTC();
    }

    @Bean
    JwtEncoder jwtEncoder(JwtProperties properties) {
        OctetSequenceKey jwk = new OctetSequenceKey.Builder(properties.signingKey().getEncoded())
                .algorithm(JWSAlgorithm.HS256)
                .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(jwk)));
    }

    @Bean
    JwtDecoder jwtDecoder(JwtProperties properties, Clock jwtClock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(properties.signingKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        JwtTimestampValidator timestamp = new JwtTimestampValidator(properties.clockSkew());
        timestamp.setClock(jwtClock);
        JwtIssuerValidator issuer = new JwtIssuerValidator(properties.issuer());
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience() != null
                && jwt.getAudience().contains(properties.audience())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
        OAuth2TokenValidator<Jwt> requiredClaims = jwt -> validRequiredClaims(jwt, jwtClock, properties)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(INVALID_TOKEN);

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                timestamp, issuer, audience, requiredClaims));
        return decoder;
    }

    private static boolean validRequiredClaims(Jwt jwt, Clock clock, JwtProperties properties) {
        String subject = jwt.getSubject();
        Instant issuedAt = jwt.getIssuedAt();
        Instant expiresAt = jwt.getExpiresAt();
        Object tokenVersion = jwt.getClaims().get("tokenVersion");
        return subject != null
                && subject.matches("uteexpress:user:[1-9][0-9]*")
                && issuedAt != null
                && expiresAt != null
                && !issuedAt.isAfter(clock.instant().plus(properties.clockSkew()))
                && tokenVersion instanceof Number number
                && number.longValue() >= 0
                && number.doubleValue() == number.longValue()
                && jwt.getAudience() != null
                && jwt.getAudience().equals(List.of(properties.audience()));
    }
}
