package com.uteexpress.payment.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("uteexpress.payment.vnpay")
public record VnpayProperties(String tmnCode, String hashSecret, String returnUrl) {
    public VnpayProperties {
        if (tmnCode == null || !tmnCode.matches("[A-Za-z0-9]{8}")
                || hashSecret == null || hashSecret.isBlank()) {
            throw new IllegalArgumentException("VNPAY Sandbox requires TMN_CODE and HASH_SECRET environment variables");
        }
        URI callback;
        try { callback = URI.create(returnUrl); }
        catch (RuntimeException ex) { throw new IllegalArgumentException("Invalid VNPAY_RETURN_URL"); }
        if (callback.getHost() == null || callback.getRawQuery() != null || callback.getFragment() != null
                || callback.getUserInfo() != null
                || !"/payments/vnpay/return".equals(callback.getPath())
                || !("https".equals(callback.getScheme()) || ("http".equals(callback.getScheme())
                    && java.util.Set.of("localhost", "127.0.0.1").contains(callback.getHost())))) {
            throw new IllegalArgumentException("VNPAY_RETURN_URL must be HTTPS (HTTP allowed only on localhost)");
        }
    }

    @Override public String toString() { return "VnpayProperties[redacted]"; }
}
