package com.uteexpress.security.dto;

import com.uteexpress.identity.dto.validation.ValidBcryptPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LoginForm {
    @NotBlank(message = "Vui lòng nhập email hoặc tên đăng nhập.")
    @Size(max = 254, message = "Thông tin đăng nhập không được vượt quá 254 ký tự.")
    private String identifier;

    @NotBlank(message = "Vui lòng nhập mật khẩu.")
    @Size(max = 64, message = "Mật khẩu không hợp lệ.")
    @ValidBcryptPassword(message = "Mật khẩu không hợp lệ.")
    private String password;

    public String getIdentifier() { return identifier; }
    public void setIdentifier(String identifier) { this.identifier = identifier; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
