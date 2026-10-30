package com.uteexpress.account.dto;

import com.uteexpress.identity.dto.validation.PasswordConfirmation;
import com.uteexpress.identity.dto.validation.PasswordMatches;
import com.uteexpress.identity.dto.validation.ValidBcryptPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@PasswordMatches
public class PasswordChangeForm implements PasswordConfirmation {
    @NotBlank(message = "Mật khẩu hiện tại là bắt buộc.")
    @Size(max = 64, message = "Mật khẩu hiện tại không hợp lệ.")
    private String currentPassword;

    @NotBlank(message = "Mật khẩu mới là bắt buộc.")
    @Size(min = 8, max = 64, message = "Mật khẩu phải có từ 8 đến 64 ký tự.")
    @ValidBcryptPassword
    private String newPassword;

    @NotBlank(message = "Vui lòng xác nhận mật khẩu mới.")
    @Size(max = 64, message = "Mật khẩu xác nhận không được vượt quá 64 ký tự.")
    private String confirmPassword;

    public String getCurrentPassword() { return currentPassword; }
    public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }
    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    @Override public String getPassword() { return newPassword; }
    @Override public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
}
