package com.uteexpress.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.util.Base64;

@ConfigurationProperties("uteexpress.security.jwt")
public record JwtProperties(
        String issuer,
        String audience,
        Duration ttl,
        Duration clockSkew,
        String cookieName,
        boolean cookieSecure,
        String secretBase64) {

    public JwtProperties {
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException("JWT issuer must be configured");
        }
        if (audience == null || audience.isBlank()) {
            throw new IllegalStateException("JWT audience must be configured");
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalStateException("JWT TTL must be positive");
        }
        if (clockSkew == null || clockSkew.isNegative() || clockSkew.compareTo(Duration.ofSeconds(60)) > 0) {
            throw new IllegalStateException("JWT clock skew must be between zero and 60 seconds");
        }
        if (cookieName == null || cookieName.isBlank()) {
            throw new IllegalStateException("JWT cookie name must be configured");
        }
        if (secretBase64 == null || secretBase64.isBlank()) {
            throw new IllegalStateException("JWT_SECRET_BASE64 must be configured");
        }
    }

    public SecretKey signingKey() {
        final byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(secretBase64);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("JWT_SECRET_BASE64 must contain valid Base64", exception);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT signing key must contain at least 32 decoded bytes");
        }
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }
}
