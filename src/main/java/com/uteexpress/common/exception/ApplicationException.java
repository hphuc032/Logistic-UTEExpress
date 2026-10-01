package com.uteexpress.common.exception;

import java.util.Objects;

/** Expected application failure. Public messages come only from the error catalog. */
public class ApplicationException extends RuntimeException {
    private final ErrorCode errorCode;
    private final ErrorCode.Detail detail;

    public ApplicationException(ErrorCode errorCode) {
        super(Objects.requireNonNull(errorCode).message());
        this.errorCode = errorCode;
        this.detail = null;
    }

    public ApplicationException(ErrorCode.Detail detail) {
        super(Objects.requireNonNull(detail).message());
        this.errorCode = detail.code();
        this.detail = detail;
    }

    public final String publicMessage() { return detail == null ? errorCode.message() : detail.message(); }
    public final ErrorCode.Detail detail() { return detail; }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
