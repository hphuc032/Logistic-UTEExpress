package com.uteexpress.identity.validation;

import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {
    public static final int MIN_CHARACTERS = 8;
    public static final int MAX_CHARACTERS = 64;
    public static final int BCRYPT_MAX_BYTES = 72;

    private PasswordPolicy() {
    }

    public static boolean isValid(String password) {
        return password != null
                && password.length() >= MIN_CHARACTERS
                && password.length() <= MAX_CHARACTERS
                && password.getBytes(StandardCharsets.UTF_8).length <= BCRYPT_MAX_BYTES;
    }

    public static boolean isWithinBcryptLimit(String password) {
        return password == null
                || password.getBytes(StandardCharsets.UTF_8).length <= BCRYPT_MAX_BYTES;
    }
}
