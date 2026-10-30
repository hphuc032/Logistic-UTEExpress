package com.uteexpress.identity.service;

public class RegistrationConflictException extends RuntimeException {
    public RegistrationConflictException() {
        super("Registration identity is unavailable.");
    }

    RegistrationConflictException(Throwable cause) {
        super("Registration identity is unavailable.", cause);
    }
}
