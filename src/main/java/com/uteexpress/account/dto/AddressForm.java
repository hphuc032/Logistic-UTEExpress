package com.uteexpress.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class AddressForm {
    @NotBlank(message = "Tên người nhận là bắt buộc.")
    @Size(max = 120, message = "Tên người nhận không được vượt quá 120 ký tự.")
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]+$", message = "Tên người nhận chứa ký tự không hợp lệ.")
    private String receiverName;

    @NotBlank(message = "Số điện thoại là bắt buộc.")
    @Size(max = 32, message = "Số điện thoại không được vượt quá 32 ký tự.")
    @Pattern(regexp = "^[0-9+()\\- ]+$", message = "Số điện thoại chứa ký tự không hợp lệ.")
    private String phone;

    @NotBlank(message = "Mã tỉnh/thành là bắt buộc.")
    @Size(max = 64, message = "Mã tỉnh/thành không được vượt quá 64 ký tự.")
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]+$", message = "Mã tỉnh/thành chứa ký tự không hợp lệ.")
    private String provinceCode;

    @NotBlank(message = "Quận/huyện là bắt buộc.")
    @Size(max = 120, message = "Quận/huyện không được vượt quá 120 ký tự.")
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]+$", message = "Quận/huyện chứa ký tự không hợp lệ.")
    private String district;

    @NotBlank(message = "Địa chỉ chi tiết là bắt buộc.")
    @Size(max = 255, message = "Địa chỉ chi tiết không được vượt quá 255 ký tự.")
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]+$", message = "Địa chỉ chi tiết chứa ký tự không hợp lệ.")
    private String detail;

    public String getReceiverName() { return receiverName; }
    public void setReceiverName(String receiverName) { this.receiverName = trim(receiverName); }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = trim(phone); }
    public String getProvinceCode() { return provinceCode; }
    public void setProvinceCode(String provinceCode) { this.provinceCode = trim(provinceCode); }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = trim(district); }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = trim(detail); }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
