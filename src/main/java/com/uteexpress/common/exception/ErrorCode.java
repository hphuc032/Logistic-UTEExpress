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
        VOUCHER_INVALID(VALIDATION_FAILED, "Mã voucher không hợp lệ. Chỉ dùng chữ, số, dấu gạch ngang hoặc gạch dưới (tối đa 64 ký tự)."),
        VOUCHER_UNKNOWN(INVALID_REQUEST, "Không tìm thấy voucher này."),
        VOUCHER_INACTIVE(INVALID_REQUEST, "Voucher đã ngừng hoạt động."),
        VOUCHER_FUTURE(INVALID_REQUEST, "Voucher chưa đến thời gian sử dụng."),
        VOUCHER_EXPIRED(INVALID_REQUEST, "Voucher đã hết hạn."),
        VOUCHER_WRONG_SHOP(INVALID_REQUEST, "Voucher không áp dụng cho shop này."),
        VOUCHER_MINIMUM(INVALID_REQUEST, "Tiền sản phẩm chưa đạt mức tối thiểu của voucher."),
        VOUCHER_QUOTA(CONFLICT, "Voucher đã hết lượt sử dụng."),
        VOUCHER_USER_LIMIT(CONFLICT, "Bạn đã dùng hết số lượt được phép của voucher này."),
        SINGLE_SHOP_CHECKOUT(CONFLICT,
                "Checkout chỉ hỗ trợ sản phẩm từ một shop. Vui lòng chọn sản phẩm của một shop cho mỗi lần thanh toán.");

        private final ErrorCode code;
        private final String message;

        Detail(ErrorCode code, String message) { this.code = code; this.message = message; }
        public ErrorCode code() { return code; }
        public String message() { return message; }
    }
}
