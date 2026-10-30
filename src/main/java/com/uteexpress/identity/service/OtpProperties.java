package com.uteexpress.identity.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Base64;

@ConfigurationProperties("uteexpress.security.otp")
public record OtpProperties(
        Duration ttl,
        Duration resendCooldown,
        int maxAttempts,
        String pepperBase64) {

    public OtpProperties {
        if (ttl == null || ttl.isZero() || ttl.isNegative() || ttl.compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalStateException("OTP TTL must be positive and no longer than five minutes");
        }
        if (resendCooldown == null || resendCooldown.compareTo(Duration.ofSeconds(60)) < 0) {
            throw new IllegalStateException("OTP resend cooldown must be at least sixty seconds");
        }
        if (maxAttempts <= 0 || maxAttempts > 5) {
            throw new IllegalStateException("OTP maximum attempts must be between one and five");
        }
        if (pepperBase64 == null || pepperBase64.isBlank()) {
            throw new IllegalStateException("OTP_PEPPER_BASE64 must be configured");
        }
        decodePepper(pepperBase64);
    }

    byte[] pepperBytes() {
        return decodePepper(pepperBase64);
    }

    private static byte[] decodePepper(String value) {
        final byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("OTP_PEPPER_BASE64 must contain valid Base64", exception);
        }
        if (decoded.length < 32) {
            throw new IllegalStateException("OTP pepper must contain at least 32 decoded bytes");
        }
        return decoded;
    }
}
