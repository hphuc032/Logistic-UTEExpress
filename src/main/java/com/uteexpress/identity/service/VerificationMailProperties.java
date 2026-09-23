package com.uteexpress.identity.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("uteexpress.mail")
public record VerificationMailProperties(String from) {
    public VerificationMailProperties {
        if (from == null || from.isBlank() || containsLineBreak(from)) {
            throw new IllegalStateException("MAIL_FROM must be a safe configured address");
        }
    }

    static boolean containsLineBreak(String value) {
        return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
    }
}
