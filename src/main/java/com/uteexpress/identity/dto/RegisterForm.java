package com.uteexpress.identity.dto;

import com.uteexpress.identity.dto.validation.PasswordMatches;
import com.uteexpress.identity.dto.validation.ValidBcryptPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@PasswordMatches
public class RegisterForm {
    @NotBlank(message = "Email là bắt buộc.")
    @Email(message = "Email không đúng định dạng.")
    @Size(max = 254, message = "Email không được vượt quá 254 ký tự.")
    private String email;

    @NotBlank(message = "Tên đăng nhập là bắt buộc.")
    @Size(min = 3, max = 30, message = "Tên đăng nhập phải có từ 3 đến 30 ký tự.")
    @Pattern(regexp = "^[A-Za-z0-9._-]+$",
            message = "Tên đăng nhập chỉ được chứa chữ cái, chữ số, dấu chấm, gạch dưới và gạch ngang.")
    private String username;

    @NotBlank(message = "Mật khẩu là bắt buộc.")
    @Size(min = 8, max = 64, message = "Mật khẩu phải có từ 8 đến 64 ký tự.")
    @ValidBcryptPassword
    private String password;

    @NotBlank(message = "Vui lòng xác nhận mật khẩu.")
    @Size(max = 64, message = "Mật khẩu xác nhận không được vượt quá 64 ký tự.")
    private String confirmPassword;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
}
