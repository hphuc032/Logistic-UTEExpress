package com.uteexpress.identity.service;

public class RegistrationConflictException extends RuntimeException {
    public RegistrationConflictException() {
        super("Registration identity is unavailable.");
    }
}
