package com.uteexpress.identity.dto;

import com.uteexpress.identity.dto.validation.PasswordConfirmation;
import com.uteexpress.identity.dto.validation.PasswordMatches;
import com.uteexpress.identity.dto.validation.ValidBcryptPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@PasswordMatches
public class ResetPasswordForm implements PasswordConfirmation {
    @NotBlank(message = "Email là bắt buộc.")
    @Email(message = "Email không đúng định dạng.")
    @Size(max = 254, message = "Email không được vượt quá 254 ký tự.")
    private String email;

    @NotBlank(message = "Vui lòng nhập mã xác nhận.")
    @Pattern(regexp = "^[0-9]{6}$", message = "Mã xác nhận phải gồm đúng 6 chữ số.")
    private String code;

    @NotBlank(message = "Mật khẩu mới là bắt buộc.")
    @Size(min = 8, max = 64, message = "Mật khẩu phải có từ 8 đến 64 ký tự.")
    @ValidBcryptPassword
    private String newPassword;

    @NotBlank(message = "Vui lòng xác nhận mật khẩu mới.")
    @Size(max = 64, message = "Mật khẩu xác nhận không được vượt quá 64 ký tự.")
    private String confirmPassword;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    @Override public String getPassword() { return newPassword; }
    @Override public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
}
