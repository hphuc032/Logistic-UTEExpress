package com.uteexpress.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "Request is invalid."),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request validation failed."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource was not found."),
    CONFLICT(HttpStatus.CONFLICT, "Request conflicts with the current resource state."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication is required."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access is denied."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }

    /** Catalogued public details; callers cannot expose arbitrary exception text. */
    public enum Detail {
        SINGLE_SHOP_CHECKOUT(CONFLICT,
                "Checkout chỉ hỗ trợ sản phẩm từ một shop. Vui lòng chọn sản phẩm của một shop cho mỗi lần thanh toán.");

        private final ErrorCode code;
        private final String message;

        Detail(ErrorCode code, String message) { this.code = code; this.message = message; }
        public ErrorCode code() { return code; }
        public String message() { return message; }
    }
}
