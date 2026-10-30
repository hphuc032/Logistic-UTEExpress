package com.uteexpress.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class VerifyOtpForm {
    @NotBlank(message = "Vui lòng nhập email hoặc tên đăng nhập.")
    @Size(max = 254, message = "Thông tin tài khoản không được vượt quá 254 ký tự.")
    private String identifier;

    @NotBlank(message = "Vui lòng nhập mã xác minh.")
    @Pattern(regexp = "^[0-9]{6}$", message = "Mã xác minh phải gồm đúng 6 chữ số.")
    private String code;

    public String getIdentifier() { return identifier; }
    public void setIdentifier(String identifier) { this.identifier = identifier; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}
