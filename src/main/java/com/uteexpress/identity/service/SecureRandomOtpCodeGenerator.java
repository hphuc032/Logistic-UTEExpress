package com.uteexpress.identity.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class SecureRandomOtpCodeGenerator implements OtpCodeGenerator {
    private static final int OTP_BOUND = 1_000_000;
    private final SecureRandom secureRandom;

    public SecureRandomOtpCodeGenerator() {
        this(new SecureRandom());
    }

    SecureRandomOtpCodeGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    @Override
    public String generate() {
        return "%06d".formatted(secureRandom.nextInt(OTP_BOUND));
    }
}
