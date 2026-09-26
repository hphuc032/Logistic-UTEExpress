package com.uteexpress.identity.service;

public final class CurrentPasswordMismatchException extends RuntimeException {
    public CurrentPasswordMismatchException() {
        super("Current password does not match");
    }
}
