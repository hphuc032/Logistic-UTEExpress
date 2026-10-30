package com.uteexpress.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ForgotPasswordForm {
    @NotBlank(message = "Email là bắt buộc.")
    @Email(message = "Email không đúng định dạng.")
    @Size(max = 254, message = "Email không được vượt quá 254 ký tự.")
    private String email;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
