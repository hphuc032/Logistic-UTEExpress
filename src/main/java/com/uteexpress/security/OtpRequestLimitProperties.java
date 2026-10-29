package com.uteexpress.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Single-instance HTTP admission budget; persisted per-account OTP controls remain authoritative. */
@ConfigurationProperties("uteexpress.security.otp-request-limit")
public record OtpRequestLimitProperties(Duration window, int maxRequests, int maxClients) {
    public OtpRequestLimitProperties {
        if (window==null || window.compareTo(Duration.ofSeconds(1))<0 || window.compareTo(Duration.ofHours(1))>0
                || maxRequests<1 || maxRequests>1000 || maxClients<1 || maxClients>100_000)
            throw new IllegalStateException("OTP request budget requires a 1s..1h window, 1..1000 requests and 1..100000 clients");
    }
}
