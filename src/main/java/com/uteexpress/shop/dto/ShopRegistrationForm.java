package com.uteexpress.shop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public class ShopRegistrationForm {
    @NotBlank(message = "Vui lòng nhập tên cửa hàng.")
    @Size(max = 120, message = "Tên cửa hàng không được vượt quá 120 ký tự.")
    private String name;

    @NotBlank(message = "Vui lòng nhập slug cửa hàng.")
    @Size(max = 120, message = "Slug không được vượt quá 120 ký tự.")
    @Pattern(regexp = "[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*",
            message = "Slug chỉ gồm chữ cái, chữ số và dấu gạch nối đơn.")
    private String slug;

    @Size(max = 2000, message = "Mô tả không được vượt quá 2000 ký tự.")
    private String description;

    @NotBlank(message = "Vui lòng nhập địa chỉ lấy hàng.")
    @Size(max = 500, message = "Địa chỉ lấy hàng không được vượt quá 500 ký tự.")
    private String pickupAddress;

    public String getName() { return name; }
    public void setName(String name) { this.name = trim(name); }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug == null ? null : slug.trim().toLowerCase(Locale.ROOT); }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = trim(description); }
    public String getPickupAddress() { return pickupAddress; }
    public void setPickupAddress(String pickupAddress) { this.pickupAddress = trim(pickupAddress); }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
