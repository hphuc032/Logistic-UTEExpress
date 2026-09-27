package com.uteexpress.account.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ProfileForm {
    @Size(max = 120, message = "Họ tên không được vượt quá 120 ký tự.")
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Họ tên chứa ký tự không hợp lệ.")
    private String fullName;

    @Size(max = 32, message = "Số điện thoại không được vượt quá 32 ký tự.")
    @Pattern(regexp = "^[0-9+()\\- ]*$", message = "Số điện thoại chứa ký tự không hợp lệ.")
    private String phone;

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = trim(fullName); }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = trim(phone); }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
