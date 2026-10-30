package com.uteexpress.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ResendOtpForm {
    @NotBlank(message = "Vui lòng nhập email hoặc tên đăng nhập.")
    @Size(max = 254, message = "Thông tin tài khoản không được vượt quá 254 ký tự.")
    private String identifier;

    public String getIdentifier() { return identifier; }
    public void setIdentifier(String identifier) { this.identifier = identifier; }
}
